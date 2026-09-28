package com.sengine.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import com.sengine.core.GameProject
import com.sengine.core.ProjectCodec
import com.sengine.core.WorldRunner
import com.sengine.renderer.ScenePainter
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.min

/** A separately installable game APK. No editor, file permission, network access, or WebView. */
class GameActivity : Activity() {
    private var game: GameView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        try {
            val project = loadProject()
            val scene = project.activeScene()
            val orientation = if (scene.gameWidth >= scene.gameHeight) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            if (requestedOrientation != orientation) requestedOrientation = orientation
            game = GameView(this, project).also { setContentView(it) }
        } catch (error: Exception) {
            Log.e("SEnginePlayer", "Cannot load game", error)
            setContentView(TextView(this).apply {
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.rgb(24, 26, 30))
                textSize = 17f
                setPadding(32, 40, 32, 40)
                text = "Game package is missing or invalid.\n\nBuild this player with tools/build_android_game.py and a .sengine project."
            })
        }
    }

    override fun onResume() { super.onResume(); game?.resume() }
    override fun onPause() { game?.pause(); super.onPause() }

    private fun loadProject(): GameProject {
        val buffer = ByteArrayOutputStream()
        assets.open("game/project.json").use { input ->
            val chunk = ByteArray(8192)
            while (true) {
                val count = input.read(chunk)
                if (count == -1) break
                if (buffer.size() + count > 2_000_000) throw IOException("Project is too large")
                buffer.write(chunk, 0, count)
            }
        }
        return ProjectCodec.decode(buffer.toString(Charsets.UTF_8.name()))
    }
}

private class GameView(context: Context, private val project: GameProject) : View(context) {
    private val painter = ScenePainter(context)
    private val hud = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12f * resources.displayMetrics.scaledDensity
    }
    private var sceneIndex = project.scenes.indexOfFirst { it.id == project.activeSceneId }.coerceAtLeast(0)
    private var runner = WorldRunner(project.scenes[sceneIndex], project.scripts)
    private var lastFrame = 0L
    private var running = false

    init { contentDescription = "${project.name} game. Tap objects to interact." }

    fun resume() { running = true; lastFrame = System.nanoTime(); postInvalidateOnAnimation() }
    fun pause() { running = false }

    override fun onDetachedFromWindow() { pause(); painter.clear(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (running) {
            val now = System.nanoTime()
            runner.advance((now - lastFrame) / 1_000_000_000f)
            lastFrame = now
        }
        runner.drainLogs().forEach { log -> Log.i("SEnginePlayer", "${log.level}: ${log.message}") }
        val scene = runner.scene
        val scale = pixelsPerWorldUnit()
        painter.draw(
            canvas, scene, width, height, scale,
            imageSource = { id -> try { context.assets.open("game/assets/$id.img") } catch (_: IOException) { null } },
            clipToFrame = true,
        )
        hud.setShadowLayer(3f, 0f, 2f, Color.BLACK)
        canvas.drawText(project.name.take(40), 12f * painter.density, 22f * painter.density, hud)
        if (project.scenes.size > 1) {
            hud.textAlign = Paint.Align.RIGHT
            canvas.drawText("SCENE ${sceneIndex + 1}/${project.scenes.size}   NEXT ▶", width - 13f * painter.density, 22f * painter.density, hud)
            hud.textAlign = Paint.Align.LEFT
        }
        if (running) postInvalidateOnAnimation()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return true
        if (project.scenes.size > 1 && event.y < 46f * painter.density && event.x > width * .64f) {
            sceneIndex = (sceneIndex + 1) % project.scenes.size
            runner = WorldRunner(project.scenes[sceneIndex], project.scripts)
            lastFrame = System.nanoTime()
        } else {
            val scene = runner.scene
            val factor = pixelsPerWorldUnit()
            runner.tap(
                scene.camera.x + (event.x - width / 2f) / factor,
                scene.camera.y + (event.y - height / 2f) / factor,
            )
        }
        invalidate()
        performClick()
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private fun pixelsPerWorldUnit(): Float {
        val scene = runner.scene
        return (min(width.toFloat() / scene.gameWidth, height.toFloat() / scene.gameHeight) * scene.camera.zoom)
            .coerceAtLeast(.01f)
    }
}
