package com.sengine.studio.ui

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.sengine.core.GameScene
import com.sengine.core.SceneCamera
import com.sengine.core.hitTest
import com.sengine.core.updateEntity
import com.sengine.renderer.ScenePainter
import com.sengine.studio.EditorTool
import java.io.File
import kotlin.math.hypot
import kotlin.math.min

@Composable
fun SceneCanvas(
    scene: GameScene,
    projectId: String,
    selectedId: String?,
    playing: Boolean,
    gameView: Boolean,
    tool: EditorTool,
    showColliders: Boolean,
    resolveAsset: (String, String) -> File,
    onSelect: (String?) -> Unit,
    onDrag: (String, Float, Float) -> Unit,
    onRotate: (String, Float) -> Unit,
    onResize: (String, Float) -> Unit,
    onCamera: (SceneCamera) -> Unit,
    onGestureBegin: () -> Unit,
    onGestureEnd: () -> Unit,
    onPlayTap: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context -> SceneViewport(context) },
        update = { view ->
            view.bind(scene, projectId, selectedId, playing, gameView, tool, showColliders, resolveAsset)
            view.onSelect = onSelect
            view.onDrag = onDrag
            view.onRotate = onRotate
            view.onResize = onResize
            view.onCamera = onCamera
            view.onGestureBegin = onGestureBegin
            view.onGestureEnd = onGestureEnd
            view.onPlayTap = onPlayTap
        },
    )
}

/** Touch controller for the editor. ScenePainter also renders the standalone Android player. */
private class SceneViewport(context: Context) : View(context) {
    private val painter = ScenePainter(context)
    private var scene = GameScene("viewport", "Viewport")
    private var projectId = ""
    private var selectedId: String? = null
    private var playing = false
    private var gameView = false
    private var tool = EditorTool.MOVE
    private var showColliders = false
    private var resolveAsset: (String, String) -> File = { _, _ -> File("") }
    var onSelect: (String?) -> Unit = {}
    var onDrag: (String, Float, Float) -> Unit = { _, _, _ -> }
    var onRotate: (String, Float) -> Unit = { _, _ -> }
    var onResize: (String, Float) -> Unit = { _, _ -> }
    var onCamera: (SceneCamera) -> Unit = {}
    var onGestureBegin: () -> Unit = {}
    var onGestureEnd: () -> Unit = {}
    var onPlayTap: (Float, Float) -> Unit = { _, _ -> }
    private var lastX = 0f
    private var lastY = 0f
    private var pinchDistance = 0f
    private var pinchX = 0f
    private var pinchY = 0f
    private var draggedId: String? = null

    init { contentDescription = "Scene viewport. Drag an object to edit; drag empty space to pan; pinch to zoom." }

    fun bind(
        next: GameScene, nextProjectId: String, selection: String?, isPlaying: Boolean, asGameView: Boolean,
        selectedTool: EditorTool, debugColliders: Boolean, assetResolver: (String, String) -> File,
    ) {
        if (nextProjectId != projectId) painter.clear()
        projectId = nextProjectId
        scene = next
        selectedId = selection
        playing = isPlaying
        gameView = asGameView
        tool = selectedTool
        showColliders = debugColliders
        resolveAsset = assetResolver
        invalidate()
    }

