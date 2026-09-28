package com.sengine.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptingTest {
    private class Host : ScriptHost {
        val values = mutableMapOf<String, Float>()
        val logs = mutableListOf<String>()
        var impulse = Vec2()
        var nextScene: String? = null
        override fun read(name: String): Float = values[name] ?: 0f
        override fun write(name: String, value: Float) { values[name] = value }
        override fun impulse(x: Float, y: Float) { impulse = Vec2(x, y) }
        override fun log(message: String) { logs += message }
        override fun changeScene(reference: String) { nextScene = reference }
    }

    @Test fun handlersConditionsVariablesAndExpressionsExecute() {
        val program = ScriptProgram.compile("""
            on start
              set score = 0
              log "ready"
            end
            on update
              add score = 1
              if score >= 3 and (x < 100)
                move max(20, 30) * dt, 0
              else
                rotate 90 * dt
              end
            end
            on tap
              impulse 0, -400
            end
        """.trimIndent())
        assertTrue(program.diagnostics.toString(), program.diagnostics.isEmpty())
        val host = Host().apply { values["dt"] = 0.5f }
        program.execute(ScriptEvent.START, host)
        repeat(3) { program.execute(ScriptEvent.UPDATE, host) }
        program.execute(ScriptEvent.TAP, host)
        assertEquals(3f, host.values.getValue("score"), 0.001f)
        assertEquals(15f, host.values.getValue("x"), 0.001f)
        assertEquals(90f, host.values.getValue("rotation"), 0.001f)
        assertEquals(-400f, host.impulse.y, 0.001f)
        assertEquals(listOf("ready"), host.logs)
    }

    @Test fun invalidScriptsReturnLineDiagnosticsWithoutRunning() {
        val program = ScriptProgram.compile("on update\n  fly 10\nend")
        assertFalse(program.handles(ScriptEvent.UPDATE))
        assertEquals(2, program.diagnostics.first().line)
        val oversized = ScriptProgram.compile("#".repeat(16_001))
        assertTrue(oversized.diagnostics.first().message.contains("16,000"))
    }

    @Test fun sceneCommandRequestsNamedSceneWithoutMutatingTheProject() {
        val program = ScriptProgram.compile("on tap\n  scene Level 2\nend")
        assertTrue(program.diagnostics.toString(), program.diagnostics.isEmpty())
        val host = Host()
        program.execute(ScriptEvent.TAP, host)
        assertEquals("Level 2", host.nextScene)
        val script = ScriptAsset("navigation", "Door", "on tap\n  scene Level 2\nend")
        val scene = GameScene("level-1", "Level 1", entities = listOf(Entity("door", "Door", scriptId = script.id)))
        val runtime = WorldRunner(scene, listOf(script))
        assertTrue(runtime.tap(0f, 0f))
        assertEquals("Level 2", runtime.consumeSceneRequest())
        assertEquals(null, runtime.consumeSceneRequest())
        assertEquals("Level 1", scene.name)
    }

    @Test fun scriptsMoveUnbodiedEntitiesDuringPlayWithoutMutatingTheScene() {
        val actor = Entity("actor", "Actor", scriptId = "script")
        val source = GameScene("scene", "Script test", gravity = Vec2(), entities = listOf(actor))
        val script = ScriptAsset("script", "Mover", "on update\n  move 60 * dt, 0\nend")
        val runner = WorldRunner(source, listOf(script))
        repeat(60) { runner.advance(1f / 60f) }
        assertEquals(60f, runner.scene.entities.first().transform.x, 0.02f)
        assertEquals(0f, source.entities.first().transform.x, 0f)
    }
}
