package com.sengine.studio.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.LruCache
import android.view.MotionEvent
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.sengine.core.ColliderShape
import com.sengine.core.Entity
import com.sengine.core.GameScene
import com.sengine.studio.EditorTool
import com.sengine.core.SceneCamera
import com.sengine.core.VisualType
import com.sengine.core.hitTest
import com.sengine.core.updateEntity
import java.io.File
import kotlin.math.hypot
import kotlin.math.floor

@Composable
fun SceneCanvas(
    scene: GameScene,
    projectId: String,
    selectedId: String?,
    playing: Boolean,
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
            view.bind(scene, projectId, selectedId, playing, tool, showColliders, resolveAsset)
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

/** Native Canvas stays responsive during a drag, even before Compose's next recomposition. */
private class SceneViewport(context: Context) : View(context) {
    private var scene = GameScene("viewport", "Viewport")
    private var projectId = ""
    private var selectedId: String? = null
    private var playing = false
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

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmaps = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val density = resources.displayMetrics.density
    private var lastX = 0f
    private var lastY = 0f
    private var pinchDistance = 0f
    private var pinchX = 0f
    private var pinchY = 0f
    private var draggedId: String? = null

    init { contentDescription = "Scene viewport. Drag objects to move; drag empty space to pan; pinch to zoom." }

    fun bind(
        next: GameScene, nextProjectId: String, selection: String?, isPlaying: Boolean,
        selectedTool: EditorTool, debugColliders: Boolean,
        assetResolver: (String, String) -> File,
    ) {
        if (nextProjectId != projectId) bitmaps.evictAll()
        projectId = nextProjectId
        scene = next
        selectedId = selection
        playing = isPlaying
        tool = selectedTool
        showColliders = debugColliders
        resolveAsset = assetResolver
        invalidate()
    }

    override fun onDetachedFromWindow() {
        bitmaps.evictAll()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(scene.background)
        val scale = worldScale()
        canvas.save()
        canvas.translate(width / 2f, height / 2f)
        canvas.scale(scale, scale)
        canvas.translate(-scene.camera.x, -scene.camera.y)
        drawGrid(canvas, scale)
        drawGameFrame(canvas, scale)
        scene.entities.forEach { if (it.visible) drawEntity(canvas, it, scale) }
        canvas.restore()
    }

    private fun drawGrid(canvas: Canvas, scale: Float) {
        val camera = scene.camera
        val left = camera.x - width / (2f * scale)
        val right = camera.x + width / (2f * scale)
        val top = camera.y - height / (2f * scale)
        val bottom = camera.y + height / (2f * scale)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f / scale
        paint.color = Color.argb(23, 202, 216, 255)
        var x = floor(left / GRID) * GRID
        while (x <= right) { canvas.drawLine(x, top, x, bottom, paint); x += GRID }
        var y = floor(top / GRID) * GRID
        while (y <= bottom) { canvas.drawLine(left, y, right, y, paint); y += GRID }
        paint.color = Color.argb(65, 161, 181, 233)
        canvas.drawLine(0f, top, 0f, bottom, paint)
        canvas.drawLine(left, 0f, right, 0f, paint)
    }

    private fun drawGameFrame(canvas: Canvas, scale: Float) {
        val frame = RectF(-scene.gameWidth / 2f, -scene.gameHeight / 2f, scene.gameWidth / 2f, scene.gameHeight / 2f)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(15, 240, 244, 255)
        canvas.drawRect(frame, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f / scale
        paint.color = Color.argb(210, 240, 244, 255)
        canvas.drawRect(frame, paint)
    }

    private fun drawEntity(canvas: Canvas, entity: Entity, scale: Float) {
        val t = entity.transform
        val rect = RectF(-t.width / 2f, -t.height / 2f, t.width / 2f, t.height / 2f)
        canvas.save()
        canvas.translate(t.x, t.y)
        canvas.rotate(t.rotation)
        paint.style = Paint.Style.FILL
        paint.color = entity.visual.color
        when (entity.visual.type) {
            VisualType.BOX -> canvas.drawRoundRect(rect, 5f, 5f, paint)
            VisualType.CIRCLE -> canvas.drawOval(rect, paint)
            VisualType.TEXT -> {
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = t.height * .62f
                canvas.drawText(entity.visual.text, 0f, -(paint.ascent() + paint.descent()) / 2f, paint)
                paint.typeface = null
            }
            VisualType.IMAGE -> {
                val bitmap = entity.visual.assetId?.let(::bitmap)
                if (bitmap != null) {
                    paint.color = Color.WHITE
                    canvas.drawBitmap(bitmap, null, rect, paint)
                } else {
                    paint.color = Color.rgb(57, 68, 89)
                    canvas.drawRect(rect, paint)
                    paint.color = Color.rgb(228, 143, 160)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2f / scale
                    canvas.drawLine(rect.left, rect.top, rect.right, rect.bottom, paint)
                    canvas.drawLine(rect.right, rect.top, rect.left, rect.bottom, paint)
                }
            }
        }
        entity.physics?.let { physics ->
            if (showColliders) {
                paint.style = Paint.Style.STROKE
                paint.color = when {
                    physics.sensor -> Color.rgb(255, 144, 185)
                    physics.type == com.sengine.core.BodyType.DYNAMIC -> Color.rgb(105, 227, 184)
                    else -> Color.rgb(244, 184, 103)
                }
                paint.strokeWidth = 2f / scale
                if (physics.collider == ColliderShape.CIRCLE ||
                    (physics.collider == ColliderShape.AUTO && entity.visual.type == VisualType.CIRCLE)
                ) {
                    canvas.drawCircle(0f, 0f, minOf(t.width, t.height) / 2f, paint)
                } else canvas.drawRect(rect, paint)
            }
        }
        if (!playing && selectedId == entity.id) {
            paint.style = Paint.Style.STROKE
            paint.color = Color.rgb(154, 186, 255)
            paint.strokeWidth = 2f / scale
            val padding = 4f / scale
            canvas.drawRect(RectF(rect.left - padding, rect.top - padding, rect.right + padding, rect.bottom + padding), paint)
            paint.style = Paint.Style.FILL
            listOf(rect.left to rect.top, rect.right to rect.top, rect.left to rect.bottom, rect.right to rect.bottom).forEach { (x, y) ->
                canvas.drawCircle(x, y, 4f / scale, paint)
            }
            if (tool == EditorTool.ROTATE) {
                paint.style = Paint.Style.STROKE
                canvas.drawCircle(0f, 0f, maxOf(t.width, t.height) * .6f, paint)
            } else if (tool == EditorTool.MOVE) {
                canvas.drawLine(0f, 0f, 22f / scale, 0f, paint)
                canvas.drawLine(0f, 0f, 0f, 22f / scale, paint)
            }
        }
        canvas.restore()
    }

    private fun bitmap(assetId: String): Bitmap? {
        val key = "$projectId/$assetId"
        bitmaps.get(key)?.let { return it }
        val file = resolveAsset(projectId, assetId)
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?.also { bitmaps.put(key, it) }
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
                if (!playing && event.pointerCount >= 2) {
                    draggedId = null
                    pinchDistance = distance(event)
                    pinchX = (event.getX(0) + event.getX(1)) / 2f
                    pinchY = (event.getY(0) + event.getY(1)) / 2f
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (playing) return true
                if (event.pointerCount >= 2) {
                    val distance = distance(event)
                    val middleX = (event.getX(0) + event.getX(1)) / 2f
                    val middleY = (event.getY(0) + event.getY(1)) / 2f
                    if (pinchDistance > 0f && distance > 0f) {
                        val old = scene.camera
                        val anchorX = old.x + (pinchX - width / 2f) / worldScale()
                        val anchorY = old.y + (pinchY - height / 2f) / worldScale()
                        val zoom = (old.zoom * distance / pinchDistance).coerceIn(.2f, 4f)
                        updateCamera(SceneCamera(
                            x = anchorX - (middleX - width / 2f) / (density * zoom),
                            y = anchorY - (middleY - height / 2f) / (density * zoom),
                            zoom = zoom,
                        ))
                    }
                    pinchDistance = distance
                    pinchX = middleX
                    pinchY = middleY
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
                        } else {
                            updateCamera(scene.camera.copy(
                                x = scene.camera.x - dx / worldScale(),
                                y = scene.camera.y - dy / worldScale(),
                            ))
                        }
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
                if (!playing) onGestureEnd()
                draggedId = null
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateCamera(camera: SceneCamera) {
        scene = scene.copy(camera = camera)
        onCamera(camera)
        invalidate()
    }

    private fun screenX(x: Float): Float = scene.camera.x + (x - width / 2f) / worldScale()
    private fun screenY(y: Float): Float = scene.camera.y + (y - height / 2f) / worldScale()
    private fun worldScale(): Float = density * scene.camera.zoom
    private fun distance(event: MotionEvent): Float = hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))

    private companion object { const val GRID = 40f }
}
