package com.discord.socialsdk

import android.app.Activity
import android.os.Bundle

/**
 * No-op stub compiled only when `discord_partner_sdk.aar` is absent so the OAuth activity
 * declared in the app manifest still resolves. Replaced by the real Social SDK class when the
 * AAR is on the classpath.
 */
class AuthenticationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
