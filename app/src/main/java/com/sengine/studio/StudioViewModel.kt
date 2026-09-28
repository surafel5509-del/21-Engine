package com.sengine.studio

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sengine.core.BodyType
import com.sengine.core.Entity
import com.sengine.core.GameProject
import com.sengine.core.GameScene
import com.sengine.core.Motion
import com.sengine.core.PhysicsBody
import com.sengine.core.ProjectFactory
import com.sengine.core.ProjectTemplate
import com.sengine.core.SceneCamera
import com.sengine.core.VisualType
import com.sengine.core.WorldRunner
import com.sengine.core.moveLayer
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
enum class SaveStatus { SAVED, SAVING, ERROR }

data class StudioState(
    val projects: List<ProjectSummary> = emptyList(),
    val project: GameProject? = null,
    val selectedId: String? = null,
    val panel: EditorPanel = EditorPanel.HIERARCHY,
    val playing: Boolean = false,
    val playScene: GameScene? = null,
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

    fun assetFile(projectId: String, assetId: String) = store.assetFile(projectId, assetId)

    fun saveNow() { _state.value.project?.let { queueSave(it, immediate = true) } }
    fun dismissNotice() { _state.update { it.copy(notice = null) } }
    fun showPanel(panel: EditorPanel) { _state.update { it.copy(panel = panel) } }
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

    fun beginGesture() { gestureRecorded = false }

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
        if (gestureRecorded) saveNow()
        gestureRecorded = false
    }

    fun zoom(factor: Float) {
        editScene { it.copy(camera = it.camera.copy(zoom = (it.camera.zoom * factor).coerceIn(0.2f, 4f))) }
    }

    fun frameScene() { editScene { it.copy(camera = SceneCamera()) } }

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
        val scene = _state.value.project?.activeScene() ?: return
        if (_state.value.playing) return
        runner = WorldRunner(scene)
        _state.update { it.copy(playing = true, playScene = scene) }
        playJob = viewModelScope.launch {
            var last = System.nanoTime()
            while (isActive && _state.value.playing) {
                delay(16)
                val now = System.nanoTime()
                val next = runner?.advance((now - last) / 1_000_000_000f) ?: break
                last = now
                _state.update { if (it.playing) it.copy(playScene = next) else it }
            }
        }
    }

    fun stop() {
        playJob?.cancel()
        playJob = null
        runner = null
        _state.update { it.copy(playing = false, playScene = null) }
    }

    fun playTap(x: Float, y: Float) {
        val world = runner ?: return
        if (world.tap(x, y)) _state.update { it.copy(playScene = world.scene) }
    }

    private fun enterProject(project: GameProject) {
        stop()
        undo.clear()
        redo.clear()
        _state.update { it.copy(project = project, selectedId = null, panel = EditorPanel.HIERARCHY, canUndo = false, canRedo = false, saveStatus = SaveStatus.SAVED) }
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
