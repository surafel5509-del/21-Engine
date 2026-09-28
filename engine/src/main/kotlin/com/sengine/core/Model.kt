package com.sengine.core

import kotlinx.serialization.Serializable

/** All coordinates and sizes are in world units; one unit is one dp at camera zoom 1. */
@Serializable
data class Vec2(val x: Float = 0f, val y: Float = 0f)

@Serializable
data class Transform(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 80f,
    val height: Float = 80f,
    val rotation: Float = 0f,
)

@Serializable
enum class VisualType { BOX, CIRCLE, TEXT, IMAGE }

@Serializable
data class Visual(
    val type: VisualType = VisualType.BOX,
    val color: Int = 0xFF9D8CFF.toInt(),
    val text: String = "Hello, world!",
    val assetId: String? = null,
)

@Serializable
enum class BodyType { STATIC, DYNAMIC, KINEMATIC }

@Serializable
enum class ColliderShape { AUTO, BOX, CIRCLE }

/** JBox2D body configuration. Velocity is measured in world units per second. */
@Serializable
data class PhysicsBody(
    val type: BodyType = BodyType.STATIC,
    val velocity: Vec2 = Vec2(),
    val gravityScale: Float = 1f,
    val bounce: Float = 0f,
    val friction: Float = 0.35f,
    val density: Float = 1f,
    val sensor: Boolean = false,
    val fixedRotation: Boolean = false,
    val linearDamping: Float = 0f,
    val collider: ColliderShape = ColliderShape.AUTO,
)

@Serializable
enum class MotionType { NONE, SPIN, FLOAT, PATROL }

@Serializable
data class Motion(
    val type: MotionType = MotionType.NONE,
    /** Cycles per second. For SPIN, one cycle is a full revolution. */
    val speed: Float = 0.6f,
    val amplitude: Float = 56f,
)

@Serializable
data class Entity(
    val id: String,
    val name: String,
    val transform: Transform = Transform(),
    val visual: Visual = Visual(),
    val physics: PhysicsBody? = null,
    val motion: Motion = Motion(),
    val visible: Boolean = true,
    val locked: Boolean = false,
    /** A sandboxed S Script asset ID, evaluated only in play mode. */
    val scriptId: String? = null,
    /** Optional source prefab; the transform remains in world coordinates. */
    val prefabId: String? = null,
)

@Serializable
data class SceneCamera(
    val x: Float = 0f,
    val y: Float = 0f,
    val zoom: Float = 1f,
)

@Serializable
data class GameScene(
    val id: String,
    val name: String,
    val background: Int = 0xFF151E30.toInt(),
    val gravity: Vec2 = Vec2(0f, 720f),
    val camera: SceneCamera = SceneCamera(),
    val gameWidth: Float = 360f,
    val gameHeight: Float = 300f,
    /** The last entity is drawn on top. */
    val entities: List<Entity> = emptyList(),
)

@Serializable
data class ImageAsset(val id: String, val name: String, val folder: String = "Textures")

/** Script sources are project assets, not arbitrary Java/Kotlin executed on the device. */
@Serializable
data class ScriptAsset(
    val id: String,
    val name: String,
    val source: String,
    val folder: String = "Scripts",
)

@Serializable
data class PrefabAsset(
    val id: String,
    val name: String,
    val folder: String = "Prefabs",
    /** An unlinked single-object template at the origin. */
    val template: Entity,
)

@Serializable
data class GameProject(
    val formatVersion: Int = 1,
    val id: String,
    val name: String,
    val scenes: List<GameScene>,
    val activeSceneId: String,
    val assets: List<ImageAsset> = emptyList(),
    val scripts: List<ScriptAsset> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val prefabs: List<PrefabAsset> = emptyList(),
) {
    fun activeScene(): GameScene = scenes.first { it.id == activeSceneId }

    fun withScene(scene: GameScene): GameProject = copy(
        scenes = scenes.map { if (it.id == scene.id) scene else it },
    )
}

enum class ProjectTemplate { BLANK, PLATFORMER, PLAYGROUND }
