package me.xx2bab.scratchpaper

import com.android.build.gradle.tasks.MergeManifests
import com.android.build.gradle.tasks.MergeResources
import me.xx2bab.scratchpaper.iconprocessor.BaseIconProcessor
import me.xx2bab.scratchpaper.utils.Aapt2Utils
import me.xx2bab.scratchpaper.utils.Logger
import org.gradle.api.Project
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory


class IconOverlayGenerator(private val params: GeneratorParams) {

    // default icon name of Android is ic_launcher
    private val defaultIconName = "ic_launcher"
    private val tagApplication = "application"
    private val attrIcon = "android:icon"
    private val attrRoundIcon = "android:roundIcon"

    fun process() {
        setAwtEnv()
        params.variant.outputs.forEach { output ->
            val processManifestTask: MergeManifests = output.processManifest as MergeManifests

            output.processResources.doFirst("process${params.dimension}IconsByScratchPaper") {
                val processedIcons = arrayListOf<File>()
                val mergedManifestFile = File(processManifestTask.manifestOutputDirectory,
                        "AndroidManifest.xml")
                val resDirs = params.variant.sourceSets[0].resDirectories
                val version = "@" + params.variant.mergedFlavor.versionName
                val iconNames = getIconName(mergedManifestFile)
                findIcons(resDirs, iconNames).forEach { icon ->
                    val icons = addTextToIcon(params.project, params.dimension,
                            icon, params.config, params.dimension, version, params.config.extraInfo)
                    if (icons != null) {
                        for (file in icons) {
                            processedIcons.add(file)
                        }
                    }
                }

                val mergeResTaskName = "merge${params.dimension}Resources"
                val mergeResTask = params.project.tasks.getByName(mergeResTaskName) as MergeResources
                val mergedResDir = mergeResTask.outputDir
                Aapt2Utils.compileResDir(params.project, mergedResDir, processedIcons)
                if (params.config.enableXmlIconRemove) {
                    removeXmlIconFiles(iconNames, mergedResDir)
                }
            }
        }
    }

    /**
     * To hack the awt on AS and Gradle building environment,
     * This is inherit from v1.x which forked from icon-version@akonior
     */
    private fun setAwtEnv() {
        // We want our font to come out looking pretty
        System.setProperty("awt.useSystemAAFontSettings", "on")
        System.setProperty("swing.aatext", "true")

        // Fix for Android Studio issue: Could not find class: apple.awt.CGraphicsEnvironment
        try {
            Class.forName(System.getProperty("java.awt.graphicsenv"))
        } catch (e: ClassNotFoundException) {
            Logger.e("java.awt.graphicsenv: $e")
            System.setProperty("java.awt.graphicsenv", "sun.awt.CGraphicsEnvironment")
        }

        //  Fix for AS issue: Toolkit not found: apple.awt.CToolkit
        try {
            Class.forName(System.getProperty("awt.toolkit"))
        } catch (e: ClassNotFoundException) {
            Logger.e("awt.toolkit: $e")
            System.setProperty("awt.toolkit", "sun.lwawt.macosx.LWCToolkit")
        }
    }

    /**
     * Icon name to search for in the app drawable folders
     * If no icon can be found in the manifest, IconOverlayGenerator#defaultIconName will be used
     */
    private fun getIconName(manifestFile: File): Array<String> {
        if (manifestFile.isDirectory || !manifestFile.exists()) {
            return arrayOf()
        }
        val manifestXml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifestFile)
        var regularIconName = manifestXml.getElementsByTagName(tagApplication).item(0)
                .attributes.getNamedItem(attrIcon)?.nodeValue
        var roundIconName = manifestXml.getElementsByTagName(tagApplication).item(0)
                .attributes.getNamedItem(attrRoundIcon)?.nodeValue
        regularIconName = regularIconName?.split("/")?.get(1) ?: defaultIconName
        roundIconName = roundIconName?.split("/")?.get(1) ?: defaultIconName + "_round"
        return arrayOf(regularIconName, roundIconName)
    }

    /**
     * Finds all icon files matching the icon specified in the given manifest.
     */
    private fun findIcons(where: Collection<File>, iconNames: Array<String>): Collection<File> {
        val result: MutableSet<File> = hashSetOf()
        where.forEach {
            it.walk()
                    .filter { dir ->
                        dir.name.contains("mipmap") || dir.name.contains("drawable")
                    }
                    .forEach { file ->
                        file.walk().forEach { image ->
                            iconNames.forEach { iconName ->
                                if (isIconFile(iconName, image)) {
                                    result.add(image)
                                }
                            }
                        }
                    }
        }
        return result
    }


    /**
     * Draws the given background and text over an image
     *
     * @param project   The Instance of org.gradle.api.Project
     * @param dimension The dimension contains buildType and flavor
     * @param image     The icon file that will be decorated
     * @param config    The configuration which controls how the overlay will appear
     * @param lines     The lines of text to be displayed
     */
    private fun addTextToIcon(project: Project,
                              dimension: String,
                              image: File,
                              config: ScratchPaperExtension = ScratchPaperExtension.DEFAULT_CONFIG,
                              vararg lines: String): Array<File>? {
        return BaseIconProcessor.getProcessor(project, dimension, image, config, lines)?.process()
    }

    /**
     * Experimental:
     * For now I didn't find an elegant approach to add a cover for xml icon,
     * so the ScratchPaper provide a temporary function to remove them.
     *
     * @param iconNames    the icons defined in the AndroidManifest.xml (icon & roundIcons)
     * @param mergedResDir it's a directory like /build/intermediates/res/merged/debug
     */
    private fun removeXmlIconFiles(iconNames: Array<String>, mergedResDir: File) {
        if (mergedResDir.isFile) {
            return
        }
        mergedResDir.walk().forEach { file ->
            iconNames.forEach { iconName ->
                if (file.isFile && file.name.contains("$iconName.xml.flat")) {
                    file.delete()
                }
            }

        }
    }

    private fun isIconFile(namePrefix: String, file: File): Boolean {
        return file.isFile && file.nameWithoutExtension == namePrefix
    }


}