package com.sengine.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sengine.core.ScriptAsset
import com.sengine.core.ScriptProgram
import com.sengine.studio.StudioViewModel

/** Edit a real event script: validate on every change; source is saved as a project asset. */
@Composable
fun ScriptEditorDialog(script: ScriptAsset, vm: StudioViewModel) {
    var source by rememberSaveable(script.id) { mutableStateOf(script.source) }
    var name by rememberSaveable(script.id) { mutableStateOf(script.name) }
    var folder by rememberSaveable(script.id) { mutableStateOf(script.folder) }
    val diagnostics = remember(source) { ScriptProgram.compile(source).diagnostics }
    Dialog(onDismissRequest = vm::closeScriptEditor, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(.94f).fillMaxHeight(.94f).border(1.dp, StudioColors.border, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp), color = StudioColors.surface,
        ) {
            Column {
                Row(Modifier.fillMaxWidth().height(47.dp).background(StudioColors.raised).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Code, null, tint = StudioColors.blue)
                    Text(" S SCRIPT  /  ${script.name}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    TextButton(onClick = vm::closeScriptEditor) { Text("Cancel") }
                    Button(onClick = { vm.saveScript(script.id, source, name, folder) }) { Text("Save script") }
                }
                Row(Modifier.weight(1f).fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.width(185.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = name, onValueChange = { name = it.take(100) }, label = { Text("Asset name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = folder, onValueChange = { folder = it.take(100) }, label = { Text("Folder") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text("EVENTS", style = MaterialTheme.typography.labelMedium, color = StudioColors.violet)
                        Text("on start · on update\non tap · on collision", style = MaterialTheme.typography.bodySmall, color = StudioColors.muted)
                        Text("COMMANDS", style = MaterialTheme.typography.labelMedium, color = StudioColors.violet)
                        Text("move x, y  ·  velocity x, y\nimpulse x, y  ·  rotate degrees\nset name = value  ·  add name = value\nif condition / else / end  ·  log value", style = MaterialTheme.typography.bodySmall, color = StudioColors.muted)
                    }
                    OutlinedTextField(
                        value = source, onValueChange = { if (it.length <= 16_000) source = it },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        label = { Text("Source  •  ${source.lines().size} lines") },
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp, color = StudioColors.text),
                    )
                }
                Column(Modifier.fillMaxWidth().background(StudioColors.raised).padding(horizontal = 14.dp, vertical = 5.dp)) {
                    Text(
                        if (diagnostics.isEmpty()) "✓ Syntax valid  •  Script executes in the sandboxed 60 Hz runtime"
                        else "${diagnostics.size} compile error${if (diagnostics.size == 1) "" else "s"}  •  Script will not run until fixed",
                        color = if (diagnostics.isEmpty()) StudioColors.mint else StudioColors.danger,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    diagnostics.take(2).forEach { issue ->
                        Text("Line ${issue.line}: ${issue.message}", color = StudioColors.danger,
                            style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
            }
        }
    }
}
