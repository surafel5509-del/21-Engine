package com.sengine.studio

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sengine.core.BodyType
import com.sengine.core.Entity
import com.sengine.core.GameProject
import com.sengine.core.GameScene
import com.sengine.core.EngineLog
import com.sengine.core.EngineLogLevel
import com.sengine.core.ScriptAsset
import com.sengine.core.PhysicsBody
import com.sengine.core.ProjectFactory
import com.sengine.core.ProjectTemplate
import com.sengine.core.SceneCamera
import com.sengine.core.VisualType
import com.sengine.core.Vec2
import com.sengine.core.WorldRunner
import com.sengine.core.applyPrefab
import com.sengine.core.instantiatePrefab
import com.sengine.core.makePrefab
import com.sengine.core.moveLayer
import com.sengine.core.removePrefab
import com.sengine.core.revertPrefab
import com.sengine.core.snappedPosition
import com.sengine.core.snappedRotation
import com.sengine.core.snappedSize
import com.sengine.core.removeEntity
import com.sengine.core.updateEntity
import com.sengine.studio.data.ProjectStore
import com.sengine.studio.data.ProjectSummary
import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class EditorPanel { HIERARCHY, INSPECTOR, ASSETS, SCENES }
enum class EditorTool { PAN, MOVE, ROTATE, SCALE }
enum class SaveStatus { SAVED, SAVING, ERROR }

