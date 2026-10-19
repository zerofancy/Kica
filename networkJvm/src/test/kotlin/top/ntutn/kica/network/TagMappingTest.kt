package top.ntutn.kica.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TagMappingTest {
    @Test
    fun missingOrMalformedTagsAreUnknownWhileEmptyArrayIsKnown() {
        listOf("{}", """{"tags":null}""", """{"tags":"travel"}""", """{"tags":["ok",1]}""")
            .forEach { assertNull(Json.parseToJsonElement(it).jsonObject.tagList()) }
        assertEquals(emptyList(), Json.parseToJsonElement("""{"tags":[]}""").jsonObject.tagList())
        assertEquals(listOf("Travel", "旅行"), Json.parseToJsonElement(
            """{"tags":[" Travel ","travel","","旅行"]}""",
        ).jsonObject.tagList())
    }
}
