package no.fint.uml

data class UmlAssociation(
    val id: String,
    var name: String? = null,
    val memberEndIds: MutableList<String> = mutableListOf(),
    val ownedEndIds: MutableList<String> = mutableListOf(),
    var bidirectional: Boolean = false,
    var deprecated: Boolean = false,
    var deprecationMessage: String? = null,
    var sourceRoleName: String? = null,
    var targetRoleName: String? = null,
    var sourceDocumentation: String? = null,
    var targetDocumentation: String? = null,
    val metadata: MutableMap<String, String> = mutableMapOf()
) {
    fun addSourceDocumentation(value: String?) {
        if (value.isNullOrBlank()) return
        sourceDocumentation = if (sourceDocumentation.isNullOrBlank()) value else "${sourceDocumentation}\n\n$value"
    }

    fun addTargetDocumentation(value: String?) {
        if (value.isNullOrBlank()) return
        targetDocumentation = if (targetDocumentation.isNullOrBlank()) value else "${targetDocumentation}\n\n$value"
    }
}