data class StudioState(
    val projects: List<ProjectSummary> = emptyList(),
    val project: GameProject? = null,
    val selectedId: String? = null,
    val panel: EditorPanel = EditorPanel.HIERARCHY,
    val playing: Boolean = false,
    val paused: Boolean = false,
    val playScene: GameScene? = null,
    val fps: Int = 0,
    val console: List<EngineLog> = emptyList(),
    val showColliders: Boolean = false,
    val snapEnabled: Boolean = false,
    val snapStep: Float = 20f,
    val tool: EditorTool = EditorTool.MOVE,
    val editingScriptId: String? = null,
    val busy: Boolean = false,
    val saveStatus: SaveStatus = SaveStatus.SAVED,
    val notice: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ProjectStore(application)
    private val _state = MutableStateFlow(StudioState())
    val state = _state.asStateFlow()
    private val undo = ArrayDeque<GameProject>()
    private val redo = ArrayDeque<GameProject>()
    private val saveMutex = Mutex()
    private val saveJobs = mutableMapOf<String, Job>()
    private val saveRevisions = mutableMapOf<String, Long>()
    private var playJob: Job? = null
    private var runner: WorldRunner? = null
    private var gestureRecorded = false
    private var gestureEntityId: String? = null

    init { refreshProjects() }

    fun refreshProjects() {
        viewModelScope.launch {
            try {
                val projects = withContext(Dispatchers.IO) { store.list() }
                _state.update { it.copy(projects = projects) }
            } catch (error: Exception) { report(error) }
        }
    }

    fun createProject(name: String, template: ProjectTemplate) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                val project = withContext(Dispatchers.IO) { store.create(name, template) }
                enterProject(project)
            } catch (error: Exception) { report(error) }
            finally { _state.update { it.copy(busy = false) } }
        }
    }

    fun openProject(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                val project = withContext(Dispatchers.IO) { store.load(id) }
                enterProject(project)
            } catch (error: Exception) { report(error) }
            finally { _state.update { it.copy(busy = false) } }
        }
    }

    fun closeProject() {
        if (_state.value.playing) {
            stop()
            return
        }
        val snapshot = _state.value.project ?: return
        val save = queueSave(snapshot, immediate = true)
        _state.update { it.copy(project = null, selectedId = null, playScene = null) }
        undo.clear()
        redo.clear()
        viewModelScope.launch { save.join(); refreshProjects() }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                saveJobs[id]?.cancel()
                withContext(Dispatchers.IO) { saveMutex.withLock { store.delete(id) } }
                val projects = withContext(Dispatchers.IO) { store.list() }
                _state.update { it.copy(projects = projects, notice = "Project deleted") }
            } catch (error: Exception) { report(error) }
            finally { _state.update { it.copy(busy = false) } }
        }
    }

    fun importArchive(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                val project = withContext(Dispatchers.IO) { store.importArchive(uri) }
                enterProject(project)
                notice("Project imported")
            } catch (error: Exception) { report(error) }
            finally { _state.update { it.copy(busy = false) } }
        }
    }

    fun exportProject(uri: Uri) {
        val snapshot = _state.value.project ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                withContext(Dispatchers.IO) { store.export(snapshot, uri) }
                notice("Project package exported")
            } catch (error: Exception) { report(error) }
            finally { _state.update { it.copy(busy = false) } }
        }
    }

    fun exportWebGame(uri: Uri) {
        val snapshot = _state.value.project ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                withContext(Dispatchers.IO) { store.exportWeb(snapshot, uri) }
                notice("Web game exported · unzip and open index.html")
            } catch (error: Exception) { report(error) }
            finally { _state.update { it.copy(busy = false) } }
        }
    }

    fun importImage(uri: Uri) {
        if (_state.value.playing) return
        val projectId = _state.value.project?.id ?: return
        if ((_state.value.project?.assets?.size ?: 0) >= 256) { notice("Asset limit reached"); return }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                val asset = withContext(Dispatchers.IO) { store.importImage(projectId, uri) }
                if (_state.value.project?.id == projectId) {
                    editProject { it.copy(assets = it.assets + asset) }
                    addEntity(VisualType.IMAGE, asset.id)
                    notice("Image imported • drag the sprite to position it")
                }
            } catch (error: Exception) { report(error) }
            finally { _state.update { it.copy(busy = false) } }
        }
    }

    fun createScript() { createScriptImpl(null) }
    fun createScriptForEntity(entityId: String) { createScriptImpl(entityId) }

    private fun createScriptImpl(entityId: String?) {
        val project = _state.value.project ?: return
        if (_state.value.playing) return
        if (project.scripts.size >= 64) { notice("Script limit reached"); return }
        val script = ScriptAsset(
            id = ProjectFactory.id(), name = "Script ${project.scripts.size + 1}",
            source = "on start\n  log \"Ready\"\nend\n\non update\n  # move 80 * dt, 0\nend\n\non tap\n  impulse 0, -470\nend\n",
        )
        editProject { current ->
            val withScript = current.copy(scripts = current.scripts + script)
            if (entityId == null) withScript else withScript.withScene(
                withScript.activeScene().updateEntity(entityId) { it.copy(scriptId = script.id) },
            )
        }
        _state.update { it.copy(editingScriptId = script.id) }
    }

    fun saveScript(id: String, source: String, name: String, folder: String) {
        if (_state.value.playing) return
        if (source.length > 16_000 || name.isBlank() || name.length > 100 || !validFolder(folder)) {
            notice("Check script name, folder and 16 KB source limit")
            return
        }
        editProject { project -> project.copy(scripts = project.scripts.map { script ->
            if (script.id == id) script.copy(name = name.trim(), folder = folder.trim(), source = source) else script
        }) }
        _state.update { it.copy(editingScriptId = null) }
        appendLog(EngineLog(EngineLogLevel.INFO, "Saved $name"))
    }

    fun attachScript(entityId: String, scriptId: String?) {
        if (scriptId != null && _state.value.project?.scripts?.none { it.id == scriptId } != false) return
        editEntity(entityId) { it.copy(scriptId = scriptId) }
    }

    fun deleteScript(id: String) {
        if (_state.value.playing) return
        val project = _state.value.project ?: return
        if (project.scenes.any { scene -> scene.entities.any { it.scriptId == id } } ||
            project.prefabs.any { it.template.scriptId == id }) {
            notice("Detach this script from objects and prefabs before deleting it")
            return
        }
        editProject { it.copy(scripts = it.scripts.filterNot { script -> script.id == id }) }
        if (_state.value.editingScriptId == id) closeScriptEditor()
    }

    fun renameAsset(id: String, name: String, folder: String) {
        if (name.isBlank() || name.length > 160 || !validFolder(folder)) { notice("Invalid asset name or folder"); return }
        editProject { it.copy(assets = it.assets.map { asset ->
            if (asset.id == id) asset.copy(name = name.trim(), folder = folder.trim()) else asset
        }) }
    }

    fun removeAsset(id: String) {
        if (_state.value.playing) return
        val project = _state.value.project ?: return
        if (project.scenes.any { scene -> scene.entities.any { it.visual.assetId == id && it.visual.type == VisualType.IMAGE } } ||
            project.prefabs.any { it.template.visual.type == VisualType.IMAGE && it.template.visual.assetId == id }) {
            notice("Remove sprites and prefabs using this image before deleting it")
            return
        }
        // The underlying file is retained until project deletion so Undo can restore the asset.
        editProject { it.copy(assets = it.assets.filterNot { asset -> asset.id == id }) }
    }

    fun createPrefab(entityId: String) {
        val project = _state.value.project ?: return
        if (_state.value.playing) return
        if (project.prefabs.size >= 128) { notice("Prefab limit reached"); return }
        val source = project.activeScene().entities.firstOrNull { it.id == entityId } ?: return
        if (source.prefabId != null) { notice("Unpack this instance to make a new prefab"); return }
        val id = ProjectFactory.id()
        editProject { it.makePrefab(entityId, id) }
        notice("Prefab saved • find it in the Project browser")
    }

    fun instantiatePrefab(prefabId: String) {
        val project = _state.value.project ?: return
        if (_state.value.playing || project.prefabs.none { it.id == prefabId }) return
        val scene = project.activeScene()
        if (scene.entities.size >= 2000) { notice("Object limit reached"); return }
        val id = ProjectFactory.id()
        editProject { it.instantiatePrefab(prefabId, id, Vec2(scene.camera.x, scene.camera.y)) }
        _state.update { it.copy(selectedId = id, panel = EditorPanel.INSPECTOR) }
    }

    fun applySelectedPrefab(entityId: String) {
        if (_state.value.playing) return
        val entity = _state.value.project?.activeScene()?.entities?.firstOrNull { it.id == entityId } ?: return
        if (entity.prefabId == null) return
        editProject { it.applyPrefab(entityId) }
        notice("Prefab changes applied to linked instances")
    }

    fun revertSelectedPrefab(entityId: String) {
        val entity = _state.value.project?.activeScene()?.entities?.firstOrNull { it.id == entityId } ?: return
        if (entity.prefabId == null) return
        editProject { it.revertPrefab(entityId) }
    }

    fun unpackPrefab(entityId: String) { editEntity(entityId) { it.copy(prefabId = null) } }

    fun renamePrefab(id: String, name: String, folder: String) {
        if (_state.value.playing) return
        if (name.isBlank() || name.length > 100 || !validFolder(folder)) {
            notice("Invalid prefab name or folder"); return
        }
        editProject { project -> project.copy(prefabs = project.prefabs.map {
            if (it.id == id) it.copy(name = name.trim(), folder = folder.trim()) else it
        }) }
    }

    fun deletePrefab(id: String) {
        if (_state.value.playing) return
        editProject { it.removePrefab(id) }
        notice("Prefab deleted • placed objects were unpacked")
    }

    fun assetFile(projectId: String, assetId: String) = store.assetFile(projectId, assetId)

    private fun validFolder(folder: String): Boolean = folder.length in 1..100 &&
        folder.split('/').all { it.length in 1..32 && it.matches(Regex("[A-Za-z0-9 _-]+")) && it.isNotBlank() }

    fun saveNow() { _state.value.project?.let { queueSave(it, immediate = true) } }
    fun dismissNotice() { _state.update { it.copy(notice = null) } }
    fun showPanel(panel: EditorPanel) { _state.update { it.copy(panel = panel) } }
    fun setTool(tool: EditorTool) { _state.update { it.copy(tool = tool) } }
    fun toggleColliders() { _state.update { it.copy(showColliders = !it.showColliders) } }
    fun toggleSnap() { _state.update { it.copy(snapEnabled = !it.snapEnabled) } }
    fun setSnapStep(step: Float) {
        if (step in listOf(5f, 10f, 20f, 40f, 80f)) _state.update { it.copy(snapStep = step, snapEnabled = true) }
    }
    fun clearConsole() { _state.update { it.copy(console = emptyList()) } }
    fun openScriptEditor(id: String) { if (!_state.value.playing) _state.update { it.copy(editingScriptId = id) } }
    fun closeScriptEditor() { _state.update { it.copy(editingScriptId = null) } }
    fun select(id: String?) { _state.update { it.copy(selectedId = id, panel = if (id == null) it.panel else EditorPanel.INSPECTOR) } }

    fun renameProject(name: String) {
        editProject { it.copy(name = name.trim().take(48).ifBlank { "Untitled project" }) }
    }

    fun addScene() {
        val project = _state.value.project ?: return
        if (project.scenes.size >= 32) { notice("Scene limit reached"); return }
        val scene = GameScene(ProjectFactory.id(), "Scene ${project.scenes.size + 1}")
        editProject { it.copy(scenes = it.scenes + scene, activeSceneId = scene.id) }
        _state.update { it.copy(selectedId = null) }
    }

    fun selectScene(id: String) {
        editProject { project -> if (project.scenes.any { it.id == id }) project.copy(activeSceneId = id) else project }
        _state.update { it.copy(selectedId = null) }
    }

    fun renameScene(name: String) {
        editScene { it.copy(name = name.trim().take(100).ifBlank { "Scene" }) }
    }

    fun deleteScene(id: String) {
        editProject { project ->
            if (project.scenes.size <= 1) { notice("Keep at least one scene"); project }
            else {
                val scenes = project.scenes.filterNot { it.id == id }
                project.copy(scenes = scenes, activeSceneId = if (project.activeSceneId == id) scenes.first().id else project.activeSceneId)
            }
        }
        _state.update { it.copy(selectedId = null) }
    }

    fun setBackground(color: Int) { editScene { it.copy(background = color) } }
    fun setGravity(x: Float, y: Float) { editScene { it.copy(gravity = com.sengine.core.Vec2(x, y)) } }
    fun setGameSize(width: Float, height: Float) { editScene { it.copy(gameWidth = width, gameHeight = height) } }

    fun addEntity(type: VisualType, assetId: String? = null) {
        val scene = _state.value.project?.activeScene() ?: return
        if (scene.entities.size >= 2000) { notice("Object limit reached"); return }
        if (type == VisualType.IMAGE && _state.value.project?.assets?.none { it.id == assetId } != false) return
        val entity = ProjectFactory.entity(type, scene, assetId)
        editScene { it.copy(entities = it.entities + entity) }
        _state.update { it.copy(selectedId = entity.id, panel = EditorPanel.INSPECTOR) }
    }

    fun duplicateEntity(id: String) {
        val scene = _state.value.project?.activeScene() ?: return
        if (scene.entities.size >= 2000) { notice("Object limit reached"); return }
        val source = scene.entities.firstOrNull { it.id == id } ?: return
        val duplicate = source.copy(
            id = ProjectFactory.id(), name = (source.name + " Copy").take(100),
            transform = source.transform.copy(x = source.transform.x + 24f, y = source.transform.y + 24f),
        )
        editScene { it.copy(entities = it.entities + duplicate) }
        _state.update { it.copy(selectedId = duplicate.id) }
    }

    fun deleteEntity(id: String) {
        editScene { it.removeEntity(id) }
        _state.update { it.copy(selectedId = null, panel = EditorPanel.HIERARCHY) }
    }

    fun reorderEntity(id: String, direction: Int) { editScene { it.moveLayer(id, direction) } }
    fun editEntity(id: String, change: (Entity) -> Entity) { editScene { it.updateEntity(id, change) } }

    fun beginGesture() { gestureRecorded = false; gestureEntityId = null }

    fun dragEntity(id: String, deltaX: Float, deltaY: Float) {
        if (deltaX == 0f && deltaY == 0f) return
        val record = !gestureRecorded
        editScene(record = record, persist = false) { scene ->
            scene.updateEntity(id) { entity ->
                val t = entity.transform
                entity.copy(transform = t.copy(
                    x = (t.x + deltaX).coerceIn(-999_999f, 999_999f),
                    y = (t.y + deltaY).coerceIn(-999_999f, 999_999f),
                ))
            }
        }
        gestureRecorded = true
        gestureEntityId = id
    }

    fun rotateEntity(id: String, degrees: Float) {
        if (degrees == 0f) return
        editScene(record = !gestureRecorded, persist = false) { scene ->
            scene.updateEntity(id) { entity ->
                entity.copy(transform = entity.transform.copy(rotation = entity.transform.rotation + degrees))
            }
        }
        gestureRecorded = true
        gestureEntityId = id
    }

    fun resizeEntity(id: String, delta: Float) {
        if (delta == 0f) return
        editScene(record = !gestureRecorded, persist = false) { scene ->
            scene.updateEntity(id) { entity ->
                entity.copy(transform = entity.transform.copy(
                    width = (entity.transform.width + delta).coerceIn(1f, 10000f),
                    height = (entity.transform.height + delta).coerceIn(1f, 10000f),
                ))
            }
        }
        gestureRecorded = true
        gestureEntityId = id
    }

    fun gestureCamera(camera: SceneCamera) {
        editScene(record = !gestureRecorded, persist = false) {
            it.copy(camera = camera.copy(
                x = camera.x.coerceIn(-999_999f, 999_999f),
                y = camera.y.coerceIn(-999_999f, 999_999f),
                zoom = camera.zoom.coerceIn(0.2f, 4f),
            ))
        }
        gestureRecorded = true
    }

    fun endGesture() {
        val target = gestureEntityId
        val settings = _state.value
        if (gestureRecorded && target != null && settings.snapEnabled) {
            editScene(record = false, persist = false) { scene -> scene.updateEntity(target) { entity ->
                val transform = when (settings.tool) {
                    EditorTool.MOVE -> entity.transform.snappedPosition(settings.snapStep)
                    EditorTool.ROTATE -> entity.transform.snappedRotation()
                    EditorTool.SCALE -> entity.transform.snappedSize(settings.snapStep)
                    EditorTool.PAN -> entity.transform
                }
                entity.copy(transform = transform)
            } }
        }
        if (gestureRecorded) saveNow()
        gestureRecorded = false
        gestureEntityId = null
    }

    fun zoom(factor: Float) {
        editScene { it.copy(camera = it.camera.copy(zoom = (it.camera.zoom * factor).coerceIn(0.2f, 4f))) }
    }

    fun frameScene() { editScene { it.copy(camera = SceneCamera()) } }

    fun focusSelected() {
        val scene = _state.value.project?.activeScene() ?: return
        val target = scene.entities.firstOrNull { it.id == _state.value.selectedId } ?: return
        editScene { it.copy(camera = it.camera.copy(x = target.transform.x, y = target.transform.y)) }
    }

    fun undo() {
        if (_state.value.playing || undo.isEmpty()) return
        val current = _state.value.project ?: return
        redo.addLast(current)
        restore(undo.removeLast())
    }

    fun redo() {
        if (_state.value.playing || redo.isEmpty()) return
        val current = _state.value.project ?: return
        undo.addLast(current)
        restore(redo.removeLast())
    }

    fun play() {
        val project = _state.value.project ?: return
        if (_state.value.playing) return
        val world = try { WorldRunner(project.activeScene(), project.scripts) }
        catch (error: Exception) {
            report(error)
            appendLog(EngineLog(EngineLogLevel.ERROR, "Could not start physics world: ${error.message}"))
            return
        }
        runner = world
        _state.update { it.copy(playing = true, paused = false, playScene = world.scene, fps = 0) }
        appendLog(EngineLog(EngineLogLevel.INFO, "Play started • ${world.bodyCount} physics bodies"))
        collectLogs(world)
        playJob = viewModelScope.launch {
            var last = System.nanoTime()
            var sampleStart = last
            var frames = 0
            while (isActive && _state.value.playing) {
                delay(16)
                val now = System.nanoTime()
                val delta = (now - last) / 1_000_000_000f
                last = now
                val active = runner ?: break
                if (!_state.value.paused) {
                    try {
                        active.advance(delta)
                        frames++
                        collectLogs(active)
                        val nextWorld = followSceneRequest(active)
                        _state.update { if (it.playing) it.copy(playScene = nextWorld.scene) else it }
                    } catch (error: Exception) {
                        appendLog(EngineLog(EngineLogLevel.ERROR, "Runtime stopped: ${error.message}"))
                        stop()
                        break
                    }
                }
                if (now - sampleStart >= 1_000_000_000L) {
                    _state.update { it.copy(fps = if (it.paused) 0 else frames) }
                    frames = 0
                    sampleStart = now
                }
            }
        }
    }

    fun togglePause() {
        if (!_state.value.playing) return
        _state.update { it.copy(paused = !it.paused, fps = if (it.paused) it.fps else 0) }
        appendLog(EngineLog(EngineLogLevel.INFO, if (_state.value.paused) "Preview paused" else "Preview resumed"))
    }

    fun stepFrame() {
        if (!_state.value.playing || !_state.value.paused) return
        val world = runner ?: return
        try {
            world.stepOnce()
            collectLogs(world)
            val nextWorld = followSceneRequest(world)
            _state.update { it.copy(playScene = nextWorld.scene) }
        } catch (error: Exception) {
            appendLog(EngineLog(EngineLogLevel.ERROR, "Step failed: ${error.message}"))
            stop()
        }
    }

    fun stop() {
        val wasPlaying = _state.value.playing
        playJob?.cancel()
        playJob = null
        runner = null
        _state.update { it.copy(playing = false, paused = false, playScene = null, fps = 0) }
        if (wasPlaying) appendLog(EngineLog(EngineLogLevel.INFO, "Play stopped • edit scene restored"))
    }

    fun playTap(x: Float, y: Float) {
        if (_state.value.paused) return
        val world = runner ?: return
        if (world.tap(x, y)) {
            collectLogs(world)
            val nextWorld = followSceneRequest(world)
            _state.update { it.copy(playScene = nextWorld.scene) }
        }
    }

    private fun followSceneRequest(current: WorldRunner): WorldRunner {
        val reference = current.consumeSceneRequest() ?: return current
        val project = _state.value.project ?: return current
        val destination = project.scenes.firstOrNull { it.id == reference }
            ?: project.scenes.firstOrNull { it.name == reference }
        if (destination == null) {
            appendLog(EngineLog(EngineLogLevel.WARNING, "Scene not found: $reference"))
            return current
        }
        if (destination.id == current.scene.id) return current
        val next = WorldRunner(destination, project.scripts)
        runner = next
        appendLog(EngineLog(EngineLogLevel.INFO, "Loaded scene: ${destination.name}"))
        collectLogs(next)
        return next
    }

    private fun appendLog(message: EngineLog) {
        _state.update { it.copy(console = (it.console + message).takeLast(500)) }
    }

    private fun collectLogs(world: WorldRunner) {
        val logs = world.drainLogs()
        if (logs.isNotEmpty()) _state.update { it.copy(console = (it.console + logs).takeLast(500)) }
    }

    private fun enterProject(project: GameProject) {
        stop()
        undo.clear()
        redo.clear()
        _state.update { it.copy(
            project = project, selectedId = null, panel = EditorPanel.HIERARCHY,
            editingScriptId = null, console = emptyList(), tool = EditorTool.MOVE,
            canUndo = false, canRedo = false, saveStatus = SaveStatus.SAVED,
        ) }
    }

    private fun editScene(record: Boolean = true, persist: Boolean = true, change: (GameScene) -> GameScene) {
        editProject(record, persist) { it.withScene(change(it.activeScene())) }
    }

    private fun editProject(record: Boolean = true, persist: Boolean = true, change: (GameProject) -> GameProject) {
        if (_state.value.playing) return
        val current = _state.value.project ?: return
        val changed = change(current)
        if (changed == current) return
        if (record) {
            undo.addLast(current)
            if (undo.size > 40) undo.removeFirst()
            redo.clear()
        }
        val updated = changed.copy(updatedAt = System.currentTimeMillis())
        _state.update { it.copy(project = updated, canUndo = undo.isNotEmpty(), canRedo = redo.isNotEmpty(), saveStatus = SaveStatus.SAVING) }
        if (persist) queueSave(updated)
    }

    private fun restore(project: GameProject) {
        val updated = project.copy(updatedAt = System.currentTimeMillis())
        val activeIds = updated.activeScene().entities.map { it.id }.toSet()
        _state.update { it.copy(
            project = updated, selectedId = it.selectedId?.takeIf { id -> id in activeIds },
            canUndo = undo.isNotEmpty(), canRedo = redo.isNotEmpty(), saveStatus = SaveStatus.SAVING,
        ) }
        queueSave(updated)
    }

    private fun queueSave(snapshot: GameProject, immediate: Boolean = false): Job {
        saveJobs[snapshot.id]?.cancel()
        val revision = (saveRevisions[snapshot.id] ?: 0L) + 1L
        saveRevisions[snapshot.id] = revision
        val job = viewModelScope.launch {
            try {
                if (!immediate) delay(350)
                withContext(Dispatchers.IO) { saveMutex.withLock { store.save(snapshot) } }
                if (saveRevisions[snapshot.id] == revision) {
                    _state.update { state ->
                        if (state.project?.id == snapshot.id) state.copy(saveStatus = SaveStatus.SAVED) else state
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                _state.update { it.copy(saveStatus = SaveStatus.ERROR) }
                report(error)
            }
        }
        saveJobs[snapshot.id] = job
        return job
    }

    private fun notice(message: String) { _state.update { it.copy(notice = message) } }
    private fun report(error: Exception) {
        if (error is CancellationException) throw error
        notice(error.message?.take(180) ?: "Something went wrong. Please try again.")
    }
}
