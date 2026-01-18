import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

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

tasks.named("generateDomainClasses") {
    val inputFile = javaClass.getMethod("getInputFile").invoke(this) as RegularFileProperty
    val outputDir = javaClass.getMethod("getOutputDir").invoke(this) as DirectoryProperty
    val packageName = javaClass.getMethod("getPackageName").invoke(this) as Property<String>
    inputFile.set(layout.projectDirectory.file("src/main/resources/FINT-informasjonsmodell.xml"))
    outputDir.set(generatedDomainDir)
    packageName.set("no.novari.fint.core.domain")
}

tasks.named("compileKotlin") {
    dependsOn(tasks.named("generateDomainClasses"))
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
