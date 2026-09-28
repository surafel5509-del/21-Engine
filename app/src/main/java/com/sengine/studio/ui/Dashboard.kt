package com.sengine.studio.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sengine.core.ProjectTemplate
import com.sengine.studio.data.ProjectSummary
import java.text.DateFormat
import java.util.Date

@Composable
fun Dashboard(
    projects: List<ProjectSummary>,
    onCreate: (String, ProjectTemplate) -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onImport: () -> Unit,
) {
    var newTemplate by remember { mutableStateOf<ProjectTemplate?>(null) }
    var deleteTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(StudioColors.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BrandMark()
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("S ENGINE", fontWeight = FontWeight.Black, letterSpacing = 1.6f.sp, fontSize = 17.sp)
                    Text("MOBILE GAME STUDIO", style = MaterialTheme.typography.labelMedium, color = StudioColors.muted)
                }
                ToolIcon(Icons.Rounded.FileUpload, "Import project package", onImport)
            }
        }
        item {
            Box(
                Modifier.fillMaxWidth().height(220.dp)
                    .background(Brush.linearGradient(listOf(Color(0xFF2F315F), Color(0xFF17263D), Color(0xFF143B3B))), RoundedCornerShape(24.dp))
                    .border(1.dp, StudioColors.violet.copy(alpha = .20f), RoundedCornerShape(24.dp)),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val step = 32.dp.toPx()
                    var x = size.width * .53f
                    while (x < size.width) {
                        drawLine(Color.White.copy(alpha = .06f), androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 1.dp.toPx())
                        x += step
                    }
                    var y = 0f
                    while (y < size.height) {
                        drawLine(Color.White.copy(alpha = .06f), androidx.compose.ui.geometry.Offset(size.width * .53f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1.dp.toPx())
                        y += step
                    }
                    drawCircle(StudioColors.mint.copy(alpha = .7f), 27.dp.toPx(), androidx.compose.ui.geometry.Offset(size.width * .84f, size.height * .53f))
                    drawCircle(Color.White.copy(alpha = .35f), 31.dp.toPx(), androidx.compose.ui.geometry.Offset(size.width * .84f, size.height * .53f), style = Stroke(1.dp.toPx()))
                    drawRect(StudioColors.violet.copy(alpha = .72f), androidx.compose.ui.geometry.Offset(size.width * .64f, size.height * .68f), androidx.compose.ui.geometry.Size(75.dp.toPx(), 8.dp.toPx()))
                }
                Column(Modifier.align(Alignment.CenterStart).padding(22.dp)) {
                    Text("YOUR POCKET STUDIO  /  01", color = StudioColors.mint, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(12.dp))
                    Text("Make worlds.\nAnywhere.", style = MaterialTheme.typography.headlineLarge, lineHeight = 34.sp, color = Color.White)
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { newTemplate = ProjectTemplate.BLANK },
                        colors = ButtonDefaults.buttonColors(containerColor = StudioColors.violet),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("New project", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item { SectionLabel("Quick start", "Pick a canvas") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { TemplateCard(ProjectTemplate.BLANK, "Blank canvas", "Start with an empty scene", StudioColors.violet) { newTemplate = it } }
                item { TemplateCard(ProjectTemplate.PLATFORMER, "Platformer", "Gravity, a player & a floor", StudioColors.mint) { newTemplate = it } }
                item { TemplateCard(ProjectTemplate.PLAYGROUND, "Playground", "Explore motion & layers", StudioColors.blue) { newTemplate = it } }
            }
        }
        item { SectionLabel("Your projects", "${projects.size} total") }
        if (projects.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().background(StudioColors.surface, RoundedCornerShape(18.dp))
                        .border(1.dp, StudioColors.border, RoundedCornerShape(18.dp)).padding(20.dp),
                ) {
                    Text("Nothing here yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(5.dp))
                    Text("Create a project above, or import a .sengine package.", style = MaterialTheme.typography.bodySmall, color = StudioColors.muted)
                }
            }
        } else {
            items(projects, key = { it.id }) { project ->
                ProjectCard(project, onOpen = { onOpen(project.id) }, onDelete = { deleteTarget = project })
            }
        }
        item {
            Text("S ENGINE  •  2D STUDIO  •  v0.1", style = MaterialTheme.typography.labelMedium, color = StudioColors.muted.copy(alpha = .65f), modifier = Modifier.padding(bottom = 10.dp))
        }
    }
    newTemplate?.let { selected ->
        var name by remember(selected) { mutableStateOf("Untitled project") }
        AlertDialog(
            onDismissRequest = { newTemplate = null },
            title = { Text("Create a project") },
            text = {
                Column {
                    Text("${selected.label} template", color = StudioColors.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it.take(48) }, label = { Text("Project name") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { onCreate(name, selected); newTemplate = null }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { newTemplate = null }) { Text("Cancel") } },
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${target.name}?") },
            text = { Text("This removes the project and its imported images from this device. Export a package first if you want a backup.") },
            confirmButton = { TextButton(onClick = { onDelete(target.id); deleteTarget = null }) { Text("Delete", color = StudioColors.danger) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Keep project") } },
        )
    }
}

private val ProjectTemplate.label: String
    get() = when (this) {
        ProjectTemplate.BLANK -> "Blank"
        ProjectTemplate.PLATFORMER -> "Platformer"
        ProjectTemplate.PLAYGROUND -> "Playground"
    }

@Composable
private fun TemplateCard(template: ProjectTemplate, title: String, detail: String, tint: Color, onClick: (ProjectTemplate) -> Unit) {
    Column(
        Modifier.width(173.dp).height(142.dp)
            .background(StudioColors.surface, RoundedCornerShape(17.dp))
            .border(1.dp, StudioColors.border, RoundedCornerShape(17.dp))
            .clickable { onClick(template) }.padding(15.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.size(32.dp).background(tint.copy(alpha = .15f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Text(when (template) { ProjectTemplate.BLANK -> "◇"; ProjectTemplate.PLATFORMER -> "▱"; ProjectTemplate.PLAYGROUND -> "◉" }, color = tint, fontSize = 20.sp)
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(3.dp))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = StudioColors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ProjectCard(project: ProjectSummary, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(StudioColors.surface, RoundedCornerShape(17.dp))
            .border(1.dp, StudioColors.border, RoundedCornerShape(17.dp))
            .clickable(onClick = onOpen).padding(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(46.dp).background(StudioColors.violet.copy(alpha = .15f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Text(project.name.firstOrNull()?.uppercase() ?: "S", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = StudioColors.violet)
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(project.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            val date = remember(project.updatedAt) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(project.updatedAt)) }
            Text("${project.sceneCount} scenes  ·  ${project.objectCount} objects  ·  $date", style = MaterialTheme.typography.bodySmall, color = StudioColors.muted, maxLines = 1)
        }
        ToolIcon(Icons.Rounded.DeleteOutline, "Delete ${project.name}", onDelete)
        Icon(Icons.Rounded.ArrowForward, null, Modifier.size(17.dp), tint = StudioColors.muted)
        Spacer(Modifier.width(8.dp))
    }
}
