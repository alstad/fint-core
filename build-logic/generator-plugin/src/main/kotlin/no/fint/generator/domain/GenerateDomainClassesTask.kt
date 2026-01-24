package no.fint.generator.domain

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
import java.io.File

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
        val eaUmlExportFile =
            inputFile.orNull?.asFile
                ?: throw GradleException("generateDomainClasses: eaUmlExportFile is required.")
        val output =
            outputDir.orNull?.asFile
                ?: throw GradleException("generateDomainClasses: output directory is required.")
        val pkg =
            packageName.orNull?.trim()
                ?: throw GradleException("generateDomainClasses: package namespace is required.")

        if (!eaUmlExportFile.exists()) {
            throw GradleException("generateDomainClasses: input file does not exist: ${eaUmlExportFile.path}")
        }

        val model = UmlParser().parse(eaUmlExportFile.toPath())
        val classNameById = model.classes.values.associate { it.id to toClassName(it.name) }
        val classPackageById =
            model.classes.values.associate { umlClass ->
                umlClass.id to buildClassPackage(pkg, umlClass.packagePath)
            }
        val classFqnById =
            classNameById.mapValues { (id, className) ->
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

        logger.lifecycle("Generated ${model.classes.size} domain classes in ${output.path}.")
    }

    private fun renderClass(
        pkg: String,
        className: String,
        umlClass: UmlClass,
        classFqnById: Map<String, String>,
        classPackage: String,
    ): String {
        val imports = linkedSetOf<String>()
        val superType = resolveSuperType(umlClass, classFqnById, classPackage, imports)
        val complexDatatype = umlClass.complexDatatype()
        if (complexDatatype) {
            imports.add("no.novari.fint.core.domain.KompleksDatatype")
        }
        val superParams =
            collectConstructorParams(
                umlClass.generalization,
                classFqnById,
                classPackage,
                imports,
            )
        val ownParams =
            umlClass.properties
                .sortedBy { it.name ?: it.id }
                .map { property ->
                    buildConstructorParam(
                        property,
                        classFqnById,
                        classPackage,
                        imports,
                        complexDatatype,
                    )
                }
        val constructorParams =
            superParams.map { it.renderAsParameter() } +
                ownParams.map { it.renderAsProperty(complexDatatype) }

        val builder = StringBuilder()
        builder
            .append("package ")
            .append(pkg)
            .append('\n')
            .append('\n')
        if (imports.isNotEmpty()) {
            imports.sorted().forEach { importFqn ->
                builder.append("import ").append(importFqn).append('\n')
            }
            builder.append('\n')
        }
        umlClass.documentation?.let { doc ->
            formatKdoc(doc, "")?.let { kdoc -> builder.append(kdoc).append('\n') }
        }
        umlClass.deprecatedAnnotation()?.let { annotation ->
            builder.append(annotation).append('\n')
        }
        val classKeyword =
            when {
                umlClass.abstract -> "abstract class"
                umlClass.generalization == null && ownParams.isNotEmpty() -> "data class"
                else -> "class"
            }
        val superArgs =
            if (superParams.isNotEmpty()) {
                superParams.joinToString(", ") { "${it.name} = ${it.name}" }
            } else {
                ""
            }
        val superEntry =
            superType?.let { type ->
                if (superArgs.isBlank()) "$type()" else "$type($superArgs)"
            }
        val typeEntries =
            listOfNotNull(
                superEntry,
                if (complexDatatype) "KompleksDatatype" else null,
            )
        val extendsSuffix =
            if (typeEntries.isNotEmpty()) {
                " : ${typeEntries.joinToString(", ")}"
            } else {
                ""
            }
        if (constructorParams.isEmpty()) {
            builder
                .append(classKeyword)
                .append(' ')
                .append(className)
                .append(extendsSuffix)
                .append('\n')
            return builder.toString()
        }
        builder
            .append(classKeyword)
            .append(' ')
            .append(className)
            .append("(\n")
        builder.append(constructorParams.joinToString(",\n"))
        builder.append("\n)").append(extendsSuffix).append('\n')
        return builder.toString()
    }

    private fun UmlClass.deprecatedAnnotation(): String? {
        if (!deprecated) return null
        val message = sanitizeDeprecationMessage(deprecationMessage) ?: "Deprecated"
        return "@Deprecated(\"${escapeForKotlin(message)}\")"
    }

    private fun UmlProperty.deprecatedAnnotation(): String? {
        if (!deprecated) return null
        val message = sanitizeDeprecationMessage(deprecationMessage) ?: "Deprecated"
        return "@Deprecated(\"${escapeForKotlin(message)}\")"
    }

    private fun sanitizeDeprecationMessage(message: String?): String? {
        val trimmed = message?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return if (trimmed.endsWith('.')) trimmed.dropLast(1) else trimmed
    }

    private fun propertyType(
        property: UmlProperty,
        classFqnById: Map<String, String>,
        classPackage: String,
        imports: MutableSet<String>,
        complexDatatype: Boolean,
    ): String {
        val baseType =
            when {
                property.type != null -> {
                    val resolved =
                        resolveTypeName(
                            classFqnById[property.type!!.id],
                            classPackage,
                        )
                    if (resolved == null) {
                        toClassName(property.type!!.name)
                    } else {
                        resolved.importFqn?.let { imports.add(it) }
                        resolved.name
                    }
                }

                !property.primitiveType.isNullOrBlank() -> {
                    mapPrimitive(property.primitiveType!!)
                }

                else -> {
                    "String"
                }
            }
        val isMany = property.isMany()
        val rawType =
            when {
                isMany && !complexDatatype -> "Set<$baseType>"
                isMany -> "List<$baseType>"
                else -> baseType
            }
        return if (!isMany && property.isNullable()) "$rawType?" else rawType
    }

    private fun buildConstructorParam(
        property: UmlProperty,
        classFqnById: Map<String, String>,
        classPackage: String,
        imports: MutableSet<String>,
        complexDatatype: Boolean,
    ): ConstructorParam {
        val name = toPropertyName(property.name)
        val type = propertyType(property, classFqnById, classPackage, imports, complexDatatype)
        return ConstructorParam(
            name = name,
            type = type,
            annotation = property.deprecatedAnnotation(),
            documentation = property.documentation,
        )
    }

    private fun collectConstructorParams(
        umlClass: UmlClass?,
        classFqnById: Map<String, String>,
        classPackage: String,
        imports: MutableSet<String>,
    ): List<ConstructorParam> {
        if (umlClass == null) return emptyList()
        val inherited = collectConstructorParams(umlClass.generalization, classFqnById, classPackage, imports)
        val own =
            umlClass.properties
                .sortedBy { it.name ?: it.id }
                .map { property ->
                    buildConstructorParam(
                        property,
                        classFqnById,
                        classPackage,
                        imports,
                        umlClass.complexDatatype(),
                    )
                }
        return inherited + own
    }

    private fun resolveSuperType(
        umlClass: UmlClass,
        classFqnById: Map<String, String>,
        classPackage: String,
        imports: MutableSet<String>,
    ): String? {
        val generalization = umlClass.generalization
        if (generalization == null) {
            imports.add("no.novari.fint.core.domain.FintResource")
            return "FintResource"
        }
        val resolved = resolveTypeName(classFqnById[generalization.id], classPackage)
        if (resolved == null) return toClassName(generalization.name)
        resolved.importFqn?.let { imports.add(it) }
        return resolved.name
    }

    private fun mapPrimitive(value: String): String =
        when (value.trim().lowercase()) {
            "string" -> "String"
            "int", "integer", "short", "byte" -> "Int"
            "long" -> "Long"
            "double", "float", "decimal" -> "Double"
            "boolean", "bool" -> "Boolean"
            else -> "String"
        }

    private fun toClassName(name: String?): String {
        val raw = name?.trim()?.takeIf { it.isNotBlank() } ?: "Unnamed"
        val normalized = replaceNordicLetters(raw)
        val parts = normalized.replace(Regex("[^A-Za-z0-9]+"), " ").trim().split(Regex("\\s+"))
        val base =
            parts
                .joinToString("") { part ->
                    part.lowercase().replaceFirstChar { it.uppercase() }
                }.ifBlank { "Unnamed" }
        val safe = if (base.first().isDigit()) "C$base" else base
        return if (KOTLIN_KEYWORDS.contains(safe)) "${safe}Type" else safe
    }

    private fun toPropertyName(name: String?): String {
        val raw = name?.trim()?.takeIf { it.isNotBlank() } ?: "unnamed"
        val normalized = replaceNordicLetters(raw)
        val parts = normalized.replace(Regex("[^A-Za-z0-9]+"), " ").trim().split(Regex("\\s+"))
        val base =
            parts
                .joinToString("")
                .replaceFirstChar { it.lowercase() }
                .ifBlank { "unnamed" }
        val safe = if (base.first().isDigit()) "_$base" else base
        return if (KOTLIN_KEYWORDS.contains(safe)) "`$safe`" else safe
    }

    private fun escapeForKotlin(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")

    private fun replaceNordicLetters(value: String): String =
        value
            .replace('æ', 'a')
            .replace('Æ', 'A')
            .replace('ø', 'o')
            .replace('Ø', 'O')
            .replace('å', 'a')
            .replace('Å', 'A')

    private fun buildClassPackage(
        basePackage: String,
        packagePath: List<String>,
    ): String {
        if (packagePath.isEmpty()) return basePackage
        val segments = packagePath.map { toPackageSegment(it) }.filter { it.isNotBlank() }
        return if (segments.isEmpty()) basePackage else "$basePackage.${segments.joinToString(".")}"
    }

    private fun toPackageSegment(value: String): String {
        val normalized = replaceNordicLetters(value)
        val cleaned =
            normalized
                .trim()
                .replace(Regex("[^A-Za-z0-9]+"), "_")
                .trim('_')
                .lowercase()
        if (cleaned.isBlank()) return "model"
        val safe = if (cleaned.first().isDigit()) "p$cleaned" else cleaned
        return if (KOTLIN_KEYWORDS.contains(safe)) "${safe}_pkg" else safe
    }

    private fun resolveTypeName(
        typeFqn: String?,
        classPackage: String,
    ): TypeName? {
        if (typeFqn.isNullOrBlank()) return null
        val lastDot = typeFqn.lastIndexOf('.')
        if (lastDot <= 0) return TypeName(typeFqn, null)
        val typePackage = typeFqn.substring(0, lastDot)
        val typeName = typeFqn.substring(lastDot + 1)
        return if (typePackage == classPackage) {
            TypeName(typeName, null)
        } else {
            TypeName(typeName, typeFqn)
        }
    }

    private data class TypeName(
        val name: String,
        val importFqn: String?,
    )

    private data class ConstructorParam(
        val name: String,
        val type: String,
        val annotation: String?,
        val documentation: String?,
    ) {
        fun renderAsParameter(): String = "    $name: $type"

        fun renderAsProperty(useVal: Boolean): String {
            val keyword = if (useVal) "val" else "var"
            return when {
                documentation != null && annotation != null -> {
                    val kdoc = formatKdoc(documentation, "    ")
                    if (kdoc == null) {
                        "    $annotation\n    $keyword $name: $type"
                    } else {
                        "$kdoc\n    $annotation\n    $keyword $name: $type"
                    }
                }
                documentation != null -> {
                    val kdoc = formatKdoc(documentation, "    ")
                    if (kdoc == null) {
                        "    $keyword $name: $type"
                    } else {
                        "$kdoc\n    $keyword $name: $type"
                    }
                }
                annotation != null -> "    $annotation\n    $keyword $name: $type"
                else -> "    $keyword $name: $type"
            }
        }
    }

    companion object {
        private val KOTLIN_KEYWORDS =
            setOf(
                "as",
                "break",
                "class",
                "continue",
                "do",
                "else",
                "false",
                "for",
                "fun",
                "if",
                "in",
                "interface",
                "is",
                "null",
                "object",
                "package",
                "return",
                "super",
                "this",
                "throw",
                "true",
                "try",
                "typealias",
                "typeof",
                "val",
                "var",
                "when",
                "while",
            )
    }
}

private fun formatKdoc(value: String, indent: String): String? {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return null
    val lines = trimmed.lines().map { it.replace("*/", "* /").trimEnd() }
    return buildString {
        append(indent).append("/**").append('\n')
        lines.forEach { line ->
            if (line.isEmpty()) {
                append(indent).append(" *").append('\n')
            } else {
                append(indent).append(" * ").append(line).append('\n')
            }
        }
        append(indent).append(" */")
    }
}
