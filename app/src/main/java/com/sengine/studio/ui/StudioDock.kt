package com.sengine.studio.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sengine.core.EngineLogLevel
import com.sengine.core.ImageAsset
import com.sengine.core.VisualType
import com.sengine.studio.StudioState
import com.sengine.studio.StudioViewModel
import java.util.Locale

private enum class DockTab { CONSOLE, ASSETS, SCRIPTS, BUILD }

@Composable
fun DockPanel(
    state: StudioState, vm: StudioViewModel, expanded: Boolean, onToggle: () -> Unit,
    onPickImage: () -> Unit, onExportPackage: (String) -> Unit, onExportWeb: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(DockTab.CONSOLE) }
    Column(modifier.fillMaxWidth().background(StudioColors.surface).border(1.dp, StudioColors.border)) {
        Row(Modifier.fillMaxWidth().height(31.dp).background(StudioColors.raised).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                DockTab.entries.forEach { item ->
                    Text(
                        item.name + if (item == DockTab.CONSOLE && state.console.isNotEmpty()) " ${state.console.size}" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (tab == item) StudioColors.violet else StudioColors.muted,
                        modifier = Modifier.clickable { tab = item; if (!expanded) onToggle() }
                            .padding(horizontal = 11.dp, vertical = 7.dp),
                    )
                }
            }
            if (tab == DockTab.CONSOLE && state.console.isNotEmpty()) {
                Text("Clear", color = StudioColors.muted, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable { vm.clearConsole() }.padding(horizontal = 8.dp))
            }
            Text(if (expanded) "⌄" else "⌃", color = StudioColors.text,
                modifier = Modifier.clickable(onClick = onToggle).padding(horizontal = 9.dp))
        }
        if (expanded) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    DockTab.CONSOLE -> ConsoleContents(state, vm)
                    DockTab.ASSETS -> AssetContents(state, vm, onPickImage)
                    DockTab.SCRIPTS -> ScriptContents(state, vm)
                    DockTab.BUILD -> BuildContents(state, onExportPackage, onExportWeb)
                }
            }
        }
    }
}

