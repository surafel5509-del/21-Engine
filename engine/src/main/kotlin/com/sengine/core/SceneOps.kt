package com.sengine.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Returns the visually topmost unlocked, visible entity under a world-space point. */
fun GameScene.hitTest(worldX: Float, worldY: Float, includeLocked: Boolean = false): Entity? =
    entities.asReversed().firstOrNull { entity ->
        entity.visible && (includeLocked || !entity.locked) && entity.contains(worldX, worldY)
    }

fun Entity.contains(worldX: Float, worldY: Float): Boolean {
    val radians = -transform.rotation.toDouble() * PI / 180.0
    val dx = (worldX - transform.x).toDouble()
    val dy = (worldY - transform.y).toDouble()
    val localX = dx * cos(radians) - dy * sin(radians)
    val localY = dx * sin(radians) + dy * cos(radians)
    val halfWidth = transform.width / 2.0
    val halfHeight = transform.height / 2.0
    return if (visual.type == VisualType.CIRCLE) {
        (localX / halfWidth) * (localX / halfWidth) +
            (localY / halfHeight) * (localY / halfHeight) <= 1.0
    } else {
        abs(localX) <= halfWidth && abs(localY) <= halfHeight
    }
}

fun GameScene.updateEntity(id: String, edit: (Entity) -> Entity): GameScene =
    copy(entities = entities.map { if (it.id == id) edit(it) else it })

fun GameScene.removeEntity(id: String): GameScene =
    copy(entities = entities.filterNot { it.id == id })

fun GameScene.moveLayer(id: String, direction: Int): GameScene {
    val index = entities.indexOfFirst { it.id == id }
    val destination = index + direction
    if (index == -1 || destination !in entities.indices) return this
    val reordered = entities.toMutableList()
    val entity = reordered.removeAt(index)
    reordered.add(destination, entity)
    return copy(entities = reordered)
}
