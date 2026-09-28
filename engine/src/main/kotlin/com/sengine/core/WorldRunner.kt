package com.sengine.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** A separate, fixed-step play world. Editing data is never mutated by simulation. */
class WorldRunner(source: GameScene) {
    private val startingTransforms = source.entities.associate { it.id to it.transform }
    private var current = source
    private var elapsed = 0f
    private var accumulator = 0f

    val scene: GameScene get() = current

    fun advance(frameSeconds: Float): GameScene {
        if (!frameSeconds.isFinite() || frameSeconds <= 0f) return current
        // Discard long pauses rather than tunnelling through platforms after a background/resume.
        accumulator += frameSeconds.coerceAtMost(0.1f)
        var steps = 0
        while (accumulator >= STEP && steps < 6) {
            tick(STEP)
            accumulator -= STEP
            steps++
        }
        if (steps == 6) accumulator = 0f
        return current
    }

    /** Touching a dynamic object gives it an upward impulse. */
    fun tap(worldX: Float, worldY: Float): Boolean {
        val hit = current.hitTest(worldX, worldY, includeLocked = true) ?: return false
        val body = hit.physics?.takeIf { it.type == BodyType.DYNAMIC } ?: return false
        current = current.updateEntity(hit.id) {
            it.copy(physics = body.copy(velocity = body.velocity.copy(y = -470f)))
        }
        return true
    }

    private fun tick(dt: Float) {
        elapsed += dt
        val colliders = current.entities.filter { it.visible && it.physics?.type == BodyType.STATIC }
        current = current.copy(entities = current.entities.map { entity ->
            val original = startingTransforms.getValue(entity.id)
            val animated = when (entity.motion.type) {
                MotionType.NONE -> entity.transform
                MotionType.SPIN -> entity.transform.copy(
                    rotation = original.rotation + elapsed * 360f * entity.motion.speed,
                )
                MotionType.FLOAT -> entity.transform.copy(
                    y = original.y + wave(entity.motion.speed) * entity.motion.amplitude,
                )
                MotionType.PATROL -> entity.transform.copy(
                    x = original.x + wave(entity.motion.speed) * entity.motion.amplitude,
                )
            }
            val body = entity.physics
            if (body?.type != BodyType.DYNAMIC || !entity.visible) {
                entity.copy(transform = animated)
            } else {
                var vx = body.velocity.x + current.gravity.x * body.gravityScale * dt
                var vy = body.velocity.y + current.gravity.y * body.gravityScale * dt
                var x = animated.x + vx * dt
                var y = animated.y

                // Axis-aligned box colliders; rotated visuals still use their unrotated bounds.
                for (wall in colliders) {
                    if (vx != 0f && overlaps(x, y, animated, wall.transform)) {
                        x = if (vx > 0f) wall.transform.x - (wall.transform.width + animated.width) / 2f
                        else wall.transform.x + (wall.transform.width + animated.width) / 2f
                        vx = -vx * body.bounce
                    }
                }
                y += vy * dt
                for (wall in colliders) {
                    if (overlaps(x, y, animated, wall.transform)) {
                        y = if (vy >= 0f) wall.transform.y - (wall.transform.height + animated.height) / 2f
                        else wall.transform.y + (wall.transform.height + animated.height) / 2f
                        vy = -vy * body.bounce
                        if (abs(vy) < 15f) vy = 0f
                    }
                }
                entity.copy(
                    transform = animated.copy(x = x, y = y),
                    physics = body.copy(velocity = Vec2(vx, vy)),
                )
            }
        })
    }

    private fun wave(speed: Float): Float = sin(elapsed.toDouble() * speed * 2.0 * PI).toFloat()

    private fun overlaps(x: Float, y: Float, a: Transform, b: Transform): Boolean =
        abs(x - b.x) * 2f < a.width + b.width && abs(y - b.y) * 2f < a.height + b.height

    private companion object {
        const val STEP = 1f / 60f
    }
}
