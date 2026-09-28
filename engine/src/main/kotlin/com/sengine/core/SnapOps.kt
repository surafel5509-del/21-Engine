package com.sengine.core

import kotlin.math.round

/** Symmetric, deterministic world-space grid snapping. Snap once at gesture end, not per frame. */
fun snapToStep(value: Float, step: Float): Float {
    require(value.isFinite() && step.isFinite() && step > 0f) { "Invalid snap input" }
    return round(value / step) * step
}

fun Transform.snappedPosition(step: Float): Transform = copy(
    x = snapToStep(x, step).coerceIn(-999_999f, 999_999f),
    y = snapToStep(y, step).coerceIn(-999_999f, 999_999f),
)

fun Transform.snappedRotation(step: Float = 15f): Transform = copy(rotation = snapToStep(rotation, step))

fun Transform.snappedSize(step: Float): Transform = copy(
    width = snapToStep(width, step).coerceIn(1f, 10_000f),
    height = snapToStep(height, step).coerceIn(1f, 10_000f),
)
