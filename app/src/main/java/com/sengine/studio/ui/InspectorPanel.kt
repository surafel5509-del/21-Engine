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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.sengine.core.BodyType
import com.sengine.core.ColliderShape
import com.sengine.core.MotionType
import com.sengine.core.PhysicsBody
import com.sengine.core.VisualType
import com.sengine.studio.StudioState
import com.sengine.studio.StudioViewModel
import java.util.Locale

@Composable
fun InspectorPanel(state: StudioState, vm: StudioViewModel) {
    val project = state.project ?: return
    val entity = project.activeScene().entities.firstOrNull { it.id == state.selectedId }
    if (entity == null) {
        Column(Modifier.fillMaxSize().padding(22.dp)) {
            SectionLabel("Inspector")
            Spacer(Modifier.height(15.dp))
            Text("Nothing selected", style = MaterialTheme.typography.titleMedium)
            Text("Tap an object in the viewport or hierarchy to edit its components.", style = MaterialTheme.typography.bodySmall, color = StudioColors.muted)
        }
        return
    }
    var confirmDelete by remember(entity.id) { mutableStateOf(false) }
    var addComponent by remember(entity.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 17.dp, vertical = 15.dp)) {
        SectionLabel("Inspector", entity.visual.type.name)
        Spacer(Modifier.height(8.dp))
        Box {
            TextButton(onClick = { addComponent = true }) {
                Icon(Icons.Rounded.Add, null, Modifier.size(16.dp))
                Text("Add component")
            }
            DropdownMenu(expanded = addComponent, onDismissRequest = { addComponent = false }) {
                DropdownMenuItem(text = { Text("Physics 2D body") }, onClick = {
                    addComponent = false
                    vm.editEntity(entity.id) { it.copy(physics = it.physics ?: PhysicsBody(BodyType.DYNAMIC)) }
                }, enabled = entity.physics == null)
                DropdownMenuItem(text = { Text("Motion behavior") }, onClick = {
                    addComponent = false
                    vm.editEntity(entity.id) { it.copy(motion = it.motion.copy(type = MotionType.SPIN)) }
                }, enabled = entity.motion.type == MotionType.NONE)
                DropdownMenuItem(text = { Text("S Script asset") }, onClick = { addComponent = false; vm.createScriptForEntity(entity.id) },
                    enabled = entity.scriptId == null)
            }
        }
        Spacer(Modifier.height(7.dp))
        CommitTextField("Object name", entity.name, { name -> vm.editEntity(entity.id) { it.copy(name = name.take(100)) } })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ToolIcon(if (entity.visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, "Toggle visibility", { vm.editEntity(entity.id) { it.copy(visible = !it.visible) } })
            ToolIcon(if (entity.locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen, "Toggle lock", { vm.editEntity(entity.id) { it.copy(locked = !it.locked) } })
            ToolIcon(Icons.Rounded.ArrowUpward, "Move layer forward", { vm.reorderEntity(entity.id, 1) })
            ToolIcon(Icons.Rounded.ArrowDownward, "Move layer backward", { vm.reorderEntity(entity.id, -1) })
            Spacer(Modifier.weight(1f))
            ToolIcon(Icons.Rounded.ContentCopy, "Duplicate", { vm.duplicateEntity(entity.id) })
            ToolIcon(Icons.Rounded.DeleteOutline, "Delete", { confirmDelete = true })
        }
        HorizontalDivider(color = StudioColors.border)
        Spacer(Modifier.height(14.dp))
        SectionLabel("Transform", "WORLD UNITS")
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("X", entity.transform.x, { x -> vm.editEntity(entity.id) { it.copy(transform = it.transform.copy(x = x)) } }, Modifier.weight(1f), -999_999f..999_999f)
            NumberField("Y", entity.transform.y, { y -> vm.editEntity(entity.id) { it.copy(transform = it.transform.copy(y = y)) } }, Modifier.weight(1f), -999_999f..999_999f)
        }
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Width", entity.transform.width, { w -> vm.editEntity(entity.id) { it.copy(transform = it.transform.copy(width = w)) } }, Modifier.weight(1f), 1f..10000f)
            NumberField("Height", entity.transform.height, { h -> vm.editEntity(entity.id) { it.copy(transform = it.transform.copy(height = h)) } }, Modifier.weight(1f), 1f..10000f)
        }
        Spacer(Modifier.height(7.dp))
        NumberField("Rotation (degrees)", entity.transform.rotation, { angle -> vm.editEntity(entity.id) { it.copy(transform = it.transform.copy(rotation = angle)) } }, range = -3600f..3600f)
        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = StudioColors.border)
        Spacer(Modifier.height(14.dp))
        SectionLabel("Appearance")
        Spacer(Modifier.height(7.dp))
        if (entity.visual.type != VisualType.IMAGE) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(VisualType.BOX, VisualType.CIRCLE, VisualType.TEXT).forEach { type ->
                    FilterChip(
                        selected = entity.visual.type == type,
                        onClick = { vm.editEntity(entity.id) { it.copy(visual = it.visual.copy(type = type)) } },
                        label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
            Spacer(Modifier.height(7.dp))
            PaletteRow(entity.visual.color, onChoose = { color -> vm.editEntity(entity.id) { it.copy(visual = it.visual.copy(color = color)) } })
            Spacer(Modifier.height(8.dp))
            HexField(entity.visual.color) { color -> vm.editEntity(entity.id) { it.copy(visual = it.visual.copy(color = color)) } }
        } else {
            val asset = project.assets.firstOrNull { it.id == entity.visual.assetId }
            Text("Sprite  •  ${asset?.name ?: "Missing image"}", style = MaterialTheme.typography.bodySmall, color = StudioColors.muted)
        }
        if (entity.visual.type == VisualType.TEXT) {
            Spacer(Modifier.height(8.dp))
            CommitTextField("Text content", entity.visual.text, { content -> vm.editEntity(entity.id) { it.copy(visual = it.visual.copy(text = content.take(500))) } }, maxLength = 500)
        }
        Spacer(Modifier.height(19.dp))
        HorizontalDivider(color = StudioColors.border)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionLabel("Physics 2D")
                Text("Box2D • rotated shapes & contacts", style = MaterialTheme.typography.bodySmall, color = StudioColors.muted)
            }
            Switch(checked = entity.physics != null, onCheckedChange = { enabled ->
                vm.editEntity(entity.id) { it.copy(physics = if (enabled) PhysicsBody(BodyType.DYNAMIC) else null) }
            })
        }
        entity.physics?.let { body ->
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                BodyType.entries.forEach { type ->
                    FilterChip(selected = body.type == type, onClick = {
                        vm.editEntity(entity.id) { it.copy(physics = it.physics?.copy(type = type)) }
                    }, label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }
            Text("Collider", style = MaterialTheme.typography.bodySmall, color = StudioColors.muted)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ColliderShape.entries.forEach { shape ->
                    FilterChip(selected = body.collider == shape, onClick = {
                        vm.editEntity(entity.id) { it.copy(physics = it.physics?.copy(collider = shape)) }
                    }, label = { Text(shape.name.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Friction", body.friction, { value -> vm.editEntity(entity.id) { it.copy(physics = it.physics?.copy(friction = value)) } }, Modifier.weight(1f), 0f..1f)
                NumberField("Bounce", body.bounce, { value -> vm.editEntity(entity.id) { it.copy(physics = it.physics?.copy(bounce = value)) } }, Modifier.weight(1f), 0f..1f)
            }
            Spacer(Modifier.height(6.dp))
            if (body.type == BodyType.DYNAMIC) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Density", body.density, { value -> vm.editEntity(entity.id) { it.copy(physics = it.physics?.copy(density = value)) } }, Modifier.weight(1f), 0.01f..100f)
                    NumberField("Gravity ×", body.gravityScale, { value -> vm.editEntity(entity.id) { it.copy(physics = it.physics?.copy(gravityScale = value)) } }, Modifier.weight(1f), 0f..5f)
                }
                Spacer(Modifier.height(6.dp))
            }
            if (body.type != BodyType.STATIC) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Velocity X", body.velocity.x, { value -> vm.editEntity(entity.id) { current ->
                        current.copy(physics = current.physics?.let { it.copy(velocity = it.velocity.copy(x = value)) })
                    } }, Modifier.weight(1f), -2000f..2000f)
                    NumberField("Velocity Y", body.velocity.y, { value -> vm.editEntity(entity.id) { current ->
                        current.copy(physics = current.physics?.let { it.copy(velocity = it.velocity.copy(y = value)) })
                    } }, Modifier.weight(1f), -2000f..2000f)
                }
                Spacer(Modifier.height(6.dp))
                NumberField("Linear damping", body.linearDamping, { value -> vm.editEntity(entity.id) { it.copy(physics = it.physics?.copy(linearDamping = value)) } }, range = 0f..20f)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Sensor (trigger only)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Switch(checked = body.sensor, onCheckedChange = { checked -> vm.editEntity(entity.id) {
                    it.copy(physics = it.physics?.copy(sensor = checked))
                } })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Fixed rotation", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Switch(checked = body.fixedRotation, onCheckedChange = { checked -> vm.editEntity(entity.id) {
                    it.copy(physics = it.physics?.copy(fixedRotation = checked))
                } })
            }
        }
        Spacer(Modifier.height(17.dp))
        HorizontalDivider(color = StudioColors.border)
        Spacer(Modifier.height(14.dp))
        SectionLabel("Motion behavior")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            MotionType.entries.forEach { type ->
                FilterChip(selected = entity.motion.type == type, onClick = {
                    vm.editEntity(entity.id) { it.copy(motion = it.motion.copy(type = type)) }
                }, label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) })
            }
        }
        if (entity.motion.type != MotionType.NONE) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Speed (Hz)", entity.motion.speed, { value -> vm.editEntity(entity.id) { it.copy(motion = it.motion.copy(speed = value)) } }, Modifier.weight(1f), 0f..6f)
                if (entity.motion.type != MotionType.SPIN) {
                    NumberField("Distance", entity.motion.amplitude, { value -> vm.editEntity(entity.id) { it.copy(motion = it.motion.copy(amplitude = value)) } }, Modifier.weight(1f), 0f..2000f)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Motion is evaluated from the object's starting transform in play mode.", style = MaterialTheme.typography.bodySmall, color = StudioColors.muted)
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = StudioColors.border)
        Spacer(Modifier.height(12.dp))
        SectionLabel("S Script", "EVENT-DRIVEN")
        val attached = project.scripts.firstOrNull { it.id == entity.scriptId }
        if (attached == null) {
            Text("No script attached. Select a project script or create one in the Scripts dock.",
                style = MaterialTheme.typography.bodySmall, color = StudioColors.muted)
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(attached.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1)
                TextButton(onClick = { vm.openScriptEditor(attached.id) }) { Text("Edit") }
                TextButton(onClick = { vm.attachScript(entity.id, null) }) { Text("Detach") }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            project.scripts.filterNot { it.id == entity.scriptId }.forEach { script ->
                FilterChip(selected = false, onClick = { vm.attachScript(entity.id, script.id) }, label = { Text("+ ${script.name}") })
            }
        }
        TextButton(onClick = vm::createScript) { Text("+ New script asset") }
        Spacer(Modifier.height(15.dp))
        HorizontalDivider(color = StudioColors.border)
        Spacer(Modifier.height(12.dp))
        SectionLabel("Prefab", if (entity.prefabId == null) "UNLINKED" else "LINKED INSTANCE")
        val prefab = project.prefabs.firstOrNull { it.id == entity.prefabId }
        if (prefab != null) {
            Text(prefab.name, style = MaterialTheme.typography.titleMedium, color = StudioColors.blue)
            Text("Apply shares components across instances. Revert keeps this object's position.",
                style = MaterialTheme.typography.bodySmall, color = StudioColors.muted)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick = { vm.applySelectedPrefab(entity.id) }) { Text("Apply") }
                TextButton(onClick = { vm.revertSelectedPrefab(entity.id) }) { Text("Revert") }
                TextButton(onClick = { vm.unpackPrefab(entity.id) }) { Text("Unpack") }
            }
        } else {
            Text("Save this object's components as a reusable project asset.",
                style = MaterialTheme.typography.bodySmall, color = StudioColors.muted)
            TextButton(onClick = { vm.createPrefab(entity.id) }) { Text("+ Create prefab") }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${entity.name}?") },
            text = { Text("This object can be restored with Undo.") },
            confirmButton = { TextButton(onClick = { vm.deleteEntity(entity.id); confirmDelete = false }) { Text("Delete", color = StudioColors.danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun PaletteRow(selected: Int, onChoose: (Int) -> Unit, colors: List<Int> = OBJECT_COLORS) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        colors.forEach { value ->
            Box(
                Modifier.size(29.dp).clip(CircleShape).background(Color(value))
                    .border(if (selected == value) 3.dp else 1.dp, if (selected == value) Color.White else StudioColors.border, CircleShape)
                    .clickable { onChoose(value) },
            )
        }
    }
}

@Composable
fun NumberField(
    label: String, value: Float, onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier, range: ClosedFloatingPointRange<Float> = -999_999f..999_999f,
) {
    var input by remember(value, label) { mutableStateOf(formatNumber(value)) }
    var wasFocused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    fun commit() {
        val number = input.toFloatOrNull()
        if (number != null && number.isFinite()) {
            val safe = number.coerceIn(range.start, range.endInclusive)
            onCommit(safe)
            input = formatNumber(safe)
        } else input = formatNumber(value)
    }
    OutlinedTextField(
        value = input, onValueChange = { if (it.length <= 16) input = it },
        modifier = modifier.onFocusChanged { focus ->
            if (wasFocused && !focus.isFocused) commit()
            wasFocused = focus.isFocused
        },
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        textStyle = MaterialTheme.typography.bodyMedium,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit(); keyboard?.hide() }),
    )
}

