package no.fint.uml

import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

class UmlParser {
    fun parse(path: Path): UmlModel = Files.newInputStream(path).use { parse(it) }

    fun parse(inputStream: InputStream): UmlModel {
        val model = UmlModel()
        val packageStack = ArrayDeque<Pair<String, List<String>>>() // id to path
        val packagedElementStack = ArrayDeque<String>()
        val pendingComments = mutableMapOf<String, MutableList<String>>()

        var currentClassId: String? = null
        var currentAssociationId: String? = null
        var currentPropertyId: String? = null
        var currentElementIdRef: String? = null
        var currentAttributeIdRef: String? = null
        var currentCommentBody: String? = null
        var currentCommentTargetId: String? = null
        var currentConnectorIdRef: String? = null
        var currentConnectorIsAssociation = false
        var currentConnectorInSource = false
        var currentConnectorInTarget = false
        var currentConnectorInTags = false
        var currentElementInTags = false
        var currentAttributeInTags = false

        val factory = XMLInputFactory.newFactory().apply {
            setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
        }

        InputStreamReader(inputStream, Charset.forName("windows-1252")).use { reader ->
            val xml = factory.createXMLStreamReader(reader)
            while (xml.hasNext()) {
                when (xml.next()) {
                    XMLStreamConstants.START_ELEMENT -> {
                        when (xml.localName) {
                            "packagedElement" -> {
                                val type = xml.attr("type", XMI_NS)
                                packagedElementStack.addLast(type ?: "")
                                when (type) {
                                    "uml:Package" -> {
                                        val id = xml.attr("id", XMI_NS)
                                        if (!id.isNullOrBlank()) {
                                            val name = xml.attr("name")
                                            val path = buildPackagePath(packageStack, name)
                                            packageStack.addLast(id to path)
                                            model.packages[id] = path
                                        }
                                    }
                                    "uml:Class" -> {
                                        val id = xml.attr("id", XMI_NS)
                                        if (!id.isNullOrBlank()) {
                                            val name = xml.attr("name") ?: "Unnamed"
                                            val packagePath = packageStack.lastOrNull()?.second ?: emptyList()
                                            val umlClass = UmlClass(id = id, name = name, packagePath = packagePath)
                                            model.classes[id] = umlClass
                                            currentClassId = id
                                        }
                                    }
                                    "uml:Association" -> {
                                        val id = xml.attr("id", XMI_NS)
                                        if (!id.isNullOrBlank()) {
                                            val name = xml.attr("name")
                                            model.associations[id] = UmlAssociation(id = id, name = name)
                                            currentAssociationId = id
                                        }
                                    }
                                    "uml:PrimitiveType", "uml:DataType", "uml:Enumeration" -> {
                                        val id = xml.attr("id", XMI_NS)
                                        val name = xml.attr("name")
                                        if (!id.isNullOrBlank() && !name.isNullOrBlank()) {
                                            model.types[id] = name
                                        }
                                    }
                                }
                            }
                            "ownedAttribute" -> {
                                if (currentClassId != null) {
                                    val property = parseUmlProperty(xml)
                                    model.properties[property.id] = property
                                    model.classes[currentClassId]?.properties?.add(property)
                                    currentPropertyId = property.id
                                }
                            }
                            "ownedEnd" -> {
                                if (currentAssociationId != null) {
                                    val property = parseUmlProperty(xml)
                                    model.properties[property.id] = property
                                    model.associations[currentAssociationId]?.ownedEndIds?.add(property.id)
                                    currentPropertyId = property.id
                                }
                            }
                            "memberEnd" -> {
                                if (currentAssociationId != null) {
                                    val ref = xml.attr("idref", XMI_NS)
                                    if (!ref.isNullOrBlank()) {
                                        model.associations[currentAssociationId]?.memberEndIds?.add(ref)
                                    }
                                }
                            }
                            "type" -> {
                                if (currentPropertyId != null) {
                                    val property = model.properties[currentPropertyId]
                                    val ref = xml.attr("idref", XMI_NS)
                                    if (!ref.isNullOrBlank()) {
                                        property?.typeId = ref
                                    }
                                    val href = xml.attr("href")
                                    if (!href.isNullOrBlank() && property?.typeId.isNullOrBlank()) {
                                        property?.primitiveType = href.substringAfterLast('#', href)
                                    }
                                }
                            }
                            "lowerValue" -> {
                                if (currentPropertyId != null) {
                                    model.properties[currentPropertyId]?.lower = xml.attr("value")
                                }
                            }
                            "upperValue" -> {
                                if (currentPropertyId != null) {
                                    model.properties[currentPropertyId]?.upper = xml.attr("value")
                                }
                            }
                            "generalization" -> {
                                if (currentClassId != null) {
                                    val general = xml.attr("general")
                                    if (!general.isNullOrBlank()) {
                                        val umlClass = model.classes[currentClassId]
                                        if (umlClass != null && umlClass.generalizationId.isNullOrBlank()) {
                                            umlClass.generalizationId = general
                                        }
                                    }
                                }
                            }
                            "ownedComment" -> {
                                currentCommentBody = xml.attr("body")
                                currentCommentTargetId = null
                            }
                            "annotatedElement" -> {
                                currentCommentTargetId = xml.attr("idref", XMI_NS)
                            }
                            "connector" -> {
                                currentConnectorIdRef = xml.attr("idref", XMI_NS)
                                currentConnectorIsAssociation = currentConnectorIdRef != null &&
                                    model.associations.containsKey(currentConnectorIdRef)
                                currentConnectorInSource = false
                                currentConnectorInTarget = false
                                currentConnectorInTags = false
                            }
                            "source" -> {
                                if (currentConnectorIdRef != null) {
                                    currentConnectorInSource = true
                                    currentConnectorInTarget = false
                                }
                            }
                            "target" -> {
                                if (currentConnectorIdRef != null) {
                                    currentConnectorInSource = false
                                    currentConnectorInTarget = true
                                }
                            }
                            "role" -> {
                                if (currentConnectorIsAssociation) {
                                    val roleName = xml.attr("name")
                                    val association = model.associations[currentConnectorIdRef]
                                    if (association != null) {
                                        if (currentConnectorInSource) {
                                            association.sourceRoleName = roleName
                                        } else if (currentConnectorInTarget) {
                                            association.targetRoleName = roleName
                                        }
                                    }
                                }
                            }
                            "tags" -> {
                                if (currentConnectorIdRef != null) {
                                    currentConnectorInTags = true
                                } else if (currentElementIdRef != null) {
                                    currentElementInTags = true
                                } else if (currentAttributeIdRef != null) {
                                    currentAttributeInTags = true
                                }
                            }
                            "tag" -> {
                                if (currentConnectorIdRef != null && currentConnectorInTags) {
                                    val name = xml.attr("name")
                                    if (name == "DEPRECATED") {
                                        val association = model.associations[currentConnectorIdRef]
                                        if (association != null) {
                                            association.deprecated = true
                                            association.deprecationMessage = xml.attr("value")
                                        }
                                    }
                                } else if (currentElementIdRef != null && currentElementInTags) {
                                    val name = xml.attr("name")
                                    if (name == "DEPRECATED") {
                                        val umlClass = model.classes[currentElementIdRef]
                                        if (umlClass != null) {
                                            umlClass.deprecated = true
                                            umlClass.deprecationMessage = xml.attr("value")
                                        }
                                    }
                                } else if (currentAttributeIdRef != null && currentAttributeInTags) {
                                    val name = xml.attr("name")
                                    if (name == "DEPRECATED") {
                                        val property = model.properties[currentAttributeIdRef]
                                        if (property != null) {
                                            property.deprecated = true
                                            property.deprecationMessage = xml.attr("value")
                                        }
                                    }
                                }
                            }
                            "element" -> {
                                currentElementIdRef = xml.attr("idref", XMI_NS)
                            }
                            "properties" -> {
                                if (currentConnectorIdRef != null) {
                                    val eaType = xml.attr("ea_type")
                                    if (eaType == "Association") {
                                        currentConnectorIsAssociation = true
                                        val direction = xml.attr("direction")
                                        val association = model.associations[currentConnectorIdRef]
                                        if (association != null) {
                                            association.bidirectional = direction == "Bi-Directional"
                                        }
                                    }
                                }
                                when {
                                    currentAttributeIdRef != null -> {
                                        val property = model.properties[currentAttributeIdRef]
                                        property?.metadata?.putAll(prefixedAttrs("ea.properties.", xml))
                                    }
                                    currentElementIdRef != null -> {
                                        val umlClass = model.classes[currentElementIdRef]
                                        if (umlClass != null) {
                                            umlClass.addDocumentation(xml.attr("documentation"))
                                            umlClass.metadata.putAll(prefixedAttrs("ea.properties.", xml))
                                        }
                                    }
                                }
                            }
                            "project" -> {
                                if (currentElementIdRef != null) {
                                    model.classes[currentElementIdRef]?.metadata?.putAll(prefixedAttrs("ea.project.", xml))
                                }
                            }
                            "extendedProperties" -> {
                                if (currentElementIdRef != null) {
                                    model.classes[currentElementIdRef]?.metadata?.putAll(prefixedAttrs("ea.extended.", xml))
                                }
                            }
                            "attribute" -> {
                                currentAttributeIdRef = xml.attr("idref", XMI_NS)
                            }
                            "documentation" -> {
                                if (currentAttributeIdRef != null) {
                                    val text = xml.attr("value")
                                    model.properties[currentAttributeIdRef]?.addDocumentation(text)
                                } else if (currentConnectorIsAssociation) {
                                    val text = xml.attr("value")
                                    val association = model.associations[currentConnectorIdRef]
                                    if (association != null) {
                                        if (currentConnectorInSource) {
                                            association.addSourceDocumentation(text)
                                        } else if (currentConnectorInTarget) {
                                            association.addTargetDocumentation(text)
                                        }
                                    }
                                }
                            }
                            "bounds" -> {
                                if (currentAttributeIdRef != null) {
                                    val property = model.properties[currentAttributeIdRef]
                                    if (property?.lower.isNullOrBlank()) {
                                        property?.lower = xml.attr("lower")
                                    }
                                    if (property?.upper.isNullOrBlank()) {
                                        property?.upper = xml.attr("upper")
                                    }
                                }
                            }
                            "stereotype" -> {
                                if (currentAttributeIdRef != null) {
                                    val stereo = xml.attr("stereotype")
                                    if (!stereo.isNullOrBlank()) {
                                        model.properties[currentAttributeIdRef]?.stereotypes?.add(stereo)
                                    }
                                }
                            }
                            else -> {
                                if (xml.namespaceURI == CUSTOM_PROFILE_NS) {
                                    val stereotype = xml.localName
                                    val classId = xml.attr("base_Class")
                                    val propertyId = xml.attr("base_Property")
                                    if (!classId.isNullOrBlank()) {
                                        model.classes[classId]?.stereotypes?.add(stereotype)
                                    }
                                    if (!propertyId.isNullOrBlank()) {
                                        model.properties[propertyId]?.stereotypes?.add(stereotype)
                                    }
                                }
                            }
                        }
                    }
                    XMLStreamConstants.END_ELEMENT -> {
                        when (xml.localName) {
                            "packagedElement" -> {
                                val type = packagedElementStack.removeLastOrNull()
                                when (type) {
                                    "uml:Package" -> packageStack.removeLastOrNull()
                                    "uml:Class" -> currentClassId = null
                                    "uml:Association" -> currentAssociationId = null
                                }
                            }
                            "ownedAttribute", "ownedEnd" -> currentPropertyId = null
                            "element" -> {
                                currentElementIdRef = null
                            }
                            "attribute" -> currentAttributeIdRef = null
                            "connector" -> {
                                currentConnectorIdRef = null
                                currentConnectorIsAssociation = false
                                currentConnectorInSource = false
                                currentConnectorInTarget = false
                                currentConnectorInTags = false
                            }
                            "source" -> currentConnectorInSource = false
                            "target" -> currentConnectorInTarget = false
                            "tags" -> {
                                currentConnectorInTags = false
                                currentElementInTags = false
                                currentAttributeInTags = false
                            }
                            "ownedComment" -> {
                                val targetId = currentCommentTargetId ?: currentClassId
                                if (!targetId.isNullOrBlank() && !currentCommentBody.isNullOrBlank()) {
                                    pendingComments.getOrPut(targetId) { mutableListOf() }.add(currentCommentBody!!)
                                }
                                currentCommentBody = null
                                currentCommentTargetId = null
                            }
                        }
                    }
                }
            }
        }

        pendingComments.forEach { (targetId, comments) ->
            val text = comments.joinToString("\n\n")
            model.classes[targetId]?.addDocumentation(text)
            model.properties[targetId]?.addDocumentation(text)
        }

        model.resolveReferences()
        model.applyAssociationDeprecations()
        return model
    }

