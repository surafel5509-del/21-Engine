package com.sengine.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefabOpsTest {
    private fun project(): GameProject {
        val source = Entity("source", "Crate", transform = Transform(x = 18f, y = 32f, width = 40f))
        val scene = GameScene("scene", "Workspace", entities = listOf(source))
        return GameProject(id = "project", name = "Studio", scenes = listOf(scene), activeSceneId = scene.id)
    }

    @Test fun createInstantiateApplyRevertAndPackRoundTrip() {
        val created = project().makePrefab("source", "crate-prefab")
        assertEquals("crate-prefab", created.activeScene().entities.first().prefabId)
        assertEquals(0f, created.prefabs.single().template.transform.x, 0f)
        val placed = created.instantiatePrefab("crate-prefab", "instance", Vec2(300f, -30f))
        assertEquals(300f, placed.activeScene().entities.last().transform.x, 0f)
        val edited = placed.withScene(placed.activeScene().updateEntity("instance") {
            it.copy(transform = it.transform.copy(width = 92f), visual = it.visual.copy(color = 0xFF22BBAA.toInt()))
        })
        val applied = edited.applyPrefab("instance")
        assertEquals(92f, applied.activeScene().entities.first().transform.width, 0f)
        assertEquals(18f, applied.activeScene().entities.first().transform.x, 0f)
        assertEquals(300f, applied.activeScene().entities.last().transform.x, 0f)
        val modified = applied.withScene(applied.activeScene().updateEntity("instance") {
            it.copy(transform = it.transform.copy(width = 120f))
        })
        val reverted = modified.revertPrefab("instance")
        assertEquals(92f, reverted.activeScene().entities.last().transform.width, 0f)
        val decoded = ProjectCodec.decode(ProjectCodec.encode(reverted))
        assertEquals(reverted, decoded)
        assertTrue(decoded.activeScene().entities.all { it.prefabId == "crate-prefab" })
    }

    @Test fun applyingPrefabUpdatesOtherScenesButPreservesTheirPositions() {
        val linked = project().makePrefab("source", "crate-prefab")
        val anotherScene = GameScene("second", "Second")
        val withSecond = linked.copy(scenes = linked.scenes + anotherScene, activeSceneId = "second")
            .instantiatePrefab("crate-prefab", "placed", Vec2(-275f, 120f))
        val first = withSecond.copy(activeSceneId = "scene")
        val changed = first.withScene(first.activeScene().updateEntity("source") {
            it.copy(transform = it.transform.copy(height = 96f))
        }).applyPrefab("source")
        val placed = changed.scenes.last().entities.single()
        assertEquals(96f, placed.transform.height, 0f)
        assertEquals(-275f, placed.transform.x, 0f)
        ProjectCodec.validate(changed)
    }

    @Test fun deletingSourceUnpacksObjectsAndCannotLeaveDanglingLinks() {
        val linked = project().makePrefab("source", "crate-prefab")
        val removed = linked.removePrefab("crate-prefab")
        assertTrue(removed.prefabs.isEmpty())
        assertNull(removed.activeScene().entities.single().prefabId)
        ProjectCodec.validate(removed)
        assertThrows(IllegalArgumentException::class.java) {
            ProjectCodec.validate(linked.copy(prefabs = emptyList()))
        }
    }

    @Test fun unknownPrefabAndDuplicatedIdsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            project().instantiatePrefab("missing", "instance", Vec2())
        }
        val linked = project().makePrefab("source", "crate-prefab")
        assertThrows(IllegalArgumentException::class.java) {
            linked.makePrefab("source", "another")
        }
        assertFalse(linked.prefabs.any { it.template.prefabId != null })
    }
}
