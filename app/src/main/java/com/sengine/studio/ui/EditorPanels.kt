package com.sengine.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sengine.core.GameScene
import com.sengine.core.VisualType
import com.sengine.studio.StudioState
import com.sengine.studio.StudioViewModel

@Composable
fun HierarchyPanel(state: StudioState, vm: StudioViewModel) {
    val scene = state.project?.activeScene() ?: return
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 13.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionLabel("Objects", "${scene.entities.size}")
                Text("Front to back · tap to inspect", style = MaterialTheme.typography.bodySmall, color = StudioColors.muted)
            }
            ToolIcon(Icons.Rounded.Add, "Add rectangle", { vm.addEntity(VisualType.BOX) }, highlighted = true)
        }
        Row(
            Modifier.fillMaxWidth().background(StudioColors.raised).clickable { vm.select(null) }
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("▾  ◈", color = StudioColors.blue)
            Spacer(Modifier.width(8.dp))
            Text("${scene.name}  /  Camera", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (scene.entities.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(22.dp)) {
                Text("A fresh canvas.", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(5.dp))
                Text("Use + to add your first object, or open Assets to import a sprite.", style = MaterialTheme.typography.bodySmall, color = StudioColors.muted)
            }
        } else {
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)) {
                items(scene.entities.asReversed(), key = { it.id }) { entity ->
                    val selected = entity.id == state.selectedId
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 2.dp)
                            .background(if (selected) StudioColors.violet.copy(alpha = .13f) else Color.Transparent, RoundedCornerShape(11.dp))
                            .clickable { vm.select(entity.id) }.padding(start = 11.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(30.dp).background(Color(entity.visual.color).copy(alpha = .18f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(when (entity.visual.type) { VisualType.BOX -> "▣"; VisualType.CIRCLE -> "●"; VisualType.TEXT -> "T"; VisualType.IMAGE -> "▧" }, color = Color(entity.visual.color), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entity.name.ifBlank { "Unnamed object" }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (entity.visible) StudioColors.text else StudioColors.muted)
                            Text(entity.visual.type.name.lowercase().replaceFirstChar { it.uppercase() } +
                                if (entity.scriptId != null) "  •  {} Script" else "",
                                style = MaterialTheme.typography.bodySmall, color = StudioColors.muted, fontSize = 10.sp)
                        }
                        Box(Modifier.size(28.dp).clickable { vm.editEntity(entity.id) { it.copy(visible = !it.visible) } }, contentAlignment = Alignment.Center) {
                            Icon(if (entity.visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                if (entity.visible) "Hide ${entity.name}" else "Show ${entity.name}",
                                tint = StudioColors.muted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AssetsPanel(state: StudioState, vm: StudioViewModel, onPickImage: () -> Unit) {
    val project = state.project ?: return
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
        SectionLabel("Asset library", "${project.assets.size} images")
        Spacer(Modifier.height(10.dp))
        Button(onClick = onPickImage, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp)) {
            Icon(Icons.Rounded.Image, null, Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text("Import image")
        }
        Spacer(Modifier.height(12.dp))
        if (project.assets.isEmpty()) {
            Text("Import a PNG, JPEG or WebP from your device. It will be copied into this project and can be reused in any scene.", style = MaterialTheme.typography.bodySmall, color = StudioColors.muted)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(project.assets, key = { it.id }) { asset ->
                    Row(
                        Modifier.fillMaxWidth().background(StudioColors.raised, RoundedCornerShape(11.dp)).padding(start = 11.dp, end = 3.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Image, null, tint = StudioColors.mint, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(asset.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        ToolIcon(Icons.Rounded.Add, "Add ${asset.name} to scene", { vm.addEntity(VisualType.IMAGE, asset.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun ScenesPanel(state: StudioState, vm: StudioViewModel) {
    val project = state.project ?: return
    val scene = project.activeScene()
    var renameProject by remember { mutableStateOf(false) }
    var renameScene by remember { mutableStateOf(false) }
    var deleteScene by remember { mutableStateOf<GameScene?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        SectionLabel("Project", "LOCAL WORKSPACE")
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth().background(StudioColors.raised, RoundedCornerShape(12.dp)).clickable { renameProject = true }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(project.name, style = MaterialTheme.typography.titleMedium)
                Text("Tap to rename", style = MaterialTheme.typography.bodySmall, color = StudioColors.muted)
            }
            Text("EDIT", style = MaterialTheme.typography.labelMedium, color = StudioColors.violet)
        }
        Spacer(Modifier.height(21.dp))
        SectionLabel("Scenes", "${project.scenes.size} / 32")
        Spacer(Modifier.height(9.dp))
        project.scenes.forEach { item ->
            val active = item.id == project.activeSceneId
            Row(
                Modifier.fillMaxWidth().padding(bottom = 7.dp)
                    .background(if (active) StudioColors.violet.copy(alpha = .14f) else StudioColors.raised, RoundedCornerShape(11.dp))
                    .border(1.dp, if (active) StudioColors.violet.copy(alpha = .38f) else StudioColors.border, RoundedCornerShape(11.dp))
                    .clickable { vm.selectScene(item.id) }.padding(start = 13.dp, end = 2.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (active) "●" else "○", color = if (active) StudioColors.violet else StudioColors.muted)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${item.entities.size} objects", style = MaterialTheme.typography.bodySmall, color = StudioColors.muted)
                }
                if (active) TextButton(onClick = { renameScene = true }) { Text("Edit", fontSize = 11.sp) }
                if (project.scenes.size > 1) ToolIcon(Icons.Rounded.DeleteOutline, "Delete ${item.name}", { deleteScene = item })
            }
        }
        TextButton(onClick = vm::addScene) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Text("Add scene")
        }
        Spacer(Modifier.height(14.dp))
        SectionLabel("Scene environment")
        Spacer(Modifier.height(12.dp))
        Text("Background", style = MaterialTheme.typography.bodySmall, color = StudioColors.muted)
        Spacer(Modifier.height(8.dp))
        PaletteRow(scene.background, onChoose = vm::setBackground, colors = BACKGROUNDS)
        Spacer(Modifier.height(14.dp))
        Text("Gravity (world units / s²)", style = MaterialTheme.typography.bodySmall, color = StudioColors.muted)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("X", scene.gravity.x, { vm.setGravity(it, scene.gravity.y) }, Modifier.weight(1f), -2000f..2000f)
            NumberField("Y", scene.gravity.y, { vm.setGravity(scene.gravity.x, it) }, Modifier.weight(1f), -2000f..2000f)
        }
        Spacer(Modifier.height(15.dp))
        SectionLabel("Game view", "WORLD UNITS")
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Width", scene.gameWidth, { vm.setGameSize(it, scene.gameHeight) }, Modifier.weight(1f), 100f..4000f)
            NumberField("Height", scene.gameHeight, { vm.setGameSize(scene.gameWidth, it) }, Modifier.weight(1f), 100f..4000f)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { vm.setGameSize(640f, 360f) }) { Text("Landscape 16:9") }
            TextButton(onClick = { vm.setGameSize(360f, 640f) }) { Text("Portrait 9:16") }
        }
        Spacer(Modifier.height(25.dp))
    }
    if (renameProject) RenameDialog("Rename project", project.name, { vm.renameProject(it) }) { renameProject = false }
    if (renameScene) RenameDialog("Rename scene", scene.name, { vm.renameScene(it) }) { renameScene = false }
    deleteScene?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteScene = null },
            title = { Text("Delete ${target.name}?") },
            text = { Text("Objects in this scene will be removed. You can undo this while the project stays open.") },
            confirmButton = { TextButton(onClick = { vm.deleteScene(target.id); deleteScene = null }) { Text("Delete", color = StudioColors.danger) } },
            dismissButton = { TextButton(onClick = { deleteScene = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RenameDialog(title: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it.take(100) }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { onSave(value); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val BACKGROUNDS = listOf(
    0xFF151E30.toInt(), 0xFF101522.toInt(), 0xFF202641.toInt(),
    0xFF123836.toInt(), 0xFF362436.toInt(), 0xFF121212.toInt(),
)