    private fun parseUmlProperty(xml: XMLStreamReader): UmlProperty {
        val id = xml.attr("id", XMI_NS) ?: "unknown"
        val property = UmlProperty(
            id = id,
            name = xml.attr("name"),
            associationId = xml.attr("association"),
            aggregation = xml.attr("aggregation")
        )
        property.metadata.putAll(
            mapOfNotNull(
                "uml.visibility" to xml.attr("visibility"),
                "uml.isStatic" to xml.attr("isStatic"),
                "uml.isReadOnly" to xml.attr("isReadOnly"),
                "uml.isDerived" to xml.attr("isDerived"),
                "uml.isOrdered" to xml.attr("isOrdered"),
                "uml.isUnique" to xml.attr("isUnique"),
                "uml.isDerivedUnion" to xml.attr("isDerivedUnion")
            )
        )
        return property
    }

    private fun buildPackagePath(stack: ArrayDeque<Pair<String, List<String>>>, name: String?): List<String> {
        val parentPath = stack.lastOrNull()?.second ?: emptyList()
        if (name.isNullOrBlank()) return parentPath
        if (SKIPPED_PACKAGE_NAMES.contains(name)) return parentPath
        return parentPath + name
    }

    private fun prefixedAttrs(prefix: String, xml: XMLStreamReader): Map<String, String> {
        val attrs = mutableMapOf<String, String>()
        for (i in 0 until xml.attributeCount) {
            val key = xml.getAttributeLocalName(i)
            val value = xml.getAttributeValue(i)
            attrs["$prefix$key"] = value
        }
        return attrs
    }

    private fun mapOfNotNull(vararg pairs: Pair<String, String?>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        pairs.forEach { (key, value) ->
            if (!value.isNullOrBlank()) {
                result[key] = value
            }
        }
        return result
    }

    private fun XMLStreamReader.attr(localName: String, namespace: String? = null): String? =
        getAttributeValue(namespace, localName)

    companion object {
        private const val XMI_NS = "http://schema.omg.org/spec/XMI/2.1"
        private const val CUSTOM_PROFILE_NS = "http://www.sparxsystems.com/profiles/thecustomprofile/1.0"
        private val SKIPPED_PACKAGE_NAMES = setOf("Model", "FINT")
    }
}
