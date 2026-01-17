plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

gradlePlugin {
    plugins {
        register("fintGeneratorPlugin") {
            id = "no.fint.generator"
            implementationClass = "no.fint.generator.FintGeneratorPlugin"
        }
    }
}
