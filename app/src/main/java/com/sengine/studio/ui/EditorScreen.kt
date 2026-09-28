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
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sengine.core.GameScene
import com.sengine.core.VisualType
import com.sengine.studio.EditorTool
import com.sengine.studio.SaveStatus
import com.sengine.studio.StudioState
import com.sengine.studio.StudioViewModel

/** Landscape workspace: hierarchy | scene viewport | inspector, with a collapsible bottom dock. */
@Composable
fun EditorScreen(
    state: StudioState, vm: StudioViewModel, onPickImage: () -> Unit,
    onExportPackage: (String) -> Unit, onExportWeb: (String) -> Unit,
) {
    val project = state.project ?: return
    val scene = state.playScene ?: project.activeScene()
    var dockExpanded by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize().background(StudioColors.background)) {
        val leftWidth = (maxWidth * .21f).coerceIn(150.dp, 252.dp)
        val rightWidth = (maxWidth * .27f).coerceIn(205.dp, 360.dp)
        val dockHeight = if (dockExpanded) { if (maxHeight < 490.dp) 132.dp else 196.dp } else 32.dp
        Column(Modifier.fillMaxSize()) {
            EditorTopBar(state, vm, onExportPackage, onExportWeb)
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.width(leftWidth).fillMaxHeight().background(StudioColors.surface)) {
                    HierarchyPanel(state, vm)
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(StudioColors.border))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    SceneToolbar(state, vm, onPickImage)
                    ViewportFrame(scene, state, vm, Modifier.weight(1f).fillMaxWidth())
                    ViewportStatus(scene, state)
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(StudioColors.border))
                Column(Modifier.width(rightWidth).fillMaxHeight().background(StudioColors.surface)) {
                    Row(
                        Modifier.fillMaxWidth().height(35.dp).background(StudioColors.raised).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (state.playing) "RUNTIME INSPECTOR" else if (state.selectedId == null) "SCENE INSPECTOR" else "INSPECTOR",
                            style = MaterialTheme.typography.labelMedium, color = StudioColors.muted,
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            state.playing -> RuntimeInspector(state, vm)
                            state.selectedId != null -> InspectorPanel(state, vm)
                            else -> ScenesPanel(state, vm)
                        }
                    }
                }
            }
            DockPanel(state, vm, dockExpanded, { dockExpanded = !dockExpanded }, onPickImage, onExportPackage, onExportWeb, Modifier.height(dockHeight))
        }
    }
    state.editingScriptId?.let { id ->
        val script = project.scripts.firstOrNull { it.id == id }
        if (script != null) ScriptEditorDialog(script, vm)
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
        Modifier.fillMaxWidth().height(49.dp).background(StudioColors.surface).padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolIcon(Icons.Rounded.ArrowBack, "Projects", vm::closeProject)
        Column(Modifier.width(149.dp).padding(start = 5.dp)) {
            Text("${project.name} / ${project.activeScene().name}", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val status = when (state.saveStatus) {
                SaveStatus.SAVED -> "SAVED"
                SaveStatus.SAVING -> "SAVING…"
                SaveStatus.ERROR -> "SAVE FAILED"
            }
            Text(status, style = MaterialTheme.typography.labelMedium, color = if (state.saveStatus == SaveStatus.ERROR) StudioColors.danger else StudioColors.muted)
        }
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            ToolIcon(Icons.Rounded.PanTool, "Pan canvas", { vm.setTool(EditorTool.PAN) }, highlighted = state.tool == EditorTool.PAN && !state.playing, enabled = !state.playing)
            ToolIcon(Icons.Rounded.OpenWith, "Move object", { vm.setTool(EditorTool.MOVE) }, highlighted = state.tool == EditorTool.MOVE && !state.playing, enabled = !state.playing)
            ToolIcon(Icons.Rounded.RotateRight, "Rotate object", { vm.setTool(EditorTool.ROTATE) }, highlighted = state.tool == EditorTool.ROTATE && !state.playing, enabled = !state.playing)
            ToolIcon(Icons.Rounded.AspectRatio, "Scale object", { vm.setTool(EditorTool.SCALE) }, highlighted = state.tool == EditorTool.SCALE && !state.playing, enabled = !state.playing)
            Box(Modifier.width(1.dp).height(26.dp).background(StudioColors.border))
            Badge("2D", StudioColors.blue)
            ToolIcon(Icons.Rounded.BugReport, "Show collision shapes", vm::toggleColliders, highlighted = state.showColliders)
            ToolIcon(Icons.Rounded.Undo, "Undo", vm::undo, enabled = !state.playing && state.canUndo)
            ToolIcon(Icons.Rounded.Redo, "Redo", vm::redo, enabled = !state.playing && state.canRedo)
            if (!state.playing) {
                ToolIcon(Icons.Rounded.Save, "Save project", vm::saveNow)
                Box {
                    ToolIcon(Icons.Rounded.FileDownload, "Export or build", { exportMenu = true })
                    DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                        DropdownMenuItem(text = { Text("Project package (.sengine)") }, onClick = {
                            exportMenu = false; onExportPackage(project.name)
                        })
                        DropdownMenuItem(text = { Text("Playable web game (.zip)") }, onClick = {
                            exportMenu = false; onExportWeb(project.name)
                        })
                    }
                }
            }
        }
        ToolIcon(Icons.Rounded.PlayArrow, "Play", vm::play, enabled = !state.playing, highlighted = state.playing)
        ToolIcon(if (state.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (state.paused) "Resume" else "Pause", vm::togglePause, enabled = state.playing)
        ToolIcon(Icons.Rounded.SkipNext, "Step one physics frame", vm::stepFrame, enabled = state.playing && state.paused)
        ToolIcon(Icons.Rounded.Stop, "Stop", vm::stop, enabled = state.playing)
    }
}

