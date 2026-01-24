plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    // Provide the Kotlin Gradle plugin on the classpath for precompiled script plugins.
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
}

kotlin {
    jvmToolchain(25)
}
