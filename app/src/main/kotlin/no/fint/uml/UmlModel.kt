package no.fint.uml

data class UmlModel(
    val classes: MutableMap<String, UmlClass> = mutableMapOf(),
    val properties: MutableMap<String, UmlProperty> = mutableMapOf(),
    val associations: MutableMap<String, UmlAssociation> = mutableMapOf(),
    val packages: MutableMap<String, List<String>> = mutableMapOf(),
    val types: MutableMap<String, String> = mutableMapOf(),
    val metadata: MutableMap<String, String> = mutableMapOf()
) {
    fun resolveReferences() {
        properties.values.forEach { property ->
            val typeId = property.typeId
            if (!typeId.isNullOrBlank()) {
                property.type = classes[typeId]
                if (property.type == null) {
                    property.primitiveType = types[typeId]
                }
            }
            if (property.type == null && property.primitiveType.isNullOrBlank()) {
                property.primitiveType = property.metadata["ea.properties.type"]
            }
            val associationId = property.associationId
            if (!associationId.isNullOrBlank()) {
                property.association = associations[associationId]
            }
            val association = property.association
            if (association != null && association.bidirectional) {
                val ends = association.memberEndIds
                if (ends.size == 2) {
                    val inverseId = when (property.id) {
                        ends[0] -> ends[1]
                        ends[1] -> ends[0]
                        else -> null
                    }
                    property.inverseProperty = inverseId?.let { properties[it] }
                }
            }
        }
        classes.values.forEach { umlClass ->
            val generalizationId = umlClass.generalizationId
            if (!generalizationId.isNullOrBlank()) {
                umlClass.generalization = classes[generalizationId]
            }
        }
        applyAssociationDocumentation()
    }

    fun applyAssociationDeprecations() {
        associations.values.forEach { association ->
            if (!association.deprecated) return@forEach
            val message = association.deprecationMessage
            val endIds = association.memberEndIds + association.ownedEndIds
            endIds.forEach { endId ->
                val property = properties[endId] ?: return@forEach
                property.deprecated = true
                property.deprecationMessage = message
            }
        }
    }

    private fun applyAssociationDocumentation() {
        associations.values.forEach { association ->
            if (association.bidirectional) {
                val sourceRole = association.sourceRoleName
                val sourceDoc = association.sourceDocumentation
                if (!sourceRole.isNullOrBlank() && !sourceDoc.isNullOrBlank()) {
                    findAssociationPropertyByName(association, sourceRole)?.addDocumentation(sourceDoc)
                }
                val targetRole = association.targetRoleName
                val targetDoc = association.targetDocumentation
                if (!targetRole.isNullOrBlank() && !targetDoc.isNullOrBlank()) {
                    findAssociationPropertyByName(association, targetRole)?.addDocumentation(targetDoc)
                }
            } else {
                val targetRole = association.targetRoleName
                val targetDoc = association.targetDocumentation
                if (!targetRole.isNullOrBlank() && !targetDoc.isNullOrBlank()) {
                    findAssociationPropertyByName(association, targetRole)?.addDocumentation(targetDoc)
                }
            }
        }
    }

    private fun findAssociationPropertyByName(
        association: UmlAssociation,
        roleName: String
    ): UmlProperty? {
        association.memberEndIds.forEach { endId ->
            val property = properties[endId]
            if (property?.name == roleName) {
                return property
            }
        }
        return null
    }
}