@Composable
private fun SceneToolbar(state: StudioState, vm: StudioViewModel, onPickImage: () -> Unit) {
    val scene = state.project?.activeScene() ?: return
    var addMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(36.dp).background(StudioColors.raised).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(scene.name.uppercase(), style = MaterialTheme.typography.labelMedium, color = StudioColors.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box {
            ToolIcon(Icons.Rounded.Add, "Add object", { addMenu = true }, enabled = !state.playing)
            DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                DropdownMenuItem(text = { Text("Rectangle") }, onClick = { addMenu = false; vm.addEntity(VisualType.BOX) })
                DropdownMenuItem(text = { Text("Circle") }, onClick = { addMenu = false; vm.addEntity(VisualType.CIRCLE) })
                DropdownMenuItem(text = { Text("Text") }, onClick = { addMenu = false; vm.addEntity(VisualType.TEXT) })
                DropdownMenuItem(text = { Text("Sprite from image…") }, onClick = { addMenu = false; onPickImage() })
            }
        }
        ToolIcon(Icons.Rounded.ZoomOut, "Zoom out", { vm.zoom(.8f) }, enabled = !state.playing)
        ToolIcon(Icons.Rounded.CenterFocusStrong, "Reset scene camera", vm::frameScene, enabled = !state.playing)
        ToolIcon(Icons.Rounded.ZoomIn, "Zoom in", { vm.zoom(1.25f) }, enabled = !state.playing)
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
            tool = state.tool,
            showColliders = state.showColliders,
            resolveAsset = vm::assetFile,
            onSelect = vm::select,
            onDrag = vm::dragEntity,
            onRotate = vm::rotateEntity,
            onResize = vm::resizeEntity,
            onCamera = vm::gestureCamera,
            onGestureBegin = vm::beginGesture,
            onGestureEnd = vm::endGesture,
            onPlayTap = vm::playTap,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            if (state.playing) { if (state.paused) "PAUSED  •  ${scene.name}" else "PLAY  •  ${scene.name}  •  ${state.fps} FPS" }
            else "EDIT  •  ${scene.name}  •  ${scene.entities.size} objects",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            modifier = Modifier.align(Alignment.TopStart).padding(7.dp)
                .background(StudioColors.background.copy(alpha = .88f), RoundedCornerShape(5.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun ViewportStatus(scene: GameScene, state: StudioState) {
    Row(
        Modifier.fillMaxWidth().height(22.dp).background(StudioColors.surface).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            if (state.playing) "JBOX2D • 60 Hz • ${if (state.paused) "paused" else "running"}"
            else "${state.tool.name}  •  drag empty space to pan  •  pinch to zoom",
            style = MaterialTheme.typography.labelMedium, color = StudioColors.muted,
        )
        Text("${(scene.camera.zoom * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = StudioColors.blue)
    }
}

@Composable
private fun RuntimeInspector(state: StudioState, vm: StudioViewModel) {
    Column(Modifier.fillMaxSize().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Play mode")
        Badge(if (state.paused) "Paused" else "Running", if (state.paused) StudioColors.danger else StudioColors.mint)
        Text("${state.fps} FPS · ${state.playScene?.entities?.count { it.physics != null } ?: 0} bodies", style = MaterialTheme.typography.titleMedium)
        Text("Tap game objects to send on tap events. Pause and step to inspect collision and script logs in the Console dock. Changes in play mode are discarded on Stop.", style = MaterialTheme.typography.bodySmall, color = StudioColors.muted)
        androidx.compose.material3.TextButton(onClick = vm::toggleColliders) {
            Text(if (state.showColliders) "Hide collider outlines" else "Show collider outlines")
        }
    }
}
