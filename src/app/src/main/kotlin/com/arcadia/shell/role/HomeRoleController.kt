package com.arcadia.shell.role

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class HomeRoleState(
    /** Whether the shell currently advertises itself as a home-screen candidate at all. */
    val isHomeCandidate: Boolean = false,
    /** Whether the system is actually using the shell as the home screen. */
    val isDefaultHome: Boolean = false,
)

/**
 * Controls whether the shell can act as the device home screen. This is deliberately a two-step
 * opt-in: enabling the alias only adds the shell to the chooser, and the user still has to pick it
 * in system settings.
 *
 * The HOME [activity-alias] lives under the manifest [namespace] (`com.arcadia.shell.HomeAlias`)
 * while the installed package is [applicationId] (`com.sora.shell`). The
 * [ComponentName] is resolved from PackageManager so a rename of either side cannot desync the
 * enable/disable path from the alias the system actually sees.
 */
@Singleton
class HomeRoleController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val packageManager: PackageManager get() = context.packageManager

    private val revision = MutableStateFlow(0)

    fun state(): Flow<HomeRoleState> = revision.map { read() }

    fun setHomeCandidate(enabled: Boolean) {
        val component = resolveAliasComponent()
        packageManager.setComponentEnabledSetting(
            component,
            if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP,
        )
        revision.value += 1
    }

    fun refresh() {
        revision.value += 1
    }

    /** Opens the system home-app picker / role request so the user can finish opt-in. */
    fun openHomeSettings() {
        val launched = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null &&
                roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                runCatching {
                    context.startActivity(
                        roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }.isSuccess
            } else {
                false
            }
        } else {
            false
        }
        if (launched) return

        val settingsLaunched = runCatching {
            context.startActivity(
                Intent(Settings.ACTION_HOME_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
        if (settingsLaunched) return

        // Last resort: fire a HOME intent so the disambiguation sheet appears when multiple
        // candidates are registered.
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun read(): HomeRoleState {
        val component = resolveAliasComponent()
        return HomeRoleState(
            isHomeCandidate = isComponentEffectivelyEnabled(component),
            isDefaultHome = resolveCurrentHomePackage() == context.packageName,
        )
    }

    private fun isComponentEffectivelyEnabled(component: ComponentName): Boolean {
        return when (packageManager.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            else -> runCatching {
                packageManager
                    .getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS)
                    .enabled
            }.getOrDefault(false)
        }
    }

    /**
     * Prefer the HOME activity-alias declared by this package (works even while disabled). Fall
     * back to the known namespace-qualified alias name used in AndroidManifest.xml.
     */
    private fun resolveAliasComponent(): ComponentName {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = runCatching {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(
                homeIntent,
                PackageManager.MATCH_DISABLED_COMPONENTS,
            )
        }.getOrDefault(emptyList())
            .asSequence()
            .map { it.activityInfo }
            .filter { it.packageName == context.packageName }
            // Prefer the alias (has a targetActivity) over MainActivity if both ever match.
            .sortedByDescending { info -> !info.targetActivity.isNullOrEmpty() }
            .firstOrNull()

        if (resolved != null) {
            return ComponentName(resolved.packageName, resolved.name)
        }

        // Manifest namespace expands `.HomeAlias` → `com.arcadia.shell.HomeAlias`; package is
        // always the installed applicationId (including the debug suffix).
        return ComponentName(context.packageName, FALLBACK_ALIAS_CLASS)
    }

    private fun resolveCurrentHomePackage(): String? {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager
            .resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.packageName
    }

    private companion object {
        const val FALLBACK_ALIAS_CLASS = "com.arcadia.shell.HomeAlias"
    }
}
