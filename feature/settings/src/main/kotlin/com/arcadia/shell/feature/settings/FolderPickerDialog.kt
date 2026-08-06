package com.arcadia.shell.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.feature.settings.preview.SettingsPreviewTheme
import com.arcadia.shell.scanner.StorageVolumeRoot
import java.io.File

/**
 * A minimal directory browser for choosing a library root.
 *
 * The system document picker cannot be used here: it hands back a tree uri, and a tree uri can
 * never be converted into the real filesystem path that path-based emulators require. With
 * all-files access already granted, browsing directly is the only way to produce a usable root.
 */
@Composable
fun FolderPickerDialog(
    volumes: List<StorageVolumeRoot>,
    listDirectories: (String) -> List<File>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var currentPath by remember { mutableStateOf<String?>(null) }

    val children = remember(currentPath) {
        currentPath?.let(listDirectories).orEmpty()
    }

    val glass = rememberGlassTokens(GlassTone.Surface)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = glass.tintStrong,
        shape = ArcadiaGlass.CardShape,
        titleContentColor = glass.content,
        textContentColor = glass.content,
        title = {
            Text(
                text = currentPath ?: "Choose a volume",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val path = currentPath

                if (path == null) {
                    volumes.forEach { volume ->
                        FolderRow(
                            label = volume.label,
                            detail = volume.path,
                            onClick = { currentPath = volume.path },
                        )
                    }
                } else {
                    FolderRow(
                        label = "..",
                        detail = "Go up",
                        onClick = {
                            val parent = File(path).parent
                            currentPath = if (volumes.any { it.path == path }) null else parent
                        },
                    )
                    HorizontalDivider()

                    if (children.isEmpty()) {
                        Text(
                            text = "No subfolders here. You can still use this folder.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                            items(items = children, key = { it.absolutePath }) { directory ->
                                FolderRow(
                                    label = directory.name,
                                    detail = null,
                                    onClick = { currentPath = directory.absolutePath },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { currentPath?.let(onPick) },
                enabled = currentPath != null,
            ) {
                Text(text = "Use this folder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "Cancel") }
        },
    )
}

@Composable
private fun FolderRow(
    label: String,
    detail: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        detail?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B1220)
@Composable
private fun FolderPickerDialogPreview() {
    SettingsPreviewTheme {
        FolderPickerDialog(
            volumes = listOf(
                StorageVolumeRoot(
                    label = "Internal storage",
                    path = "/storage/emulated/0",
                    isRemovable = false,
                ),
                StorageVolumeRoot(
                    label = "SD card",
                    path = "/storage/1234-5678",
                    isRemovable = true,
                ),
            ),
            listDirectories = { path ->
                listOf(
                    File(path, "ROMs"),
                    File(path, "Saves"),
                    File(path, "Screenshots"),
                )
            },
            onDismiss = {},
            onPick = {},
        )
    }
}
