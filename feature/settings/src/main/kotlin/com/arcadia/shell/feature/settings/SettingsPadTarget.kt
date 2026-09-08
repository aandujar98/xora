package com.arcadia.shell.feature.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.ArcadiaGlass
import kotlinx.coroutines.delay

internal data class SettingsPadBinding(
    val activate: () -> Unit,
    val adjust: () -> ((Int) -> Unit)?,
)

internal class SettingsPadRegistry {
    private val order = mutableStateListOf<String>()
    private val bindings = mutableMapOf<String, SettingsPadBinding>()

    fun register(id: String, binding: SettingsPadBinding) {
        if (id !in order) order.add(id)
        bindings[id] = binding
    }

    fun unregister(id: String) {
        order.remove(id)
        bindings.remove(id)
    }

    fun ids(): List<String> = order.toList()

    fun binding(id: String): SettingsPadBinding? = bindings[id]
}

internal val LocalSettingsPadFocusId = staticCompositionLocalOf<String?> { null }
internal val LocalSettingsPadRegistry = staticCompositionLocalOf<SettingsPadRegistry?> { null }

/**
 * One gamepad stop. Registers in composition order so Up/Down can walk every control, including
 * lists that appear and disappear. No-ops when Setup is not hosting pad capture (onboarding).
 */
@Composable
fun SettingsPadTarget(
    id: String,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
    onAdjust: ((Int) -> Unit)? = null,
    shape: Shape = ArcadiaGlass.ChipShape,
    listed: Boolean = true,
    showFocusBorder: Boolean = true,
    content: @Composable () -> Unit,
) {
    val focusId = LocalSettingsPadFocusId.current
    val registry = LocalSettingsPadRegistry.current
    val activateNow = rememberUpdatedState(onActivate)
    val adjustNow = rememberUpdatedState(onAdjust)
    val requester = remember { BringIntoViewRequester() }
    DisposableEffect(id, registry, listed) {
        if (registry == null || !listed) return@DisposableEffect onDispose { }
        registry.register(
            id,
            SettingsPadBinding(
                activate = { activateNow.value() },
                adjust = { adjustNow.value },
            ),
        )
        onDispose { registry.unregister(id) }
    }
    val focused = registry != null && focusId == id
    LaunchedEffect(focused, id) {
        if (!focused) return@LaunchedEffect
        delay(16)
        requester.bringIntoView()
    }
    Box(
        modifier = modifier
            .bringIntoViewRequester(requester)
            .then(
                if (focused && showFocusBorder) {
                    Modifier.border(2.dp, Color.White.copy(alpha = 0.88f), shape)
                } else {
                    Modifier
                },
            ),
    ) {
        content()
    }
}
