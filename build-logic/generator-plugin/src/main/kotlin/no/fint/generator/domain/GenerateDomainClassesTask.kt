package no.fint.generator.domain

import java.io.File
import no.fint.uml.UmlClass
import no.fint.uml.UmlParser
import no.fint.uml.UmlProperty
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

abstract class GenerateDomainClassesTask : DefaultTask() {
    @get:InputFile
    abstract val inputFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val packageName: Property<String>

    @Option(option = "input", description = "Path to UML XMI input file.")
    fun setInputPath(path: String) {
        inputFile.set(project.layout.file(project.provider { project.file(path) }))
    }

    @Option(option = "output", description = "Output directory for generated Kotlin sources.")
    fun setOutputPath(path: String) {
        outputDir.set(project.layout.dir(project.provider { project.file(path) }))
    }

    @Option(option = "package", description = "Kotlin package namespace for generated sources.")
    fun setPackageNameOption(value: String) {
        packageName.set(value)
    }

    @TaskAction
    fun generate() {
        val eaUmlExportFile = inputFile.orNull?.asFile
            ?: throw GradleException("generateDomainClasses: eaUmlExportFile is required.")
        val output = outputDir.orNull?.asFile
            ?: throw GradleException("generateDomainClasses: output directory is required.")
        val pkg = packageName.orNull?.trim()
            ?: throw GradleException("generateDomainClasses: package namespace is required.")

        if (!eaUmlExportFile.exists()) {
            throw GradleException("generateDomainClasses: input file does not exist: ${eaUmlExportFile.path}")
        }

        val model = UmlParser().parse(eaUmlExportFile.toPath())
        val classNameById = model.classes.values.associate { it.id to toClassName(it.name) }
        val classPackageById = model.classes.values.associate { umlClass ->
            umlClass.id to buildClassPackage(pkg, umlClass.packagePath)
        }
        val classFqnById = classNameById.mapValues { (id, className) ->
            val packageName = classPackageById[id] ?: pkg
            "$packageName.$className"
        }

        model.classes.values
            .sortedBy { it.name }
            .forEach { umlClass ->
                val className = classNameById[umlClass.id] ?: "Unnamed"
                val packageName = classPackageById[umlClass.id] ?: pkg
                val packageDir = File(output, packageName.replace('.', File.separatorChar))
                if (!packageDir.exists() && !packageDir.mkdirs()) {
                    throw GradleException("generateDomainClasses: failed to create output dir: ${packageDir.path}")
                }
                val file = File(packageDir, "$className.kt")
                file.writeText(renderClass(packageName, className, umlClass, classFqnById, packageName))
            }

        logger.lifecycle("Generated ${model.classes.size} domain classes in ${packageDir.path}.")
    }

    private fun renderClass(
        pkg: String,
        className: String,
        umlClass: UmlClass,
        classFqnById: Map<String, String>,
        classPackage: String
    ): String {
        val properties = umlClass.properties
            .sortedBy { it.name ?: it.id }
            .map { property ->
                val propName = toPropertyName(property.name)
                val propType = propertyType(property, classFqnById, classPackage)
                val annotation = property.deprecatedAnnotation()
                val prefix = if (annotation == null) "" else "$annotation "
                "    ${prefix}val $propName: $propType"
            }

        val builder = StringBuilder()
        builder.append("package ").append(pkg).append('\n').append('\n')
        umlClass.deprecatedAnnotation()?.let { annotation ->
            builder.append(annotation).append('\n')
        }
        if (properties.isEmpty()) {
            builder.append("data class ").append(className).append('\n')
            return builder.toString()
        }
        builder.append("data class ").append(className).append("(\n")
        builder.append(properties.joinToString(",\n"))
        builder.append("\n)\n")
        return builder.toString()
    }

    private fun UmlClass.deprecatedAnnotation(): String? {
        if (!deprecated) return null
        val message = deprecationMessage?.takeIf { it.isNotBlank() } ?: "Deprecated"
        return "@Deprecated(\"${escapeForKotlin(message)}\")"
    }

