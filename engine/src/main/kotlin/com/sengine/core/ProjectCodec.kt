package com.sengine.core

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Versioned, portable scene format shared by autosaves and .sengine archives. */
object ProjectCodec {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    private val safeId = Regex("[A-Za-z0-9_-]{1,80}")

    fun encode(project: GameProject): String {
        validate(project)
        return json.encodeToString(project)
    }

    fun decode(source: String): GameProject = json.decodeFromString<GameProject>(source).also(::validate)

    fun validate(project: GameProject) {
        require(project.formatVersion == 1) { "Unsupported project format" }
        require(safeId.matches(project.id)) { "Invalid project ID" }
        require(project.name.length in 1..80) { "Invalid project name" }
        require(project.scenes.size in 1..32) { "Project needs 1–32 scenes" }
        require(project.scenes.map { it.id }.distinct().size == project.scenes.size)
        require(project.scenes.any { it.id == project.activeSceneId }) { "Active scene is missing" }
        require(project.assets.size <= 256)
        require(project.assets.map { it.id }.distinct().size == project.assets.size)
        project.assets.forEach { asset ->
            require(safeId.matches(asset.id) && asset.name.length <= 160 && asset.folder.isFolder()) { "Invalid asset" }
        }
        require(project.scripts.size <= 64) { "Too many scripts" }
        require(project.scripts.map { it.id }.distinct().size == project.scripts.size)
        project.scripts.forEach { script ->
            require(safeId.matches(script.id) && script.name.length in 1..100 && script.folder.isFolder()) { "Invalid script" }
            require(script.source.length <= 16_000) { "Script is too large" }
        }
        val scriptIds = project.scripts.mapTo(mutableSetOf()) { it.id }
        val assetIds = project.assets.mapTo(mutableSetOf()) { it.id }
        project.scenes.forEach { scene ->
            require(safeId.matches(scene.id) && scene.name.length <= 100) { "Invalid scene" }
            require(scene.camera.x.isPosition() && scene.camera.y.isPosition())
            require(scene.camera.zoom.isFinite() && scene.camera.zoom in 0.2f..4f)
            require(scene.gameWidth in 100f..4000f && scene.gameHeight in 100f..4000f)
            require(scene.gravity.x in -2000f..2000f && scene.gravity.y in -2000f..2000f)
            require(scene.entities.size <= 2000) { "Scene has too many objects" }
            require(scene.entities.map { it.id }.distinct().size == scene.entities.size)
            scene.entities.forEach { entity ->
                require(safeId.matches(entity.id) && entity.name.length <= 100) { "Invalid object" }
                val t = entity.transform
                require(t.x.isPosition() && t.y.isPosition() && t.rotation.isFinite())
                require(t.width.isFinite() && t.width in 1f..10000f)
                require(t.height.isFinite() && t.height in 1f..10000f)
                require(entity.visual.text.length <= 500)
                if (entity.visual.type == VisualType.IMAGE) {
                    val assetId = entity.visual.assetId
                    require(assetId != null && assetId in assetIds) { "Sprite asset is missing" }
                }
                entity.scriptId?.let { require(it in scriptIds) { "Object script is missing" } }
                entity.physics?.let { body ->
                    require(body.velocity.x in -5000f..5000f && body.velocity.y in -5000f..5000f)
                    require(body.gravityScale.isFinite() && body.gravityScale in 0f..5f)
                    require(body.bounce.isFinite() && body.bounce in 0f..1f)
                    require(body.friction.isFinite() && body.friction in 0f..1f)
                    require(body.density.isFinite() && body.density in 0.01f..100f)
                    require(body.linearDamping.isFinite() && body.linearDamping in 0f..20f)
                }
                require(entity.motion.speed.isFinite() && entity.motion.speed in 0f..6f)
                require(entity.motion.amplitude.isFinite() && entity.motion.amplitude in 0f..2000f)
            }
        }
    }

    private fun Float.isPosition(): Boolean = isFinite() && this in -1_000_000f..1_000_000f

    private fun String.isFolder(): Boolean = length in 1..100 && split('/').all { part ->
        part.length in 1..32 && part.matches(Regex("[A-Za-z0-9 _-]+")) && part.isNotBlank()
    }
}
