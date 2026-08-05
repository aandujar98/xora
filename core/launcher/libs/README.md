# Discord Social SDK (optional)

Live Discord Rich Presence on Android requires Discord’s proprietary **Social SDK** partner
binary. It is **not** published on Maven Central.

## What to download

1. Open the [Discord Developer Portal](https://discord.com/developers/applications) → your app
   (SORA Application ID `1531690290526683176`).
2. Sidebar → **Discord Social SDK** → **Downloads**.
3. Download the latest **C++ / Android** Social SDK zip (e.g. `DiscordSocialSdk-1.9.x.zip`).
4. From the zip, copy the **release** Android archive:

```
discord_social_sdk/lib/release/discord_partner_sdk.aar
```

to:

```
core/launcher/libs/discord_partner_sdk.aar
```

(Prefer the non-Krisp `discord_partner_sdk.aar` over `discord_partner_sdk_krisp.aar` unless you
need noise cancellation — Krisp is larger.)

5. Optional fallback headers (Prefab already ships them inside the AAR):

```
core/launcher/libs/discord_partner_sdk/include/discordpp.h
core/launcher/libs/discord_partner_sdk/include/cdiscord.h
```

## Gradle behavior

- **Without** the AAR: the app compiles and runs. Rich Presence stays on the status-bridge path;
  Settings shows **SDK missing** with download steps.
- **With** the AAR: Gradle adds the dependency, enables Prefab + NDK, and builds `libsora_discord`
  (JNI bridge). Runtime can publish real Rich Presence after Discord account linking.

Do **not** commit the AAR or client secrets (already gitignored). The public Application ID alone
is safe to keep in preferences / defaults.

## Portal setup (account linking)

1. Set the Discord application **name** to **SORA** (this is the “Playing SORA” line friends see).
2. Register the mobile OAuth2 redirect URI on the OAuth2 tab:

```
discord-1531690290526683176:/authorize/callback
```

3. Enable **Public Client** on that tab so the on-device Social SDK can exchange the auth code
   for tokens (no client secret in the APK).

The app manifest already deep-links `discord-1531690290526683176` to
`com.discord.socialsdk.AuthenticationActivity`.

### Verify Rich Presence

1. Install Discord on the device and open it once.
2. In SORA: Settings → Social (or Social → Discord) → **Link Discord account**.
3. Complete OAuth; status should become **Connected · Playing SORA**.
4. On another Discord client (or the same mobile app’s profile), confirm **Playing SORA** with
   details like “In the library” / “Browsing {game}”.
5. Logcat filter (no secrets logged): `SoraDiscord` (also `DiscordBridge` for native).
   Look for `Social SDK Ready`, `UpdateRichPresence ok`, or `UpdateRichPresence FAILED`.

Handhelds need Social SDK **account link** + Discord installed and signed in. Classic
unauthenticated RPC only works with Discord **desktop** on the same machine.

Docs:

- [Setting Rich Presence](https://docs.discord.com/developers/discord-social-sdk/development-guides/setting-rich-presence)
- [Account Linking on Mobile](https://docs.discord.com/developers/discord-social-sdk/development-guides/account-linking-on-mobile)

## Requirements when enabling the AAR

- Android NDK (side-by-side with the SDK used by this project)
- CMake 3.22+
- `androidx.browser` (already wired when the AAR is present)
- Rebuild after dropping the AAR (debug and release both pick it up)
