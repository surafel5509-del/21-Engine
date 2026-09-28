package com.sengine.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.sengine.core.PrefabAsset
import com.sengine.core.VisualType
import com.sengine.studio.StudioState
import com.sengine.studio.StudioViewModel

private enum class ProjectKind(val title: String, val symbol: String, val tint: Color) {
    SCENE("Scenes", "◈", StudioColors.blue),
    PREFAB("Prefabs", "⬡", StudioColors.violet),
    TEXTURE("Textures", "▧", StudioColors.mint),
    SCRIPT("Scripts", "{ }", StudioColors.blue),
}

private data class ProjectEntry(
    val id: String, val title: String, val folder: String, val kind: ProjectKind, val detail: String,
)

/** Unity-style project window. The folder tree is built from persisted project metadata. */
@Composable
fun ProjectBrowser(state: StudioState, vm: StudioViewModel, onPickImage: () -> Unit) {
    val project = state.project ?: return
    val entries = buildList {
        project.scenes.forEach { add(ProjectEntry(it.id, it.name, "Scenes", ProjectKind.SCENE, "${it.entities.size} objects")) }
        project.prefabs.forEach { add(ProjectEntry(it.id, it.name, it.folder, ProjectKind.PREFAB, "Reusable object")) }
        project.assets.forEach { add(ProjectEntry(it.id, it.name, it.folder, ProjectKind.TEXTURE, "Texture")) }
        project.scripts.forEach { add(ProjectEntry(it.id, it.name, it.folder, ProjectKind.SCRIPT, "${it.source.lines().size} lines")) }
    }
    var kind by rememberSaveable(project.id) { mutableStateOf<ProjectKind?>(null) }
    var folder by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var query by rememberSaveable(project.id) { mutableStateOf("") }
    var editingPrefab by remember { mutableStateOf<PrefabAsset?>(null) }
    var deletingPrefab by remember { mutableStateOf<PrefabAsset?>(null) }
    val visible = entries.filter { entry ->
        (kind == null || entry.kind == kind) &&
            (folder == null || entry.folder == folder || entry.folder.startsWith("$folder/")) &&
            (query.isBlank() || entry.title.contains(query, ignoreCase = true) || entry.folder.contains(query, ignoreCase = true))
    }
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(134.dp).fillMaxHeight().background(StudioColors.surface).verticalScroll(rememberScrollState()).padding(5.dp)) {
            BrowserFolder("◫  Assets", kind == null, 0) { kind = null; folder = null }
            ProjectKind.entries.forEach { section ->
                BrowserFolder("${section.symbol}  ${section.title}", kind == section && folder == null, 1) {
                    kind = section; folder = null
                }
                if (kind == section) {
                    entries.filter { it.kind == section }.map { it.folder }.distinct().sorted()
                        .filter { it != section.title }.forEach { path ->
                            BrowserFolder("⌁  $path", folder == path, 2) { folder = path }
                        }
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(StudioColors.border))
        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 8.dp)) {
            Row(Modifier.fillMaxWidth().height(37.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${kind?.title ?: "Assets"}${folder?.let { " / $it" } ?: ""}  (${visible.size})",
                    style = MaterialTheme.typography.labelMedium, color = StudioColors.text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("+ Image", style = MaterialTheme.typography.labelMedium, color = StudioColors.mint,
                    modifier = Modifier.clickable(enabled = !state.playing, onClick = onPickImage).padding(horizontal = 7.dp))
                Text("+ Script", style = MaterialTheme.typography.labelMedium, color = StudioColors.blue,
                    modifier = Modifier.clickable(enabled = !state.playing, onClick = vm::createScript).padding(horizontal = 7.dp))
                Text("+ Prefab", style = MaterialTheme.typography.labelMedium, color = StudioColors.violet,
                    modifier = Modifier.clickable(enabled = !state.playing && state.selectedId != null) {
                        state.selectedId?.let(vm::createPrefab)
                    }.padding(horizontal = 7.dp))
            }
            OutlinedTextField(query, { query = it.take(80) }, label = { Text("Search project") },
                singleLine = true, modifier = Modifier.fillMaxWidth().height(52.dp).padding(end = 8.dp))
            if (visible.isEmpty()) {
                Text("No matching files. Add a scene, import a texture, write a script or save an object as a prefab.",
                    style = MaterialTheme.typography.bodySmall, color = StudioColors.muted,
                    modifier = Modifier.padding(9.dp))
            } else {
                LazyRow(Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(visible, key = { "${it.kind.name}-${it.id}" }) { entry ->
                        val active = entry.kind == ProjectKind.SCENE && entry.id == project.activeSceneId
                        Column(
                            Modifier.width(116.dp).height(88.dp)
                                .background(if (active) StudioColors.violet.copy(alpha = .16f) else StudioColors.raised, RoundedCornerShape(7.dp))
                                .border(1.dp, if (active) StudioColors.violet else StudioColors.border, RoundedCornerShape(7.dp))
                                .clickable(enabled = !state.playing) {
                                    when (entry.kind) {
                                        ProjectKind.SCENE -> vm.selectScene(entry.id)
                                        ProjectKind.PREFAB -> vm.instantiatePrefab(entry.id)
                                        ProjectKind.TEXTURE -> vm.addEntity(VisualType.IMAGE, entry.id)
                                        ProjectKind.SCRIPT -> vm.openScriptEditor(entry.id)
                                    }
                                }.padding(6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(entry.kind.symbol, color = entry.kind.tint, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.weight(1f))
                                if (entry.kind == ProjectKind.PREFAB && !state.playing) {
                                    var menu by remember(entry.id) { mutableStateOf(false) }
                                    Box {
                                        Text("⋮", color = StudioColors.muted, modifier = Modifier.clickable { menu = true }.padding(horizontal = 5.dp))
                                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                            DropdownMenuItem(text = { Text("Rename / move") }, onClick = {
                                                menu = false; editingPrefab = project.prefabs.firstOrNull { it.id == entry.id }
                                            })
                                            DropdownMenuItem(text = { Text("Delete prefab") }, onClick = {
                                                menu = false; deletingPrefab = project.prefabs.firstOrNull { it.id == entry.id }
                                            })
                                        }
                                    }
                                }
                            }
                            Text(entry.title, style = MaterialTheme.typography.bodySmall, color = StudioColors.text,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(entry.detail, style = MaterialTheme.typography.labelMedium, color = StudioColors.muted,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
    editingPrefab?.let { prefab ->
        var name by remember(prefab.id) { mutableStateOf(prefab.name) }
        var path by remember(prefab.id) { mutableStateOf(prefab.folder) }
        AlertDialog(
            onDismissRequest = { editingPrefab = null },
            title = { Text("Prefab asset") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it.take(100) }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(path, { path = it.take(100) }, label = { Text("Folder (e.g. Prefabs/Enemies)") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { vm.renamePrefab(prefab.id, name, path); editingPrefab = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editingPrefab = null }) { Text("Cancel") } },
        )
    }
    deletingPrefab?.let { prefab ->
        AlertDialog(
            onDismissRequest = { deletingPrefab = null },
            title = { Text("Delete ${prefab.name}?") },
            text = { Text("Placed instances will remain in their scenes as unlinked objects. You can undo this action.") },
            confirmButton = { TextButton(onClick = { vm.deletePrefab(prefab.id); deletingPrefab = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deletingPrefab = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BrowserFolder(label: String, selected: Boolean, level: Int, onClick: () -> Unit) {
    Text(label, style = MaterialTheme.typography.bodySmall,
        color = if (selected) StudioColors.violet else StudioColors.muted,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth()
            .background(if (selected) StudioColors.raised else Color.Transparent, RoundedCornerShape(5.dp))
            .clickable(onClick = onClick).padding(start = (6 + level * 7).dp, top = 6.dp, bottom = 6.dp, end = 4.dp))
}
