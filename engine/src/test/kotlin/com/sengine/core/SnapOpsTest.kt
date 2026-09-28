package com.sengine.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SnapOpsTest {
    @Test fun positionRotationAndDimensionsSnapWithoutMovingUnrelatedComponents() {
        val original = Transform(x = -27f, y = 34f, width = 52f, height = 76f, rotation = 28f)
        assertEquals(Transform(-20f, 40f, 52f, 76f, 28f), original.snappedPosition(20f))
        assertEquals(30f, original.snappedRotation().rotation, 0f)
        assertEquals(60f, original.snappedSize(20f).width, 0f)
        assertEquals(80f, original.snappedSize(20f).height, 0f)
        assertEquals(original, original.copy())
    }

    @Test fun invalidSnapSettingsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { snapToStep(10f, 0f) }
        assertThrows(IllegalArgumentException::class.java) { snapToStep(Float.NaN, 10f) }
    }
}
