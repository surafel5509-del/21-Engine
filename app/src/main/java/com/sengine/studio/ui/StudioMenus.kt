package com.sengine.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sengine.core.VisualType
import com.sengine.studio.StudioState
import com.sengine.studio.StudioViewModel

/** Functional compact File/Edit/GameObject/Window menu bar rather than decorative labels. */
@Composable
internal fun StudioMenuBar(
    state: StudioState, vm: StudioViewModel, onPickImage: () -> Unit,
    onExportPackage: (String) -> Unit, onExportWeb: (String) -> Unit,
    onShowDock: (DockTab) -> Unit,
) {
    val project = state.project ?: return
    var open by remember { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxWidth().height(27.dp).background(StudioColors.raised).padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("S ENGINE", style = MaterialTheme.typography.labelMedium, color = StudioColors.violet,
            modifier = Modifier.width(79.dp))
        listOf("File", "Edit", "GameObject", "Window").forEach { label ->
            Box {
                Text(label, style = MaterialTheme.typography.bodySmall,
                    color = if (open == label) StudioColors.text else StudioColors.muted,
                    modifier = Modifier.clickable { open = label }.padding(horizontal = 8.dp, vertical = 5.dp))
                DropdownMenu(expanded = open == label, onDismissRequest = { open = null }) {
                    when (label) {
                        "File" -> {
                            DropdownMenuItem(text = { Text("Save project") }, onClick = { open = null; vm.saveNow() }, enabled = !state.playing)
                            DropdownMenuItem(text = { Text("Export project (.sengine)") }, onClick = {
                                open = null; onExportPackage(project.name)
                            }, enabled = !state.playing)
                            DropdownMenuItem(text = { Text("Export web game") }, onClick = {
                                open = null; onExportWeb(project.name)
                            }, enabled = !state.playing)
                            DropdownMenuItem(text = { Text("Build Android game…") }, onClick = {
                                open = null; onShowDock(DockTab.BUILD)
                            })
                            DropdownMenuItem(text = { Text("Projects dashboard") }, onClick = { open = null; vm.closeProject() })
                        }
                        "Edit" -> {
                            DropdownMenuItem(text = { Text("Undo") }, onClick = { open = null; vm.undo() }, enabled = !state.playing && state.canUndo)
                            DropdownMenuItem(text = { Text("Redo") }, onClick = { open = null; vm.redo() }, enabled = !state.playing && state.canRedo)
                            DropdownMenuItem(text = { Text("Duplicate selected object") }, onClick = {
                                open = null; state.selectedId?.let(vm::duplicateEntity)
                            }, enabled = !state.playing && state.selectedId != null)
                            DropdownMenuItem(text = { Text("${if (state.snapEnabled) "Disable" else "Enable"} grid snapping") }, onClick = {
                                open = null; vm.toggleSnap()
                            }, enabled = !state.playing)
                            DropdownMenuItem(text = { Text("Frame selected") }, onClick = { open = null; vm.focusSelected() },
                                enabled = !state.playing && state.selectedId != null)
                        }
                        "GameObject" -> {
                            DropdownMenuItem(text = { Text("Create rectangle") }, onClick = { open = null; vm.addEntity(VisualType.BOX) }, enabled = !state.playing)
                            DropdownMenuItem(text = { Text("Create circle") }, onClick = { open = null; vm.addEntity(VisualType.CIRCLE) }, enabled = !state.playing)
                            DropdownMenuItem(text = { Text("Create text") }, onClick = { open = null; vm.addEntity(VisualType.TEXT) }, enabled = !state.playing)
                            DropdownMenuItem(text = { Text("Import sprite…") }, onClick = { open = null; onPickImage() }, enabled = !state.playing)
                            DropdownMenuItem(text = { Text("Save selected as prefab") }, onClick = {
                                open = null; state.selectedId?.let(vm::createPrefab)
                            }, enabled = !state.playing && state.selectedId != null)
                        }
                        "Window" -> {
                            listOf(DockTab.PROJECT, DockTab.CONSOLE, DockTab.ASSETS, DockTab.SCRIPTS, DockTab.BUILD).forEach { tab ->
                                DropdownMenuItem(text = { Text(tab.name.lowercase().replaceFirstChar { it.uppercase() }) }, onClick = {
                                    open = null; onShowDock(tab)
                                })
                            }
                            DropdownMenuItem(text = { Text("${if (state.showColliders) "Hide" else "Show"} physics outlines") },
                                onClick = { open = null; vm.toggleColliders() })
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text("2D  •  ANDROID", style = MaterialTheme.typography.labelMedium, color = StudioColors.muted)
    }
}
