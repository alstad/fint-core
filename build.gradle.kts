buildscript {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    dependencies {
        classpath("org.jlleitschuh.gradle:ktlint-gradle:12.1.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
    }
}

val configurationCacheRequested = gradle.startParameter.isConfigurationCacheRequested
if (!configurationCacheRequested) {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
}

subprojects {
    if (!configurationCacheRequested) {
        apply(plugin = "org.jlleitschuh.gradle.ktlint")
        extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension>("ktlint") {
            filter {
                exclude { it.file.toPath().startsWith(project.layout.buildDirectory.get().asFile.toPath()) }
            }
        }
        tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask>().configureEach {
            exclude { it.file.toPath().startsWith(project.layout.buildDirectory.get().asFile.toPath()) }
        }
        tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask>().configureEach {
            exclude { it.file.toPath().startsWith(project.layout.buildDirectory.get().asFile.toPath()) }
        }
    }
}
