package com.arcadia.shell.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arcadia.shell.launcher.InstalledApp

/**
 * Searchable multi-select used by onboarding and Setup → Storage for the Android platform.
 */
@Composable
fun AndroidAppPicker(
    apps: List<InstalledApp>,
    selectedPackages: Set<String>,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggle: (packageName: String, selected: Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val needle = query.trim()
    val visible = if (needle.isEmpty()) {
        apps
    } else {
        apps.filter { app ->
            app.label.contains(needle, ignoreCase = true) ||
                app.packageName.contains(needle, ignoreCase = true)
        }
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val searchRequester = remember { FocusRequester() }
        SettingsPadTarget(
            id = "android_search",
            onActivate = { searchRequester.requestFocus() },
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().focusRequester(searchRequester),
                singleLine = true,
                label = { Text("Search apps") },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${selectedPackages.size} of ${apps.size} selected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SettingsPadTarget(
                    id = "android_select_all",
                    onActivate = { if (apps.isNotEmpty()) onSelectAll() },
                ) {
                    TextButton(onClick = onSelectAll, enabled = apps.isNotEmpty()) {
                        Text("Select all")
                    }
                }
                SettingsPadTarget(
                    id = "android_clear",
                    onActivate = { if (selectedPackages.isNotEmpty()) onClear() },
                ) {
                    TextButton(onClick = onClear, enabled = selectedPackages.isNotEmpty()) {
                        Text("Clear")
                    }
                }
            }
        }
        if (apps.isEmpty()) {
            Text(
                text = "No launchable apps were found on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                visible.forEach { app ->
                    val checked = app.packageName in selectedPackages
                    SettingsPadTarget(
                        id = "android_app_${app.packageName}",
                        onActivate = { onToggle(app.packageName, !checked) },
                    ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(app.packageName, !checked) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { onToggle(app.packageName, it) },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    }
                }
                if (visible.isEmpty()) {
                    Text(
                        text = "No apps match “$needle”.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