@Composable
fun CommitTextField(label: String, value: String, onCommit: (String) -> Unit, maxLength: Int = 100) {
    var input by remember(value, label) { mutableStateOf(value) }
    var wasFocused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    fun commit() { if (input != value) onCommit(input); keyboard?.hide() }
    OutlinedTextField(
        value = input, onValueChange = { input = it.take(maxLength) },
        modifier = Modifier.fillMaxWidth().onFocusChanged { focus ->
            if (wasFocused && !focus.isFocused) commit()
            wasFocused = focus.isFocused
        },
        label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
    )
}

@Composable
private fun HexField(color: Int, onCommit: (Int) -> Unit) {
    val initial = remember(color) { String.format(Locale.US, "#%06X", color and 0xFFFFFF) }
    var input by remember(initial) { mutableStateOf(initial) }
    var wasFocused by remember { mutableStateOf(false) }
    fun commit() {
        val digits = input.trim().removePrefix("#")
        if (digits.matches(Regex("[0-9a-fA-F]{6}"))) {
            onCommit((0xFF000000L or digits.toLong(16)).toInt())
        } else input = initial
    }
    OutlinedTextField(
        value = input, onValueChange = { input = it.take(9) },
        modifier = Modifier.fillMaxWidth().onFocusChanged { focus ->
            if (wasFocused && !focus.isFocused) commit()
            wasFocused = focus.isFocused
        },
        label = { Text("Hex color") }, singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
    )
}

private fun formatNumber(value: Float): String =
    if (value == value.toInt().toFloat()) value.toInt().toString() else String.format(Locale.US, "%.2f", value)

private val OBJECT_COLORS = listOf(
    0xFF9D8CFF.toInt(), 0xFF4DD9C0.toInt(), 0xFF79AFFF.toInt(), 0xFFFFB56B.toInt(),
    0xFFFF7F9E.toInt(), 0xFFF4F2FF.toInt(), 0xFF536887.toInt(), 0xFF283C59.toInt(),
)
