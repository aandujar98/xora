package com.arcadia.shell.xoranetwork

import org.junit.Assert.assertTrue
import org.junit.Test

class XoraNetworkClientConfigTest {

    @Test
    fun `website sign-in is available without a Nakama client key`() {
        assertTrue(XoraNetworkClient.isConfigured)
    }
}
