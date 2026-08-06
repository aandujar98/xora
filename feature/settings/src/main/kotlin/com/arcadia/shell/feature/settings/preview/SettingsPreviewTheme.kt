package com.arcadia.shell.feature.settings.preview

import androidx.compose.runtime.Composable
import com.arcadia.shell.designsystem.ArcadiaTheme

@Composable
fun SettingsPreviewTheme(content: @Composable () -> Unit) {
    ArcadiaTheme(darkTheme = true, content = content)
}
