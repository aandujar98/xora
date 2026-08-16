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
            key = "to2:pal",
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
        assertEquals("to2:pal", obj["key"]?.jsonPrimitive?.content)
    }

    @Test
    fun storageWriteKeepsOwnerWritePermission() {
        // permission_write 0 makes the object permanently client-immutable — the second
        // invite to the same friend was rejected with "storage write rejected".
        // permission_read 1 is owner-only; friends never saw the invite. Public read is 2.
        val body = buildStorageWriteBody(
            collection = "xora_netplay_invites",
            key = "to2:pal",
            valueJson = "{}",
            permissionRead = XoraNetplayInvites.PERMISSION_PUBLIC_READ,
        )
        val obj = (body["objects"] as JsonArray)[0] as JsonObject
        assertEquals("1", obj["permission_write"]?.jsonPrimitive?.content)
        assertEquals("2", obj["permission_read"]?.jsonPrimitive?.content)
    }

    @Test
    fun storageWriteDefaultIsOwnerReadUntilCallersAskForPublic() {
        val body = buildStorageWriteBody(
            collection = "xora_netplay_invites",
            key = "to2:pal",
            valueJson = "{}",
        )
        val obj = (body["objects"] as JsonArray)[0] as JsonObject
        assertEquals("1", obj["permission_read"]?.jsonPrimitive?.content)
    }
}
