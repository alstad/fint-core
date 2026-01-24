import no.fint.generator.domain.GenerateDomainClassesTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `build-logic/convention-plugins/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")

    // Apply the Application plugin to add support for building an executable JVM application.
    application

    // Apply the FINT generator plugin.
    id("no.fint.generator")
}

dependencies {
    // Project "app" depends on project "utils". (Project paths are separated with ":", so ":utils" refers to the top-level "utils" project.)
    implementation(project(":utils"))
}

val generatedDomainDir = layout.buildDirectory.dir("generated/sources/fint-domain")

val generateDomainClasses =
    tasks.named<GenerateDomainClassesTask>("generateDomainClasses") {
        inputFile.set(layout.projectDirectory.file("src/main/resources/FINT-informasjonsmodell.xml"))
        outputDir.set(generatedDomainDir)
        packageName.set("no.novari.fint.core.domain")
    }

tasks.named<KotlinCompile>("compileKotlin") {
    dependsOn(generateDomainClasses)
}

kotlin {
    sourceSets.main {
        kotlin.srcDir(generatedDomainDir)
    }
}

application {
    // Define the Fully Qualified Name for the application main class
    // (Note that Kotlin compiles `App.kt` to a class with FQN `com.example.app.AppKt`.)
    mainClass = "org.example.app.AppKt"
}

pluginManager.withPlugin("org.jlleitschuh.gradle.ktlint") {
    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension>("ktlint") {
        filter {
            include("**/src/main/kotlin/**")
            include("**/src/test/kotlin/**")
            exclude("**/build/**")
        }
    }

    tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask>().configureEach {
        if (name.contains("MainSourceSet")) {
            setSource(fileTree("src/main/kotlin"))
        }
    }

    tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask>().configureEach {
        if (name.contains("MainSourceSet")) {
            setSource(fileTree("src/main/kotlin"))
        }
    }

    tasks.named<org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask>("runKtlintFormatOverMainSourceSet") {
        setSource(fileTree("src/main/kotlin"))
    }

    tasks.named<org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask>("runKtlintCheckOverMainSourceSet") {
        setSource(fileTree("src/main/kotlin"))
    }
}
