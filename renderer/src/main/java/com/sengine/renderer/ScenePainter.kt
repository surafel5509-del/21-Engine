package com.sengine.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.LruCache
import com.sengine.core.BodyType
import com.sengine.core.ColliderShape
import com.sengine.core.Entity
import com.sengine.core.GameScene
import com.sengine.core.VisualType
import java.io.InputStream
import kotlin.math.floor
import kotlin.math.min

/** The same Canvas2D renderer is used by the editor viewport and built Android games. */
class ScenePainter(context: Context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmaps = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    val density: Float = context.resources.displayMetrics.density

    fun clear() { bitmaps.evictAll() }

    fun draw(
        canvas: Canvas,
        scene: GameScene,
        width: Int,
        height: Int,
        pixelsPerWorldUnit: Float,
        imageSource: (String) -> InputStream?,
        selectedId: String? = null,
        showGrid: Boolean = false,
        showFrame: Boolean = false,
        showColliders: Boolean = false,
        clipToFrame: Boolean = false,
    ) {
        val scale = pixelsPerWorldUnit.coerceAtLeast(0.01f)
        canvas.drawColor(if (clipToFrame) Color.BLACK else scene.background)
        canvas.save()
        canvas.translate(width / 2f, height / 2f)
        canvas.scale(scale, scale)
        canvas.translate(-scene.camera.x, -scene.camera.y)
        // The game frame follows the camera; it must not slide across the display when the camera pans.
        val bounds = RectF(
            scene.camera.x - scene.gameWidth / 2f, scene.camera.y - scene.gameHeight / 2f,
            scene.camera.x + scene.gameWidth / 2f, scene.camera.y + scene.gameHeight / 2f,
        )
        if (clipToFrame) {
            paint.style = Paint.Style.FILL
            paint.color = scene.background
            canvas.drawRect(bounds, paint)
            canvas.clipRect(bounds)
        }
        if (showGrid) grid(canvas, scene, width, height, scale)
        if (showFrame) {
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(14, 240, 244, 255)
            canvas.drawRect(bounds, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f / scale
            paint.color = Color.argb(215, 240, 244, 255)
            canvas.drawRect(bounds, paint)
        }
        scene.entities.forEach { if (it.visible) entity(canvas, it, scale, selectedId, showColliders, imageSource) }
        canvas.restore()
    }

    private fun grid(canvas: Canvas, scene: GameScene, width: Int, height: Int, scale: Float) {
        val camera = scene.camera
        val left = camera.x - width / (2f * scale)
        val right = camera.x + width / (2f * scale)
        val top = camera.y - height / (2f * scale)
        val bottom = camera.y + height / (2f * scale)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f / scale
        paint.color = Color.argb(24, 204, 216, 235)
        var x = floor(left / GRID) * GRID
        while (x <= right) { canvas.drawLine(x, top, x, bottom, paint); x += GRID }
        var y = floor(top / GRID) * GRID
        while (y <= bottom) { canvas.drawLine(left, y, right, y, paint); y += GRID }
        paint.color = Color.argb(80, 148, 177, 245)
        canvas.drawLine(0f, top, 0f, bottom, paint)
        paint.color = Color.argb(80, 244, 148, 148)
        canvas.drawLine(left, 0f, right, 0f, paint)
    }

    private fun entity(
        canvas: Canvas, entity: Entity, scale: Float, selection: String?, colliders: Boolean,
        imageSource: (String) -> InputStream?,
    ) {
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
                val image = entity.visual.assetId?.let { bitmap(it, imageSource) }
                if (image == null) {
                    paint.color = Color.rgb(59, 65, 75)
                    canvas.drawRect(rect, paint)
                    paint.style = Paint.Style.STROKE
                    paint.color = Color.rgb(236, 140, 150)
                    paint.strokeWidth = 2f / scale
                    canvas.drawLine(rect.left, rect.top, rect.right, rect.bottom, paint)
                } else {
                    paint.color = Color.WHITE
                    canvas.drawBitmap(image, null, rect, paint)
                }
            }
        }
        entity.physics?.let { body ->
            if (colliders) {
                paint.style = Paint.Style.STROKE
                paint.color = when {
                    body.sensor -> Color.rgb(255, 144, 185)
                    body.type == BodyType.DYNAMIC -> Color.rgb(105, 227, 184)
                    else -> Color.rgb(244, 184, 103)
                }
                paint.strokeWidth = 2f / scale
                if (body.collider == ColliderShape.CIRCLE ||
                    (body.collider == ColliderShape.AUTO && entity.visual.type == VisualType.CIRCLE)
                ) canvas.drawCircle(0f, 0f, min(t.width, t.height) / 2f, paint)
                else canvas.drawRect(rect, paint)
            }
        }
        if (selection == entity.id) {
            paint.style = Paint.Style.STROKE
            paint.color = Color.rgb(152, 184, 255)
            paint.strokeWidth = 2f / scale
            val padding = 4f / scale
            canvas.drawRect(RectF(rect.left - padding, rect.top - padding, rect.right + padding, rect.bottom + padding), paint)
            paint.style = Paint.Style.FILL
            listOf(rect.left to rect.top, rect.right to rect.top, rect.left to rect.bottom, rect.right to rect.bottom).forEach { (x, y) ->
                canvas.drawCircle(x, y, 4f / scale, paint)
            }
        }
        canvas.restore()
    }

    private fun bitmap(id: String, source: (String) -> InputStream?): Bitmap? {
        bitmaps.get(id)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        source(id)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
        return source(id)?.use { input ->
            BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }?.also { bitmaps.put(id, it) }
    }

    private companion object { const val GRID = 40f }
}
