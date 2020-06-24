rootProject.name = "scratch-paper"
plugins{
    id("com.gradle.enterprise") version("3.3.4")
}
// As part of making the publishing plugins stable, the 'deferred configurable' behavior
// of the 'publishing {}' block has been deprecated.
// In Gradle 5.0 the 'enableFeaturePreview('STABLE_PUBLISHING')' flag will be removed
// and the new behavior will become the default.
// Please add 'enableFeaturePreview('STABLE_PUBLISHING')' to your settings file
// and do a test run by publishing to a local repository.
// If all artifacts are published as expected, there is nothing else to do.
