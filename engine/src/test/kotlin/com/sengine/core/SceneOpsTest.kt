package com.sengine.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SceneOpsTest {
    @Test fun hitTestingRespectsLayerVisibilityLockAndRotation() {
        val lower = Entity("lower", "Lower", transform = Transform(width = 60f, height = 60f))
        val upper = Entity("upper", "Upper", transform = Transform(width = 80f, height = 20f, rotation = 90f))
        val scene = GameScene("scene", "Test", entities = listOf(lower, upper))
        assertEquals("upper", scene.hitTest(0f, 30f)?.id)
        assertEquals("lower", scene.hitTest(20f, 0f)?.id)
        assertEquals("lower", scene.copy(entities = listOf(lower, upper.copy(locked = true))).hitTest(0f, 0f)?.id)
        assertEquals("upper", scene.hitTest(0f, 0f, includeLocked = true)?.id)
        assertNull(scene.hitTest(300f, 300f))
    }

    @Test fun circlesUseEllipseNotBoundingBox() {
        val circle = Entity("orb", "Orb", transform = Transform(width = 100f, height = 50f), visual = Visual(VisualType.CIRCLE))
        val scene = GameScene("scene", "Test", entities = listOf(circle))
        assertNull(scene.hitTest(48f, 23f))
        assertEquals("orb", scene.hitTest(0f, 23f)?.id)
    }

    @Test fun editingAndLayerOrderingAreImmutable() {
        val first = Entity("one", "First")
        val second = Entity("two", "Second")
        val scene = GameScene("scene", "Test", entities = listOf(first, second))
        assertEquals(listOf("two", "one"), scene.moveLayer("one", 1).entities.map { it.id })
        assertEquals(listOf("one", "two"), scene.entities.map { it.id })
        assertEquals("Edited", scene.updateEntity("one") { it.copy(name = "Edited") }.entities.first().name)
        assertEquals(listOf("two"), scene.removeEntity("one").entities.map { it.id })
    }
}
