package com.sengine.core

/** Immutable single-object prefab operations shared by editor and project serialization. */
fun GameProject.makePrefab(entityId: String, prefabId: String): GameProject {
    require(prefabs.size < 128 && prefabs.none { it.id == prefabId }) { "Prefab limit reached or ID already exists" }
    val source = activeScene().entities.firstOrNull { it.id == entityId }
        ?: throw IllegalArgumentException("Object not found")
    require(source.prefabId == null) { "Unpack this instance before creating a new prefab" }
    val template = source.copy(
        prefabId = null, locked = false,
        transform = source.transform.copy(x = 0f, y = 0f),
    )
    val prefab = PrefabAsset(prefabId, source.name.ifBlank { "Prefab" }.take(100), template = template)
    return copy(
        prefabs = prefabs + prefab,
        scenes = scenes.map { scene ->
            if (scene.id == activeSceneId) scene.updateEntity(entityId) { it.copy(prefabId = prefabId) }
            else scene
        },
    )
}

/** Instantiate at an explicit world position; IDs are always supplied by the editor. */
fun GameProject.instantiatePrefab(prefabId: String, entityId: String, position: Vec2): GameProject {
    val prefab = prefabs.firstOrNull { it.id == prefabId } ?: throw IllegalArgumentException("Prefab not found")
    val scene = activeScene()
    require(scene.entities.size < 2000 && scene.entities.none { it.id == entityId }) { "Scene is full or object ID already exists" }
    val count = scene.entities.count { it.prefabId == prefabId }
    val instance = prefab.template.copy(
        id = entityId, name = "${prefab.name} ${count + 1}".take(100), prefabId = prefabId,
        transform = prefab.template.transform.copy(x = position.x, y = position.y),
        locked = false,
    )
    return withScene(scene.copy(entities = scene.entities + instance))
}

/** Apply a selected instance's component configuration to its source and all linked instances.
 * Instance names, locks and world positions remain independent; geometry/behavior are synchronized.
 */
fun GameProject.applyPrefab(entityId: String): GameProject {
    val source = activeScene().entities.firstOrNull { it.id == entityId }
        ?: throw IllegalArgumentException("Object not found")
    val prefabId = source.prefabId ?: throw IllegalArgumentException("Object is not a prefab instance")
    require(prefabs.any { it.id == prefabId }) { "Prefab source is missing" }
    val template = source.copy(prefabId = null, locked = false, transform = source.transform.copy(x = 0f, y = 0f))
    return copy(
        prefabs = prefabs.map { if (it.id == prefabId) it.copy(template = template) else it },
        scenes = scenes.map { scene -> scene.copy(entities = scene.entities.map { instance ->
            if (instance.prefabId == prefabId) instance.withPrefabComponents(template) else instance
        }) },
    )
}

/** Revert one instance, leaving its world position/name/lock alone. */
fun GameProject.revertPrefab(entityId: String): GameProject {
    val source = activeScene().entities.firstOrNull { it.id == entityId }
        ?: throw IllegalArgumentException("Object not found")
    val prefab = prefabs.firstOrNull { it.id == source.prefabId }
        ?: throw IllegalArgumentException("Prefab source is missing")
    return withScene(activeScene().updateEntity(entityId) { it.withPrefabComponents(prefab.template) })
}

/** Removing a source unpacks instances without destroying placed game objects. */
fun GameProject.removePrefab(prefabId: String): GameProject = copy(
    prefabs = prefabs.filterNot { it.id == prefabId },
    scenes = scenes.map { scene -> scene.copy(entities = scene.entities.map { entity ->
        if (entity.prefabId == prefabId) entity.copy(prefabId = null) else entity
    }) },
)

private fun Entity.withPrefabComponents(template: Entity): Entity = copy(
    visual = template.visual,
    physics = template.physics,
    motion = template.motion,
    scriptId = template.scriptId,
    visible = template.visible,
    transform = transform.copy(
        width = template.transform.width, height = template.transform.height,
        rotation = template.transform.rotation,
    ),
)
