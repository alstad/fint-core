package no.fint.generator

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.api.DefaultTask

class FintGeneratorPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("generateDomainClasses", GenerateDomainClassesTask::class.java) {
            group = "fint generation"
            description = "Generate domain classes from the UML model."
        }
        project.tasks.register("generateRestApiClasses", GenerateRestApiClassesTask::class.java) {
            group = "fint generation"
            description = "Generate Jackson annotated classes for Spring RestController APIs."
        }
        project.tasks.register("generateGraphQlClasses", GenerateGraphQlClassesTask::class.java) {
            group = "fint generation"
            description = "Generate Spring GraphQL API classes from the UML model."
        }
    }
}

abstract class GenerateDomainClassesTask : DefaultTask() {
    @TaskAction
    fun generate() {
        logger.lifecycle("Generating domain classes.")
    }
}

abstract class GenerateRestApiClassesTask : DefaultTask() {
    @TaskAction
    fun generate() {
        logger.lifecycle("Generating Jackson annotated REST API classes.")
    }
}

abstract class GenerateGraphQlClassesTask : DefaultTask() {
    @TaskAction
    fun generate() {
        logger.lifecycle("Generating GraphQL API classes.")
    }
}