    override fun onDetachedFromWindow() {
        painter.clear()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        painter.draw(
            canvas, scene, width, height, worldScale(),
            imageSource = { id -> resolveAsset(projectId, id).takeIf { it.isFile }?.inputStream() },
            selectedId = if (playing || gameView) null else selectedId,
            showGrid = !gameView,
            showFrame = !gameView,
            showColliders = showColliders && !gameView,
            clipToFrame = gameView,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                lastX = event.x
                lastY = event.y
                pinchDistance = 0f
                val x = screenX(event.x)
                val y = screenY(event.y)
                if (playing) {
                    onPlayTap(x, y)
                } else if (gameView) {
                    // An edit-mode Game view is a read-only preview until Play is pressed.
                } else {
                    if (tool != EditorTool.PAN) {
                        val hit = scene.hitTest(x, y, includeLocked = true)
                        draggedId = hit?.takeUnless { it.locked }?.id
                        selectedId = hit?.id
                        onSelect(hit?.id)
                    } else draggedId = null
                    onGestureBegin()
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!playing && !gameView && event.pointerCount >= 2) {
                    draggedId = null
                    pinchDistance = distance(event)
                    pinchX = (event.getX(0) + event.getX(1)) / 2f
                    pinchY = (event.getY(0) + event.getY(1)) / 2f
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (playing || gameView) return true
                if (event.pointerCount >= 2) {
                    val separation = distance(event)
                    val midX = (event.getX(0) + event.getX(1)) / 2f
                    val midY = (event.getY(0) + event.getY(1)) / 2f
                    if (pinchDistance > 0f && separation > 0f) {
                        val previous = scene.camera
                        val anchorX = previous.x + (pinchX - width / 2f) / worldScale()
                        val anchorY = previous.y + (pinchY - height / 2f) / worldScale()
                        val zoom = (previous.zoom * separation / pinchDistance).coerceIn(.2f, 4f)
                        updateCamera(SceneCamera(
                            x = anchorX - (midX - width / 2f) / (painter.density * zoom),
                            y = anchorY - (midY - height / 2f) / (painter.density * zoom),
                            zoom = zoom,
                        ))
                    }
                    pinchDistance = separation
                    pinchX = midX
                    pinchY = midY
                } else {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    if (dx != 0f || dy != 0f) {
                        val target = draggedId
                        if (target != null) {
                            val worldDx = dx / worldScale()
                            val worldDy = dy / worldScale()
                            when (tool) {
                                EditorTool.MOVE -> {
                                    scene = scene.updateEntity(target) { entity ->
                                        entity.copy(transform = entity.transform.copy(
                                            x = entity.transform.x + worldDx, y = entity.transform.y + worldDy,
                                        ))
                                    }
                                    onDrag(target, worldDx, worldDy)
                                }
                                EditorTool.ROTATE -> {
                                    val degrees = dx * .65f
                                    scene = scene.updateEntity(target) { entity ->
                                        entity.copy(transform = entity.transform.copy(rotation = entity.transform.rotation + degrees))
                                    }
                                    onRotate(target, degrees)
                                }
                                EditorTool.SCALE -> {
                                    val change = worldDx - worldDy
                                    scene = scene.updateEntity(target) { entity ->
                                        entity.copy(transform = entity.transform.copy(
                                            width = (entity.transform.width + change).coerceIn(1f, 10000f),
                                            height = (entity.transform.height + change).coerceIn(1f, 10000f),
                                        ))
                                    }
                                    onResize(target, change)
                                }
                                EditorTool.PAN -> Unit
                            }
                        } else updateCamera(scene.camera.copy(
                            x = scene.camera.x - dx / worldScale(),
                            y = scene.camera.y - dy / worldScale(),
                        ))
                        invalidate()
                    }
                    lastX = event.x
                    lastY = event.y
                }
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val remaining = if (event.actionIndex == 0) 1 else 0
                lastX = event.getX(remaining)
                lastY = event.getY(remaining)
                pinchDistance = 0f
                draggedId = null
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!playing && !gameView) onGestureEnd()
                draggedId = null
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
    private fun updateCamera(camera: SceneCamera) { scene = scene.copy(camera = camera); onCamera(camera); invalidate() }
    private fun screenX(x: Float): Float = scene.camera.x + (x - width / 2f) / worldScale()
    private fun screenY(y: Float): Float = scene.camera.y + (y - height / 2f) / worldScale()
    private fun worldScale(): Float = if (gameView) {
        (min(width.toFloat() / scene.gameWidth, height.toFloat() / scene.gameHeight) * scene.camera.zoom)
            .coerceAtLeast(.01f)
    } else painter.density * scene.camera.zoom
    private fun distance(event: MotionEvent): Float = hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
}
