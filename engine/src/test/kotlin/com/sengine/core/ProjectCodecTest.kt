package com.sengine.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProjectCodecTest {
    @Test fun templatesRoundTripIncludingUnicode() {
        ProjectTemplate.entries.forEach { template ->
            val project = ProjectFactory.create("  My ✨ game  ", template, now = 123L)
            val restored = ProjectCodec.decode(ProjectCodec.encode(project))
            assertEquals("My ✨ game", restored.name)
            assertEquals(project, restored)
            assertEquals(123L, restored.updatedAt)
        }
    }

    @Test fun olderScenePackagesWithoutPrefabFieldsRemainReadable() {
        val source = ProjectCodec.encode(ProjectFactory.create("Legacy", ProjectTemplate.BLANK))
        val legacy = source.replace(Regex(",\\s*\"prefabs\": \\[\\]"), "")
        assertTrue(legacy != source)
        assertTrue(ProjectCodec.decode(legacy).prefabs.isEmpty())
    }

    @Test fun rejectsUnknownVersionsAndUnsafeIds() {
        val source = ProjectCodec.encode(ProjectFactory.create("Test", ProjectTemplate.BLANK))
        rejects { ProjectCodec.decode(source.replace("\"formatVersion\": 1", "\"formatVersion\": 99")) }
        rejects { ProjectCodec.decode(source.replace(Regex("\"id\": \"[^\"]+\""), "\"id\": \"../escape\"")) }
    }

    @Test fun rejectsMissingSpriteAndInvalidGeometry() {
        val base = ProjectFactory.create("Test", ProjectTemplate.BLANK)
        val scene = base.activeScene()
        val image = ProjectFactory.entity(VisualType.IMAGE, scene, "missing")
        rejects { ProjectCodec.encode(base.withScene(scene.copy(entities = listOf(image)))) }
        val box = ProjectFactory.entity(VisualType.BOX, scene).copy(transform = Transform(width = 0f))
        rejects { ProjectCodec.encode(base.withScene(scene.copy(entities = listOf(box)))) }
    }

    private fun rejects(block: () -> Unit) {
        try {
            block()
            fail("Expected invalid project to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message != null)
        }
    }
}
