package com.sengine.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldRunnerTest {
    @Test fun dynamicBodyLandsOnStaticPlatformWithoutChangingEditorScene() {
        val floor = Entity(
            "floor", "Floor", transform = Transform(y = 100f, width = 300f, height = 20f),
            physics = PhysicsBody(),
        )
        val ball = Entity(
            "ball", "Ball", transform = Transform(y = 0f, width = 20f, height = 20f),
            visual = Visual(VisualType.CIRCLE), physics = PhysicsBody(BodyType.DYNAMIC),
        )
        val source = GameScene("scene", "Test", entities = listOf(floor, ball))
        val world = WorldRunner(source)
        repeat(180) { world.advance(1f / 60f) }
        assertEquals(80f, world.scene.entities.last().transform.y, 0.01f)
        assertEquals(0f, world.scene.entities.last().physics!!.velocity.y, 0.01f)
        assertEquals(0f, source.entities.last().transform.y, 0.01f)
        assertTrue(world.tap(0f, 80f))
        assertEquals(-470f, world.scene.entities.last().physics!!.velocity.y, 0.01f)
        assertFalse(world.tap(2000f, 2000f))
    }

    @Test fun motionIsComputedFromStartingTransformNotAccumulated() {
        val entity = Entity("spinner", "Spinner", motion = Motion(MotionType.SPIN, speed = 1f))
        val world = WorldRunner(GameScene("scene", "Test", entities = listOf(entity)))
        repeat(60) { world.advance(1f / 60f) }
        assertEquals(360f, world.scene.entities.first().transform.rotation, 0.02f)
        assertEquals(0f, entity.transform.rotation, 0.01f)
    }
}
