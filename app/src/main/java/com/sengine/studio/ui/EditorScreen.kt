package com.sengine.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sengine.core.GameScene
import com.sengine.core.VisualType
import com.sengine.studio.EditorPanel
import com.sengine.studio.SaveStatus
import com.sengine.studio.StudioState
import com.sengine.studio.StudioViewModel

@Composable
fun EditorScreen(
    state: StudioState, vm: StudioViewModel, onPickImage: () -> Unit,
    onExportPackage: (String) -> Unit, onExportWeb: (String) -> Unit,
) {
    val project = state.project ?: return
    val scene = state.playScene ?: project.activeScene()
    Column(Modifier.fillMaxSize().background(StudioColors.background)) {
        EditorTopBar(state, vm, onExportPackage, onExportWeb)
        BoxWithConstraints(Modifier.weight(1f)) {
            if (maxWidth >= 700.dp) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        SceneToolbar(state, vm, onPickImage)
                        ViewportFrame(scene, state, vm, Modifier.weight(1f).fillMaxWidth())
                        ViewportStatus(scene, state.playing)
                    }
                    Box(Modifier.width(1.dp).fillMaxHeight().background(StudioColors.border))
                    EditorDetails(state, vm, onPickImage, Modifier.width(350.dp).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    SceneToolbar(state, vm, onPickImage)
                    ViewportFrame(scene, state, vm, Modifier.weight(1.08f).fillMaxWidth())
                    ViewportStatus(scene, state.playing)
                    EditorDetails(state, vm, onPickImage, Modifier.weight(.92f).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun EditorTopBar(
    state: StudioState, vm: StudioViewModel,
    onExportPackage: (String) -> Unit, onExportWeb: (String) -> Unit,
) {
    val project = state.project ?: return
    var exportMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(62.dp).background(StudioColors.surface).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolIcon(Icons.Rounded.ArrowBack, "Back to projects", vm::closeProject)
        Column(Modifier.weight(1f).padding(start = 6.dp)) {
            Text(project.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val status = when (state.saveStatus) {
                SaveStatus.SAVED -> "ALL CHANGES SAVED"
                SaveStatus.SAVING -> "SAVING PROJECT…"
                SaveStatus.ERROR -> "SAVE FAILED · TAP SAVE"
            }
            Text(status, style = MaterialTheme.typography.labelMedium, color = if (state.saveStatus == SaveStatus.ERROR) StudioColors.danger else StudioColors.muted, maxLines = 1)
        }
        if (!state.playing) {
            Box {
                ToolIcon(Icons.Rounded.FileDownload, "Export project or web game", { exportMenu = true })
                DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                    DropdownMenuItem(text = { Text("Project package (.sengine)") }, onClick = {
                        exportMenu = false; onExportPackage(project.name)
                    })
                    DropdownMenuItem(text = { Text("Playable web game (.zip)") }, onClick = {
                        exportMenu = false; onExportWeb(project.name)
                    })
                }
            }
            ToolIcon(Icons.Rounded.Save, "Save project", vm::saveNow)
        }
        Spacer(Modifier.width(5.dp))
        Button(
            onClick = { if (state.playing) vm.stop() else vm.play() },
            colors = ButtonDefaults.buttonColors(containerColor = if (state.playing) StudioColors.raised else StudioColors.mint),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.height(38.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        ) {
            Icon(if (state.playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null, Modifier.width(18.dp), tint = if (state.playing) StudioColors.mint else StudioColors.background)
            Text(if (state.playing) "Stop" else "Play", color = if (state.playing) StudioColors.mint else StudioColors.background, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SceneToolbar(state: StudioState, vm: StudioViewModel, onPickImage: () -> Unit) {
    val scene = state.project?.activeScene() ?: return
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(52.dp).background(StudioColors.background)
            .horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(scene.name.uppercase(), style = MaterialTheme.typography.labelMedium, color = StudioColors.violet, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        Text("/  2D", style = MaterialTheme.typography.labelMedium, color = StudioColors.muted)
        Spacer(Modifier.width(12.dp))
        Box {
            ToolIcon(Icons.Rounded.Add, "Add object", { menu = true }, enabled = !state.playing, highlighted = true)
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Rectangle") }, onClick = { menu = false; vm.addEntity(VisualType.BOX) })
                DropdownMenuItem(text = { Text("Circle") }, onClick = { menu = false; vm.addEntity(VisualType.CIRCLE) })
                DropdownMenuItem(text = { Text("Text") }, onClick = { menu = false; vm.addEntity(VisualType.TEXT) })
                DropdownMenuItem(text = { Text("Import image…") }, onClick = { menu = false; onPickImage() })
            }
        }
        ToolIcon(Icons.Rounded.Undo, "Undo", vm::undo, enabled = !state.playing && state.canUndo)
        ToolIcon(Icons.Rounded.Redo, "Redo", vm::redo, enabled = !state.playing && state.canRedo)
        ToolIcon(Icons.Rounded.ContentCopy, "Duplicate selected object", { state.selectedId?.let(vm::duplicateEntity) }, enabled = !state.playing && state.selectedId != null)
        ToolIcon(Icons.Rounded.Image, "Import image", onPickImage, enabled = !state.playing)
    }
}

@Composable
private fun ViewportFrame(scene: GameScene, state: StudioState, vm: StudioViewModel, modifier: Modifier) {
    val project = state.project ?: return
    Box(modifier.border(1.dp, StudioColors.border)) {
        SceneCanvas(
            scene = scene, projectId = project.id,
            selectedId = if (state.playing) null else state.selectedId,
            playing = state.playing,
            resolveAsset = vm::assetFile,
            onSelect = vm::select,
            onDrag = vm::dragEntity,
            onCamera = vm::gestureCamera,
            onGestureBegin = vm::beginGesture,
            onGestureEnd = vm::endGesture,
            onPlayTap = vm::playTap,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.align(Alignment.TopStart).padding(12.dp)) {
            Badge(if (state.playing) "● Live preview" else "◇ Scene view", if (state.playing) StudioColors.mint else StudioColors.violet)
        }
        if (state.playing) {
            Text(
                "Tap a dynamic object to jump",
                color = Color.White, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                    .background(StudioColors.background.copy(alpha = .75f), RoundedCornerShape(9.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        } else {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(10.dp)
                    .background(StudioColors.surface.copy(alpha = .95f), RoundedCornerShape(12.dp)),
            ) {
                ToolIcon(Icons.Rounded.ZoomOut, "Zoom out", { vm.zoom(.8f) })
                ToolIcon(Icons.Rounded.CenterFocusStrong, "Reset camera", vm::frameScene)
                ToolIcon(Icons.Rounded.ZoomIn, "Zoom in", { vm.zoom(1.25f) })
            }
        }
    }
}

@Composable
private fun ViewportStatus(scene: GameScene, playing: Boolean) {
    Row(
        Modifier.fillMaxWidth().height(30.dp).background(StudioColors.surface).padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(if (playing) "RUNTIME  •  60 HZ PHYSICS" else "DRAG TO PAN  •  PINCH TO ZOOM", style = MaterialTheme.typography.labelMedium, color = StudioColors.muted)
        Text("${(scene.camera.zoom * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = StudioColors.violet)
    }
}

@Composable
private fun EditorDetails(state: StudioState, vm: StudioViewModel, onPickImage: () -> Unit, modifier: Modifier) {
    Column(modifier.background(StudioColors.surface)) {
        if (state.playing) {
            Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Badge("Playing", StudioColors.mint)
                Text("Your scene is alive.", style = MaterialTheme.typography.headlineSmall)
                Text("Objects with Dynamic physics respond to gravity. Tap them in the viewport to jump. Stop play mode to return to your editable scene; runtime changes are discarded.", style = MaterialTheme.typography.bodyMedium, color = StudioColors.muted)
                Spacer(Modifier.height(4.dp))
                Button(onClick = vm::stop, colors = ButtonDefaults.buttonColors(containerColor = StudioColors.raised)) {
                    Icon(Icons.Rounded.Stop, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Stop preview")
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().height(52.dp).border(1.dp, StudioColors.border), verticalAlignment = Alignment.CenterVertically) {
                PanelTab(EditorPanel.HIERARCHY, Icons.Rounded.Layers, state.panel, vm)
                PanelTab(EditorPanel.INSPECTOR, Icons.Rounded.Tune, state.panel, vm)
                PanelTab(EditorPanel.ASSETS, Icons.Rounded.Image, state.panel, vm)
                PanelTab(EditorPanel.SCENES, Icons.Rounded.Folder, state.panel, vm)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state.panel) {
                    EditorPanel.HIERARCHY -> HierarchyPanel(state, vm)
                    EditorPanel.INSPECTOR -> InspectorPanel(state, vm)
                    EditorPanel.ASSETS -> AssetsPanel(state, vm, onPickImage)
                    EditorPanel.SCENES -> ScenesPanel(state, vm)
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.PanelTab(
    panel: EditorPanel, icon: ImageVector, selected: EditorPanel, vm: StudioViewModel,
) {
    val active = panel == selected
    androidx.compose.material3.TextButton(
        onClick = { vm.showPanel(panel) },
        modifier = Modifier.weight(1f).fillMaxHeight(),
        shape = RoundedCornerShape(0.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(icon, panel.name.lowercase(), Modifier.height(18.dp), tint = if (active) StudioColors.violet else StudioColors.muted)
            Text(panel.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, color = if (active) StudioColors.violet else StudioColors.muted, fontSize = 10.sp)
        }
    }
}
