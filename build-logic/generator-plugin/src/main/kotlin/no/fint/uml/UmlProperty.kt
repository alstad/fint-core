package no.fint.uml

data class UmlProperty(
    val id: String,
    var name: String? = null,
    var typeId: String? = null,
    var type: UmlClass? = null,
    var primitiveType: String? = null,
    var associationId: String? = null,
    var association: UmlAssociation? = null,
    var inverseProperty: UmlProperty? = null,
    var aggregation: String? = null,
    var lower: String? = null,
    var upper: String? = null,
    var deprecated: Boolean = false,
    var deprecationMessage: String? = null,
    var documentation: String? = null,
    val stereotypes: MutableSet<String> = mutableSetOf(),
    val metadata: MutableMap<String, String> = mutableMapOf()
) {
    fun addDocumentation(value: String?) {
        if (value.isNullOrBlank()) return
        documentation = if (documentation.isNullOrBlank()) value else "${documentation}\n\n$value"
    }

    fun isMany(): Boolean = upper == "*" || upper == "-1"

    fun isRequired(): Boolean = lower == "1"

    fun isNullable(): Boolean = !isRequired()
}