@Composable
private fun ConsoleContents(state: StudioState, vm: StudioViewModel) {
    var filter by rememberSaveable { mutableStateOf("ALL") }
    val entries = state.console.filter { filter == "ALL" || it.level.name == filter }.asReversed()
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(87.dp).fillMaxHeight().border(1.dp, StudioColors.border).padding(top = 2.dp)) {
            listOf("ALL", "INFO", "WARNING", "ERROR", "PHYSICS").forEach { level ->
                Text(level, color = if (filter == level) StudioColors.violet else StudioColors.muted,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.fillMaxWidth().clickable { filter = level }.padding(horizontal = 9.dp, vertical = 4.dp))
            }
        }
        if (entries.isEmpty()) {
            Text("No ${if (filter == "ALL") "messages" else filter.lowercase() + " messages"}. Play a scene to see physics contacts and script output.",
                style = MaterialTheme.typography.bodySmall, color = StudioColors.muted, modifier = Modifier.padding(12.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 10.dp)) {
                items(entries) { log ->
                    val tone = when (log.level) {
                        EngineLogLevel.ERROR -> StudioColors.danger
                        EngineLogLevel.WARNING -> Color(0xFFFFCB73)
                        EngineLogLevel.PHYSICS -> StudioColors.mint
                        EngineLogLevel.INFO -> StudioColors.blue
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable { log.entityId?.let(vm::select) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("●", color = tone, modifier = Modifier.width(18.dp))
                        Text(String.format(Locale.US, "%.2fs", log.time), style = MaterialTheme.typography.bodySmall,
                            color = StudioColors.muted, modifier = Modifier.width(48.dp))
                        Text(log.message, style = MaterialTheme.typography.bodySmall, color = StudioColors.text,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun AssetContents(state: StudioState, vm: StudioViewModel, onPickImage: () -> Unit) {
    val project = state.project ?: return
    var edit by remember { mutableStateOf<ImageAsset?>(null) }
    var folder by rememberSaveable { mutableStateOf("ALL") }
    val folders = listOf("ALL") + project.assets.map { it.folder }.distinct().sorted()
    if (folder !in folders) folder = "ALL"
    Column(Modifier.fillMaxSize().padding(start = 10.dp)) {
        Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                folders.forEach { option ->
                    Text(option, style = MaterialTheme.typography.labelMedium,
                        color = if (folder == option) StudioColors.violet else StudioColors.muted,
                        modifier = Modifier.clickable { folder = option }.padding(horizontal = 8.dp, vertical = 6.dp))
                }
            }
            Text("+ Import image", style = MaterialTheme.typography.labelMedium, color = StudioColors.mint,
                modifier = Modifier.clickable(onClick = onPickImage).padding(horizontal = 12.dp))
        }
        val visible = project.assets.filter { folder == "ALL" || it.folder == folder }
        if (visible.isEmpty()) {
            Text("No textures in this folder. Import a PNG, JPEG or WebP through Android's file picker.",
                style = MaterialTheme.typography.bodySmall, color = StudioColors.muted, modifier = Modifier.padding(9.dp))
        } else LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 14.dp, bottom = 6.dp)) {
            items(visible, key = { it.id }) { asset ->
                Column(
                    Modifier.width(111.dp).background(StudioColors.raised, RoundedCornerShape(7.dp))
                        .clickable { vm.addEntity(VisualType.IMAGE, asset.id) }.padding(5.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AssetThumbnail(state.project.id, asset, vm)
                        Text(asset.name, style = MaterialTheme.typography.bodySmall, color = StudioColors.text,
                            modifier = Modifier.weight(1f).padding(start = 5.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text("${asset.folder}   •  Edit", style = MaterialTheme.typography.labelMedium, color = StudioColors.muted,
                        modifier = Modifier.clickable { edit = asset }.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    edit?.let { asset ->
        var name by remember(asset.id) { mutableStateOf(asset.name) }
        var path by remember(asset.id) { mutableStateOf(asset.folder) }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text("Texture asset") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it.take(160) }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(path, { path = it.take(100) }, label = { Text("Folder (e.g. Textures/UI)") }, singleLine = true)
                    TextButton(onClick = { vm.removeAsset(asset.id); edit = null }) { Text("Remove unused asset", color = StudioColors.danger) }
                }
            },
            confirmButton = { TextButton(onClick = { vm.renameAsset(asset.id, name, path); edit = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AssetThumbnail(projectId: String, asset: ImageAsset, vm: StudioViewModel) {
    val path = remember(projectId, asset.id) { vm.assetFile(projectId, asset.id).path }
    val thumbnail = remember(path) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 96 || bounds.outHeight / sample > 96) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }
    Box(Modifier.size(38.dp).clip(RoundedCornerShape(5.dp)).background(StudioColors.background), contentAlignment = Alignment.Center) {
        if (thumbnail != null) Image(thumbnail, asset.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else androidx.compose.material3.Icon(Icons.Rounded.Image, null, tint = StudioColors.muted)
    }
}

@Composable
private fun ScriptContents(state: StudioState, vm: StudioViewModel) {
    val project = state.project ?: return
    Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(8.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.width(104.dp).height(84.dp).background(StudioColors.raised, RoundedCornerShape(8.dp))
            .clickable { vm.createScript() }.padding(12.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.Icon(Icons.Rounded.Add, "Create script", tint = StudioColors.violet)
            Text("New script", style = MaterialTheme.typography.labelMedium, color = StudioColors.violet)
        }
        project.scripts.forEach { script ->
            var menu by remember(script.id) { mutableStateOf(false) }
            Column(Modifier.width(152.dp).height(84.dp).background(StudioColors.raised, RoundedCornerShape(8.dp))
                .clickable { vm.openScriptEditor(script.id) }.padding(9.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(Icons.Rounded.Code, null, Modifier.size(17.dp), tint = StudioColors.blue)
                    Text(script.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(start = 5.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box {
                        androidx.compose.material3.Icon(Icons.Rounded.MoreVert, "Script actions", Modifier.size(20.dp).clickable { menu = true }, tint = StudioColors.muted)
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Edit source") }, onClick = { menu = false; vm.openScriptEditor(script.id) })
                            DropdownMenuItem(text = { Text("Attach to selected object") }, onClick = {
                                menu = false; state.selectedId?.let { vm.attachScript(it, script.id) }
                            }, enabled = state.selectedId != null)
                            DropdownMenuItem(text = { Text("Delete unused script") }, onClick = { menu = false; vm.deleteScript(script.id) })
                        }
                    }
                }
                Text("${script.folder}  ·  ${script.source.lines().size} lines", style = MaterialTheme.typography.labelMedium,
                    color = StudioColors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun BuildContents(
    state: StudioState, onExportPackage: (String) -> Unit, onExportWeb: (String) -> Unit,
) {
    val project = state.project ?: return
    Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BuildCard("PROJECT PACKAGE", ".sengine • scenes, scripts, textures", { onExportPackage(project.name) })
        BuildCard("WEB GAME", "Playable offline Canvas game ZIP", { onExportWeb(project.name) })
        BuildCard("ANDROID APK", "Build with the :player Gradle target", { onExportPackage(project.name) })
    }
}

@Composable
private fun BuildCard(title: String, detail: String, onClick: () -> Unit) {
    Column(
        Modifier.width(205.dp).fillMaxHeight().background(StudioColors.raised, RoundedCornerShape(8.dp))
            .border(1.dp, StudioColors.border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(11.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = StudioColors.violet)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = StudioColors.muted, maxLines = 2)
        Text("EXPORT →", style = MaterialTheme.typography.labelMedium, color = StudioColors.mint)
    }
}
