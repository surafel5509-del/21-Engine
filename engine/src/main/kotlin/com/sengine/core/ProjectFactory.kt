package com.sengine.core

import java.util.UUID

object ProjectFactory {
    fun id(): String = UUID.randomUUID().toString()

    fun create(name: String, template: ProjectTemplate, now: Long = System.currentTimeMillis()): GameProject {
        val scene = when (template) {
            ProjectTemplate.BLANK -> GameScene(id(), "Scene 01")
            ProjectTemplate.PLATFORMER -> platformer()
            ProjectTemplate.PLAYGROUND -> playground()
        }
        return GameProject(
            id = id(),
            name = name.trim().take(48).ifBlank { "Untitled project" },
            scenes = listOf(scene),
            activeSceneId = scene.id,
            createdAt = now,
            updatedAt = now,
        )
    }

    fun entity(type: VisualType, scene: GameScene, assetId: String? = null): Entity {
        require(type != VisualType.IMAGE || assetId != null) { "An image needs an imported asset" }
        val label = when (type) {
            VisualType.BOX -> "Rectangle"
            VisualType.CIRCLE -> "Circle"
            VisualType.TEXT -> "Text"
            VisualType.IMAGE -> "Sprite"
        }
        val number = scene.entities.count { it.name.startsWith(label) } + 1
        return Entity(
            id = id(),
            name = "$label $number",
            transform = Transform(
                x = scene.camera.x,
                y = scene.camera.y,
                width = if (type == VisualType.TEXT) 180f else 80f,
                height = if (type == VisualType.TEXT) 36f else 80f,
            ),
            visual = Visual(
                type = type,
                color = when (type) {
                    VisualType.CIRCLE -> 0xFF4DD9C0.toInt()
                    VisualType.TEXT -> 0xFFF4F2FF.toInt()
                    else -> 0xFF9D8CFF.toInt()
                },
                assetId = assetId,
            ),
        )
    }

    private fun platformer(): GameScene {
        val label = Entity(
            id = id(), name = "Hint",
            transform = Transform(y = -108f, width = 230f, height = 28f),
            visual = Visual(VisualType.TEXT, 0xFFBDC6E2.toInt(), "TAP THE ORB TO JUMP"),
        )
        val ledge = Entity(
            id = id(), name = "Floating platform",
            transform = Transform(x = 112f, y = 8f, width = 116f, height = 16f),
            visual = Visual(VisualType.BOX, 0xFF517AAF.toInt()),
            physics = PhysicsBody(),
        )
        val ground = Entity(
            id = id(), name = "Ground",
            transform = Transform(y = 112f, width = 370f, height = 26f),
            visual = Visual(VisualType.BOX, 0xFF506685.toInt()),
            physics = PhysicsBody(),
        )
        val player = Entity(
            id = id(), name = "Player orb",
            transform = Transform(x = -92f, y = -32f, width = 46f, height = 46f),
            visual = Visual(VisualType.CIRCLE, 0xFFB8A4FF.toInt()),
            physics = PhysicsBody(type = BodyType.DYNAMIC, bounce = 0.12f),
        )
        return GameScene(id(), "Platformer", entities = listOf(label, ledge, ground, player))
    }

    private fun playground(): GameScene {
        val title = Entity(
            id = id(), name = "Title",
            transform = Transform(y = -100f, width = 250f, height = 32f),
            visual = Visual(VisualType.TEXT, 0xFFE4E9FF.toInt(), "YOUR WORLD STARTS HERE"),
        )
        val orbit = Entity(
            id = id(), name = "Spinning tile",
            transform = Transform(x = -80f, y = -8f, width = 70f, height = 70f),
            visual = Visual(VisualType.BOX, 0xFF957AF5.toInt()),
            motion = Motion(MotionType.SPIN, speed = 0.18f),
        )
        val float = Entity(
            id = id(), name = "Floating orb",
            transform = Transform(x = 84f, y = 3f, width = 66f, height = 66f),
            visual = Visual(VisualType.CIRCLE, 0xFF49CECA.toInt()),
            motion = Motion(MotionType.FLOAT, speed = 0.6f, amplitude = 24f),
        )
        val platform = Entity(
            id = id(), name = "Base",
            transform = Transform(y = 108f, width = 330f, height = 20f),
            visual = Visual(VisualType.BOX, 0xFF394C6A.toInt()),
        )
        return GameScene(id(), "Playground", entities = listOf(title, platform, orbit, float))
    }
}