    private fun UmlProperty.deprecatedAnnotation(): String? {
        if (!deprecated) return null
        val message = deprecationMessage?.takeIf { it.isNotBlank() } ?: "Deprecated"
        return "@Deprecated(\"${escapeForKotlin(message)}\")"
    }

    private fun propertyType(
        property: UmlProperty,
        classFqnById: Map<String, String>,
        classPackage: String
    ): String {
        val baseType = when {
            property.type != null -> resolveTypeName(
                classFqnById[property.type!!.id],
                classPackage
            ) ?: toClassName(property.type!!.name)
            !property.primitiveType.isNullOrBlank() -> mapPrimitive(property.primitiveType!!)
            else -> "String"
        }
        val isList = property.upper == "*" || (property.upper?.toIntOrNull() ?: 1) > 1
        val rawType = if (isList) "List<$baseType>" else baseType
        val nullable = property.lower.isNullOrBlank() || property.lower == "0"
        return if (nullable) "$rawType?" else rawType
    }

    private fun mapPrimitive(value: String): String {
        return when (value.trim().lowercase()) {
            "string" -> "String"
            "int", "integer", "short", "byte" -> "Int"
            "long" -> "Long"
            "double", "float", "decimal" -> "Double"
            "boolean", "bool" -> "Boolean"
            else -> "String"
        }
    }

    private fun toClassName(name: String?): String {
        val raw = name?.trim()?.takeIf { it.isNotBlank() } ?: "Unnamed"
        val parts = raw.replace(Regex("[^A-Za-z0-9]+"), " ").trim().split(Regex("\\s+"))
        val base = parts.joinToString("") { part ->
            part.lowercase().replaceFirstChar { it.uppercase() }
        }.ifBlank { "Unnamed" }
        val safe = if (base.first().isDigit()) "C$base" else base
        return if (KOTLIN_KEYWORDS.contains(safe)) "${safe}Type" else safe
    }

    private fun toPropertyName(name: String?): String {
        val raw = name?.trim()?.takeIf { it.isNotBlank() } ?: "unnamed"
        val parts = raw.replace(Regex("[^A-Za-z0-9]+"), " ").trim().split(Regex("\\s+"))
        val base = parts.mapIndexed { index, part ->
            val lower = part.lowercase()
            if (index == 0) lower else lower.replaceFirstChar { it.uppercase() }
        }.joinToString("").ifBlank { "unnamed" }
        val safe = if (base.first().isDigit()) "_$base" else base
        return if (KOTLIN_KEYWORDS.contains(safe)) "`$safe`" else safe
    }

    private fun escapeForKotlin(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
    }

    private fun buildClassPackage(basePackage: String, packagePath: List<String>): String {
        if (packagePath.isEmpty()) return basePackage
        val segments = packagePath.map { toPackageSegment(it) }.filter { it.isNotBlank() }
        return if (segments.isEmpty()) basePackage else "$basePackage.${segments.joinToString(".")}"
    }

    private fun toPackageSegment(value: String): String {
        val cleaned = value.trim()
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .trim('_')
            .lowercase()
        if (cleaned.isBlank()) return "model"
        val safe = if (cleaned.first().isDigit()) "p$cleaned" else cleaned
        return if (KOTLIN_KEYWORDS.contains(safe)) "${safe}_pkg" else safe
    }

    private fun resolveTypeName(typeFqn: String?, classPackage: String): String? {
        if (typeFqn.isNullOrBlank()) return null
        val lastDot = typeFqn.lastIndexOf('.')
        if (lastDot <= 0) return typeFqn
        val typePackage = typeFqn.substring(0, lastDot)
        val typeName = typeFqn.substring(lastDot + 1)
        return if (typePackage == classPackage) typeName else typeFqn
    }

    companion object {
        private val KOTLIN_KEYWORDS = setOf(
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in",
            "interface", "is", "null", "object", "package", "return", "super", "this", "throw",
            "true", "try", "typealias", "typeof", "val", "var", "when", "while"
        )
    }
}
