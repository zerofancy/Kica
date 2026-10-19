package top.ntutn.kica.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import top.ntutn.kica.data.cleanTags

internal fun JsonObject.tagList(): List<String>? {
    val array = get("tags") as? JsonArray ?: return null
    if (array.any { it !is JsonPrimitive || !it.isString }) return null
    return cleanTags(array.map { (it as JsonPrimitive).content })
}
