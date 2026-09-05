package com.arcadia.shell.xoranetwork

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class XoraNetworkClientConfigTest {

    @Test
    fun `bundled client key marks the network client configured`() {
        assertTrue(XoraNetworkClient.isConfigured)
        assertEquals(
            "4badd4561ab8bea17a809d4d2f1ef6ee7eaed5f87c364b25",
            BuildConfig.XORA_NETWORK_SERVER_KEY,
        )
    }
}
