package com.arcadia.shell.xoranetwork

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XoraNetworkStorageWriteTest {

    @Test
    fun storageWriteValueIsAJsonStringNotAnObject() {
        val encoded = """{"code":"ABC234","toUsername":"pal","gameTitle":"Sonic"}"""
        val body = buildStorageWriteBody(
            collection = "xora_netplay_invites",
            key = "to:pal",
            valueJson = encoded,
        )
        val objects = body["objects"] as JsonArray
        val obj = objects[0] as JsonObject
        val value = obj["value"] as JsonPrimitive
        assertTrue(
            "Nakama WriteStorageObject.value must be a string, not a nested object",
            value.isString,
        )
        assertEquals(encoded, value.content)
        assertEquals("xora_netplay_invites", obj["collection"]?.jsonPrimitive?.content)
        assertEquals("to:pal", obj["key"]?.jsonPrimitive?.content)
    }
}
