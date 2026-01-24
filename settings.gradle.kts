// The settings file is the entry point of every Gradle build.
// Its primary purpose is to define the subprojects.
// It is also used for some aspects of project-wide configuration, like managing plugins, dependencies, etc.
// https://docs.gradle.org/current/userguide/settings_file_basics.html

pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.3.0"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0"
        id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
        id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
    }
}

dependencyResolutionManagement {
    // Use Maven Central as the default repository (where Gradle will download dependencies) in all subprojects.
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }
}

val enableFoojayResolver =
    providers.gradleProperty("enableFoojayResolver").orNull?.toBoolean() ?: false
if (enableFoojayResolver) {
    apply(plugin = "org.gradle.toolchains.foojay-resolver-convention")
}

// Include the `app` and `utils` subprojects in the build.
// If there are changes in only one of the projects, Gradle will rebuild only the one that has changed.
// Learn more about structuring projects with Gradle - https://docs.gradle.org/8.7/userguide/multi_project_builds.html
include(":app")
include(":utils")

rootProject.name = "fint-core"
