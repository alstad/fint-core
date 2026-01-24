pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.3.0"
        id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
    }
}

rootProject.name = "build-logic"

include(":convention-plugins")
include(":generator-plugin")
