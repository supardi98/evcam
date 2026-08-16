package net.supardi.evcam.logic

import org.json.JSONArray
import org.json.JSONObject

data class WatermarkElement(
    val id: String,
    val type: WatermarkElementType,
    var content: String,
    var quadrant: WatermarkQuadrant,
    var size: Int = 14
)

fun serializeWatermarkElements(elements: List<WatermarkElement>): String {
    val array = JSONArray()
    for (e in elements) {
        val obj = JSONObject()
        obj.put("id", e.id)
        obj.put("type", e.type.name)
        obj.put("content", e.content)
        obj.put("quadrant", e.quadrant.name)
        obj.put("size", e.size)
        array.put(obj)
    }
    return array.toString()
}

fun deserializeWatermarkElements(jsonStr: String?): List<WatermarkElement> {
    val list = mutableListOf<WatermarkElement>()
    if (jsonStr != null && jsonStr.isNotEmpty()) {
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(WatermarkElement(
                    id = obj.getString("id"),
                    type = WatermarkElementType.valueOf(obj.getString("type")),
                    content = obj.getString("content"),
                    quadrant = WatermarkQuadrant.valueOf(obj.getString("quadrant")),
                    size = obj.optInt("size", 14)
                ))
            }
        } catch(e: Exception) {}
    }
    if (list.isEmpty()) {
        list.add(WatermarkElement("1", WatermarkElementType.TEXT, "Shot on EV Cam Pro", WatermarkQuadrant.BOTTOM_LEFT))
    }
    return list
}
