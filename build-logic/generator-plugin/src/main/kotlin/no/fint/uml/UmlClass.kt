package no.fint.uml

data class UmlClass(
    val id: String,
    var name: String,
    var packagePath: List<String> = emptyList(),
    var deprecated: Boolean = false,
    var abstract: Boolean = false,
    var deprecationMessage: String? = null,
    val properties: MutableList<UmlProperty> = mutableListOf(),
    var generalizationId: String? = null,
    var generalization: UmlClass? = null,
    var documentation: String? = null,
    val stereotypes: MutableSet<String> = mutableSetOf(),
    val metadata: MutableMap<String, String> = mutableMapOf()
) {
    fun addDocumentation(value: String?) {
        if (value.isNullOrBlank()) return
        documentation = if (documentation.isNullOrBlank()) value else "${documentation}\n\n$value"
    }

    fun complexDatatype(): Boolean = !stereotypes.contains("hovedklasse") && !abstract
}
