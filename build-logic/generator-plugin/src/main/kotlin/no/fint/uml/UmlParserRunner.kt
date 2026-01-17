package no.fint.uml

import java.nio.file.Paths

object UmlParserRunner {
    @JvmStatic
    fun main(args: Array<String>) {
        val file = if (args.isNotEmpty()) {
            Paths.get(args[0])
        } else {
            Paths.get("app/src/main/resources/FINT-informasjonsmodell.xml")
        }

        val model = UmlParser().parse(file)
        println("Classes: ${model.classes.size}")
        println("Properties: ${model.properties.size}")
        println("Associations: ${model.associations.size}")
        model.classes.values.forEach { umlClass ->
            println(umlClass.id)
        }
    }
}
