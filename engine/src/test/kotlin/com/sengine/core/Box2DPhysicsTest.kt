package com.sengine.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Box2DPhysicsTest {
    @Test fun dynamicBodiesCollideAndRebound() {
        val left = Entity(
            "left", "Left", transform = Transform(x = -60f, width = 22f, height = 22f),
            visual = Visual(VisualType.CIRCLE),
            physics = PhysicsBody(BodyType.DYNAMIC, velocity = Vec2(80f, 0f), gravityScale = 0f, bounce = 1f, friction = 0f),
        )
        val right = Entity(
            "right", "Right", transform = Transform(x = 60f, width = 22f, height = 22f),
            visual = Visual(VisualType.CIRCLE),
            physics = PhysicsBody(BodyType.DYNAMIC, velocity = Vec2(-80f, 0f), gravityScale = 0f, bounce = 1f, friction = 0f),
        )
        val runner = WorldRunner(GameScene("scene", "Physics", gravity = Vec2(), entities = listOf(left, right)))
        repeat(90) { runner.advance(1f / 60f) }
        assertTrue(runner.scene.entities[0].physics!!.velocity.x < -45f)
        assertTrue(runner.scene.entities[1].physics!!.velocity.x > 45f)
        assertTrue(runner.drainLogs().any { it.level == EngineLogLevel.PHYSICS })
    }

    @Test fun sensorReportsContactButDoesNotStopTheBody() {
        val sensor = Entity(
            "zone", "Zone", transform = Transform(y = 70f, width = 300f, height = 15f),
            physics = PhysicsBody(type = BodyType.STATIC, sensor = true),
        )
        val ball = Entity(
            "ball", "Ball", transform = Transform(y = -20f, width = 22f, height = 22f),
            physics = PhysicsBody(type = BodyType.DYNAMIC),
        )
        val runner = WorldRunner(GameScene("scene", "Sensor", entities = listOf(sensor, ball)))
        repeat(100) { runner.advance(1f / 60f) }
        assertTrue(runner.scene.entities.last().transform.y > 100f)
        assertTrue(runner.drainLogs().any { it.message.contains("Zone") })
    }

    @Test fun contactScriptRunsOnceWhenObjectsFirstTouch() {
        val floor = Entity(
            "floor", "Floor", transform = Transform(y = 100f, width = 300f, height = 20f),
            physics = PhysicsBody(),
        )
        val ball = Entity(
            "ball", "Ball", transform = Transform(width = 20f, height = 20f),
            physics = PhysicsBody(BodyType.DYNAMIC), scriptId = "contact-script",
        )
        val script = ScriptAsset("contact-script", "Contact", "on collision\n log \"Hit floor\"\nend")
        val runner = WorldRunner(GameScene("scene", "Contact", entities = listOf(floor, ball)), listOf(script))
        repeat(100) { runner.advance(1f / 60f) }
        assertEquals(1, runner.drainLogs().count { it.message == "Hit floor" })
    }
}
