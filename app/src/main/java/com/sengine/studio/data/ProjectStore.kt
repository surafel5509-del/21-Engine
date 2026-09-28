package com.sengine.studio.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import com.sengine.core.GameProject
import com.sengine.core.ImageAsset
import com.sengine.core.ProjectCodec
import com.sengine.core.ProjectFactory
import com.sengine.core.ProjectTemplate
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ProjectSummary(
    val id: String,
    val name: String,
    val updatedAt: Long,
    val sceneCount: Int,
    val objectCount: Int,
)

/** Projects are private to the app. SAF is used only for user-initiated imports/exports. */
class ProjectStore(private val context: Context) {
    private val root = File(context.filesDir, "projects").apply { mkdirs() }
    private val safeId = Regex("[A-Za-z0-9_-]{1,80}")
    private val imageEntry = Regex("assets/([A-Za-z0-9_-]{1,80})\\.img")

    fun list(): List<ProjectSummary> = root.listFiles().orEmpty()
        .filter { it.isDirectory && !it.name.startsWith("_incoming-") && safeId.matches(it.name) }
        .mapNotNull { dir ->
            runCatching { load(dir.name) }.getOrNull()?.let { project ->
                ProjectSummary(
                    project.id, project.name, project.updatedAt,
                    project.scenes.size, project.scenes.sumOf { it.entities.size },
                )
            }
        }.sortedByDescending { it.updatedAt }

    fun create(name: String, template: ProjectTemplate): GameProject =
        ProjectFactory.create(name, template).also(::save)

    fun load(id: String): GameProject {
        val file = File(projectDir(id), "project.json")
        if (file.length() > MAX_JSON_BYTES) throw IOException("Project is too large")
        val text = AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        val project = ProjectCodec.decode(text)
        if (project.id != id) throw IOException("Project ID does not match its folder")
        return project
    }

