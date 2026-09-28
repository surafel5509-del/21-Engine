package com.sengine.core

import java.util.ArrayDeque
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import org.jbox2d.callbacks.ContactImpulse
import org.jbox2d.callbacks.ContactListener
import org.jbox2d.collision.Manifold
import org.jbox2d.collision.shapes.CircleShape
import org.jbox2d.collision.shapes.PolygonShape
import org.jbox2d.dynamics.Body
import org.jbox2d.dynamics.BodyDef
import org.jbox2d.dynamics.FixtureDef
import org.jbox2d.dynamics.World
import org.jbox2d.dynamics.contacts.Contact
import org.jbox2d.dynamics.BodyType as BoxBodyType
import org.jbox2d.common.Vec2 as BoxVec2

enum class EngineLogLevel { INFO, WARNING, ERROR, PHYSICS }

data class EngineLog(
    val level: EngineLogLevel,
    val message: String,
    val entityId: String? = null,
    val time: Float = 0f,
)

/** Fixed-step Box2D world with rotated fixtures, contacts, sensors, and sandboxed scripts. */
class WorldRunner(
    private val source: GameScene,
    scripts: List<ScriptAsset> = emptyList(),
) {
    private val world = World(BoxVec2(source.gravity.x / PIXELS_PER_METER, source.gravity.y / PIXELS_PER_METER))
    private val bodies = mutableMapOf<String, Body>()
    private val transforms = source.entities.associate { it.id to it.transform }.toMutableMap()
    private val entities = source.entities.associateBy { it.id }
    private val variables = mutableMapOf<String, MutableMap<String, Float>>()
    private val programs = scripts.associate { it.id to ScriptProgram.compile(it.source) }
    private val scriptNames = scripts.associate { it.id to it.name }
    private val messages = ArrayDeque<EngineLog>()
    private val contacts = mutableListOf<Pair<String, String>>()
    private val reportedFailures = mutableSetOf<String>()
    private var elapsed = 0f
    private var accumulator = 0f
    private var current = source

    val scene: GameScene get() = current
    val bodyCount: Int get() = bodies.size

    init {
        world.setContactListener(object : ContactListener {
            override fun beginContact(contact: Contact) {
                val first = contact.fixtureA.body.userData as? String ?: return
                val second = contact.fixtureB.body.userData as? String ?: return
                contacts += first to second
            }
            override fun endContact(contact: Contact) = Unit
            override fun preSolve(contact: Contact, oldManifold: Manifold) = Unit
            override fun postSolve(contact: Contact, impulse: ContactImpulse) = Unit
        })
        source.entities.forEach { entity ->
            val settings = entity.physics ?: return@forEach
            val t = entity.transform
            val definition = BodyDef().apply {
                type = when (settings.type) {
                    BodyType.STATIC -> BoxBodyType.STATIC
                    BodyType.DYNAMIC -> BoxBodyType.DYNAMIC
                    BodyType.KINEMATIC -> BoxBodyType.KINEMATIC
                }
                position.set(t.x / PIXELS_PER_METER, t.y / PIXELS_PER_METER)
                angle = radians(t.rotation)
                linearVelocity.set(settings.velocity.x / PIXELS_PER_METER, settings.velocity.y / PIXELS_PER_METER)
                gravityScale = settings.gravityScale
                linearDamping = settings.linearDamping
                fixedRotation = settings.fixedRotation
                active = entity.visible
                bullet = settings.type == BodyType.DYNAMIC && (t.width < 20f || t.height < 20f)
            }
            val body = world.createBody(definition)
            body.userData = entity.id
            val shape = when (settings.collider) {
                ColliderShape.CIRCLE -> ColliderShape.CIRCLE
                ColliderShape.BOX -> ColliderShape.BOX
                ColliderShape.AUTO -> if (entity.visual.type == VisualType.CIRCLE) ColliderShape.CIRCLE else ColliderShape.BOX
            }
            val fixture = FixtureDef().apply {
                this.shape = if (shape == ColliderShape.CIRCLE) {
                    CircleShape().apply { m_radius = (min(t.width, t.height) / 2f / PIXELS_PER_METER).coerceAtLeast(0.01f) }
                } else {
                    PolygonShape().apply { setAsBox(
                        (t.width / 2f / PIXELS_PER_METER).coerceAtLeast(0.01f),
                        (t.height / 2f / PIXELS_PER_METER).coerceAtLeast(0.01f),
                    ) }
                }
                density = settings.density
                friction = settings.friction
                restitution = settings.bounce
                isSensor = settings.sensor
            }
            body.createFixture(fixture)
            bodies[entity.id] = body
        }
        source.entities.forEach { entity ->
            val scriptId = entity.scriptId ?: return@forEach
            val program = programs[scriptId]
            if (program == null) {
                emit(EngineLogLevel.ERROR, "Script asset is missing", entity.id)
            } else {
                program.diagnostics.forEach { issue ->
                    emit(EngineLogLevel.ERROR, "${scriptNames[scriptId]}:${issue.line}: ${issue.message}", entity.id)
                }
            }
        }
        source.entities.forEach { runScript(it, ScriptEvent.START, 0f) }
        refreshScene()
    }

    /** Frame time is clamped; the solver never attempts unbounded catch-up after a pause. */
    fun advance(frameSeconds: Float): GameScene {
        if (!frameSeconds.isFinite() || frameSeconds <= 0f) return current
        accumulator += frameSeconds.coerceAtMost(0.1f)
        var steps = 0
        while (accumulator >= STEP && steps < 6) {
            stepOnce()
            accumulator -= STEP
            steps++
        }
        if (steps == 6) accumulator = 0f
        return current
    }

    /** Used by the editor's debugger when execution is paused. */
    fun stepOnce(): GameScene {
        elapsed += STEP
        source.entities.forEach { entity ->
            val start = entity.transform
            val animated = when (entity.motion.type) {
                MotionType.NONE -> null
                MotionType.SPIN -> transforms.getValue(entity.id).copy(rotation = start.rotation + elapsed * 360f * entity.motion.speed)
                MotionType.FLOAT -> transforms.getValue(entity.id).copy(y = start.y + wave(entity.motion.speed) * entity.motion.amplitude)
                MotionType.PATROL -> transforms.getValue(entity.id).copy(x = start.x + wave(entity.motion.speed) * entity.motion.amplitude)
            }
            if (animated != null) setTransform(entity.id, animated)
        }
        source.entities.forEach { runScript(it, ScriptEvent.UPDATE, STEP) }
        world.step(STEP, 8, 3)
        refreshScene()
        val began = contacts.toList()
        contacts.clear()
        began.forEach { (a, b) ->
            val first = entities[a]
            val second = entities[b]
            if (first != null && second != null) {
                emit(EngineLogLevel.PHYSICS, "${first.name} ↔ ${second.name}", first.id)
                runScript(first, ScriptEvent.COLLISION, STEP, second)
                runScript(second, ScriptEvent.COLLISION, STEP, first)
            }
        }
        refreshScene()
        return current
    }

    /** A script's tap handler takes precedence over the default upward impulse. */
    fun tap(worldX: Float, worldY: Float): Boolean {
        val hit = current.hitTest(worldX, worldY, includeLocked = true) ?: return false
        val script = hit.scriptId?.let(programs::get)
        if (script?.handles(ScriptEvent.TAP) == true) {
            runScript(hit, ScriptEvent.TAP, 0f)
            refreshScene()
            return true
        }
        val body = bodies[hit.id]?.takeIf { hit.physics?.type == BodyType.DYNAMIC } ?: return false
        body.setLinearVelocity(BoxVec2(body.linearVelocity.x, -470f / PIXELS_PER_METER))
        refreshScene()
        return true
    }

    fun drainLogs(): List<EngineLog> = buildList {
        while (messages.isNotEmpty()) add(messages.removeFirst())
    }

    private fun runScript(entity: Entity, event: ScriptEvent, dt: Float, other: Entity? = null) {
        val scriptId = entity.scriptId ?: return
        val program = programs[scriptId] ?: return
        if (!program.handles(event)) return
        try { program.execute(event, host(entity, dt, other)) }
        catch (error: ScriptExecutionException) {
            val key = "$scriptId:${error.line}:${error.message}"
            if (reportedFailures.add(key)) emit(EngineLogLevel.ERROR, "${scriptNames[scriptId]}:${error.line}: ${error.message}", entity.id)
        }
    }

    private fun host(entity: Entity, dt: Float, other: Entity?): ScriptHost = object : ScriptHost {
        private val locals = variables.getOrPut(entity.id) { mutableMapOf() }
        override fun read(name: String): Float {
            val body = bodies[entity.id]
            val transform = transforms.getValue(entity.id)
            return when (name) {
                "x" -> body?.position?.x?.times(PIXELS_PER_METER) ?: transform.x
                "y" -> body?.position?.y?.times(PIXELS_PER_METER) ?: transform.y
                "rotation" -> body?.angle?.let { Math.toDegrees(it.toDouble()).toFloat() } ?: transform.rotation
                "vx" -> body?.linearVelocity?.x?.times(PIXELS_PER_METER) ?: (locals[name] ?: 0f)
                "vy" -> body?.linearVelocity?.y?.times(PIXELS_PER_METER) ?: (locals[name] ?: 0f)
                "dt" -> dt
                "time" -> elapsed
                "other_x" -> other?.let { transforms[it.id]?.x } ?: 0f
                "other_y" -> other?.let { transforms[it.id]?.y } ?: 0f
                else -> locals[name] ?: 0f
            }
        }
        override fun write(name: String, value: Float) {
            require(value.isFinite() && abs(value) < 1_000_000f) { "Non-finite or out-of-range value" }
            val body = bodies[entity.id]
            val old = transforms.getValue(entity.id)
            when (name) {
                "x" -> setTransform(entity.id, old.copy(x = value))
                "y" -> setTransform(entity.id, old.copy(y = value))
                "rotation" -> setTransform(entity.id, old.copy(rotation = value))
                "vx", "vy" -> {
                    if (body != null) body.setLinearVelocity(BoxVec2(
                        if (name == "vx") value / PIXELS_PER_METER else body.linearVelocity.x,
                        if (name == "vy") value / PIXELS_PER_METER else body.linearVelocity.y,
                    )) else locals[name] = value
                }
                "dt", "time", "other_x", "other_y" -> throw IllegalArgumentException("$name is read-only")
                else -> {
                    require(name.length <= 32 && locals.size < 64 || name in locals) { "Too many local variables" }
                    locals[name] = value
                }
            }
        }
        override fun impulse(x: Float, y: Float) {
            require(x.isFinite() && y.isFinite() && abs(x) < 5000f && abs(y) < 5000f) { "Invalid impulse" }
            val body = bodies[entity.id] ?: return
            if (entity.physics?.type == BodyType.DYNAMIC) {
                val mass = body.mass
                body.applyLinearImpulse(BoxVec2(x / PIXELS_PER_METER * mass, y / PIXELS_PER_METER * mass), body.worldCenter)
            }
        }
        override fun log(message: String) { emit(EngineLogLevel.INFO, message.take(240), entity.id) }
    }

    private fun setTransform(id: String, value: Transform) {
        transforms[id] = value
        bodies[id]?.setTransform(BoxVec2(value.x / PIXELS_PER_METER, value.y / PIXELS_PER_METER), radians(value.rotation))
    }

    private fun refreshScene() {
        current = source.copy(entities = source.entities.map { entity ->
            val body = bodies[entity.id]
            val old = transforms.getValue(entity.id)
            if (body == null) entity.copy(transform = old) else {
                val moved = old.copy(
                    x = body.position.x * PIXELS_PER_METER,
                    y = body.position.y * PIXELS_PER_METER,
                    rotation = Math.toDegrees(body.angle.toDouble()).toFloat(),
                )
                transforms[entity.id] = moved
                entity.copy(transform = moved, physics = entity.physics?.copy(velocity = Vec2(
                    body.linearVelocity.x * PIXELS_PER_METER,
                    body.linearVelocity.y * PIXELS_PER_METER,
                )))
            }
        })
    }

    private fun emit(level: EngineLogLevel, message: String, entityId: String? = null) {
        if (messages.size == 500) messages.removeFirst()
        messages.addLast(EngineLog(level, message, entityId, elapsed))
    }

    private fun wave(speed: Float): Float = sin(elapsed.toDouble() * speed * 2 * PI).toFloat()
    private fun radians(degrees: Float): Float = Math.toRadians(degrees.toDouble()).toFloat()

    private companion object {
        const val PIXELS_PER_METER = 100f
        const val STEP = 1f / 60f
    }
}