    fun save(project: GameProject) {
        val bytes = ProjectCodec.encode(project).toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_JSON_BYTES) throw IOException("Project is too large to save")
        val dir = projectDir(project.id).apply { mkdirs() }
        val atomic = AtomicFile(File(dir, "project.json"))
        val output = atomic.startWrite()
        try {
            output.write(bytes)
            atomic.finishWrite(output)
        } catch (error: Exception) {
            atomic.failWrite(output)
            throw error
        }
    }

    fun delete(id: String) {
        val dir = projectDir(id)
        if (dir.exists() && !dir.deleteRecursively()) throw IOException("Could not delete project")
    }

    fun assetFile(projectId: String, assetId: String): File {
        require(safeId.matches(assetId)) { "Invalid asset ID" }
        return File(projectDir(projectId), "assets/$assetId.img")
    }

    fun importImage(projectId: String, uri: Uri): ImageAsset {
        val assetId = ProjectFactory.id()
        val target = assetFile(projectId, assetId)
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "$assetId.part")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot read image")
            input.use { source ->
                FileOutputStream(temporary).use { destination -> copyBounded(source, destination, MAX_IMAGE_BYTES) }
            }
            validateImage(temporary)
            if (!temporary.renameTo(target)) throw IOException("Cannot store image")
            return ImageAsset(assetId, displayName(uri).take(160))
        } finally {
            temporary.delete()
        }
    }

    fun export(project: GameProject, uri: Uri) {
        ProjectCodec.validate(project)
        val output = context.contentResolver.openOutputStream(uri, "w") ?: throw IOException("Cannot write archive")
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("project.json"))
            zip.write(ProjectCodec.encode(project).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            writeImages(zip, project)
        }
    }

    /** Creates a standalone browser game: unzip and open index.html, or host the folder. */
    fun exportWeb(project: GameProject, uri: Uri) {
        ProjectCodec.validate(project)
        val json = ProjectCodec.encode(project)
        val template = context.assets.open("web/index.html").bufferedReader(Charsets.UTF_8).use { it.readText() }
        if (!template.contains("__SENGINE_PROJECT_DATA__")) throw IOException("Web player template is missing")
        // An escaped '<' cannot terminate the application/json script tag, even in user-entered text.
        val html = template.replace("__SENGINE_PROJECT_DATA__", json.replace("<", "\\u003c"))
        val output = context.contentResolver.openOutputStream(uri, "w") ?: throw IOException("Cannot write web game")
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("index.html"))
            zip.write(html.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            listOf("runtime.js", "styles.css").forEach { name ->
                zip.putNextEntry(ZipEntry(name))
                context.assets.open("web/$name").use { it.copyTo(zip) }
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("project.json"))
            zip.write(json.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            writeImages(zip, project)
        }
    }

    private fun writeImages(zip: ZipOutputStream, project: GameProject) {
        project.assets.forEach { asset ->
            val file = assetFile(project.id, asset.id)
            if (!file.isFile) throw IOException("Missing image: ${asset.name}")
            zip.putNextEntry(ZipEntry("assets/${asset.id}.img"))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    /** ZIP extraction is allowlisted and size-limited; no archive path is ever used as a filesystem path. */
    fun importArchive(uri: Uri): GameProject {
        val temp = File(root, "_incoming-${UUID.randomUUID()}").apply { mkdirs() }
        var finalDir: File? = null
        try {
            var json: String? = null
            val extracted = mutableSetOf<String>()
            var totalBytes = 0L
            var entries = 0
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot read archive")
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    if (entries > 260) throw IOException("Too many archive entries")
                    if (entry.isDirectory) {
                        if (entry.name != "assets/") throw IOException("Unexpected archive directory")
                    } else if (entry.name == "project.json") {
                        if (json != null) throw IOException("Duplicate project.json")
                        val buffer = java.io.ByteArrayOutputStream()
                        totalBytes += copyBounded(zip, buffer, MAX_JSON_BYTES)
                        json = buffer.toString(Charsets.UTF_8.name())
                    } else {
                        val assetId = imageEntry.matchEntire(entry.name)?.groupValues?.get(1)
                            ?: throw IOException("Unexpected archive entry")
                        if (!extracted.add(assetId)) throw IOException("Duplicate image entry")
                        val file = File(temp, "assets/$assetId.img")
                        file.parentFile?.mkdirs()
                        FileOutputStream(file).use { destination ->
                            totalBytes += copyBounded(zip, destination, MAX_IMAGE_BYTES)
                        }
                        validateImage(file)
                    }
                    if (totalBytes > MAX_ARCHIVE_BYTES) throw IOException("Archive is too large")
                    zip.closeEntry()
                }
            }
            val source = ProjectCodec.decode(json ?: throw IOException("No project.json in archive"))
            if (source.assets.map { it.id }.toSet() != extracted) throw IOException("Image list does not match archive")
            val imported = source.copy(
                id = ProjectFactory.id(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
            val destination = projectDir(imported.id)
            if (destination.exists() || !temp.renameTo(destination)) throw IOException("Cannot install project")
            finalDir = destination
            save(imported)
            return imported
        } catch (error: Exception) {
            finalDir?.deleteRecursively()
            throw error
        } finally {
            temp.deleteRecursively()
        }
    }

    private fun projectDir(id: String): File {
        require(safeId.matches(id)) { "Invalid project ID" }
        return File(root, id)
    }

    private fun validateImage(file: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth !in 1..4096 || bounds.outHeight !in 1..4096) {
            throw IOException("Use a PNG, JPEG or WebP image up to 4096 × 4096")
        }
    }

    private fun displayName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Imported image"

    private fun copyBounded(input: java.io.InputStream, output: java.io.OutputStream, max: Long): Long {
        var count = 0L
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            count += read
            if (count > max) throw IOException("File is too large")
            output.write(buffer, 0, read)
        }
        return count
    }

    private companion object {
        const val MAX_JSON_BYTES = 2_000_000L
        const val MAX_IMAGE_BYTES = 10_000_000L
        const val MAX_ARCHIVE_BYTES = 64_000_000L
    }
}
