package com.sengine.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Deliberately bounded, offline script language. No JVM reflection, filesystem, or network access. */
enum class ScriptEvent { START, UPDATE, TAP, COLLISION }

data class ScriptDiagnostic(val line: Int, val message: String)

class ScriptExecutionException(val line: Int, message: String) : RuntimeException(message)

/** Implemented by the physics world. A host owns persistent local variables for one entity. */
interface ScriptHost {
    fun read(name: String): Float
    fun write(name: String, value: Float)
    fun impulse(x: Float, y: Float)
    fun log(message: String)
    /** Request a scene by its name or ID; the game host applies it after this frame. */
    fun changeScene(reference: String)
}

class ScriptProgram private constructor(
    private val handlers: Map<ScriptEvent, List<Statement>>,
    val diagnostics: List<ScriptDiagnostic>,
) {
    fun handles(event: ScriptEvent): Boolean = diagnostics.isEmpty() && handlers.containsKey(event)

    fun execute(event: ScriptEvent, host: ScriptHost) {
        if (diagnostics.isNotEmpty()) return
        var remaining = 256
        fun run(statements: List<Statement>) {
            statements.forEach { statement ->
                if (--remaining < 0) throw ScriptExecutionException(statement.line, "Instruction budget exceeded")
                try {
                    when (statement) {
                        is Statement.If -> run(if (statement.test.eval(host) != 0f) statement.thenBody else statement.elseBody)
                        is Statement.Assign -> {
                            val value = statement.expr.eval(host)
                            host.write(statement.name, if (statement.add) host.read(statement.name) + value else value)
                        }
                        is Statement.Move -> {
                            host.write("x", host.read("x") + statement.x.eval(host))
                            host.write("y", host.read("y") + statement.y.eval(host))
                        }
                        is Statement.Velocity -> {
                            host.write("vx", statement.x.eval(host))
                            host.write("vy", statement.y.eval(host))
                        }
                        is Statement.Impulse -> host.impulse(statement.x.eval(host), statement.y.eval(host))
                        is Statement.Rotate -> host.write("rotation", host.read("rotation") + statement.angle.eval(host))
                        is Statement.Scene -> host.changeScene(statement.reference)
                        is Statement.Print -> host.log(statement.text ?: statement.expr!!.eval(host).toString())
                    }
                } catch (error: ScriptExecutionException) { throw error }
                catch (error: Exception) {
                    throw ScriptExecutionException(statement.line, error.message ?: "Script execution failed")
                }
            }
        }
        run(handlers[event].orEmpty())
    }

    companion object {
        fun compile(source: String): ScriptProgram = ScriptParser(source).parse()
    }

    private sealed class Statement(open val line: Int) {
        data class If(val test: Expr, val thenBody: List<Statement>, val elseBody: List<Statement>, override val line: Int) : Statement(line)
        data class Assign(val name: String, val expr: Expr, val add: Boolean, override val line: Int) : Statement(line)
        data class Move(val x: Expr, val y: Expr, override val line: Int) : Statement(line)
        data class Velocity(val x: Expr, val y: Expr, override val line: Int) : Statement(line)
        data class Impulse(val x: Expr, val y: Expr, override val line: Int) : Statement(line)
        data class Rotate(val angle: Expr, override val line: Int) : Statement(line)
        data class Scene(val reference: String, override val line: Int) : Statement(line)
        data class Print(val text: String?, val expr: Expr?, override val line: Int) : Statement(line)
    }

    private sealed interface Expr {
        fun eval(host: ScriptHost): Float

        data class Literal(val value: Float) : Expr { override fun eval(host: ScriptHost) = value }
        data class Variable(val name: String) : Expr { override fun eval(host: ScriptHost) = host.read(name) }
        data class Unary(val sign: String, val part: Expr) : Expr {
            override fun eval(host: ScriptHost): Float = when (sign) {
                "-" -> -part.eval(host)
                "!" -> if (part.eval(host) == 0f) 1f else 0f
                else -> part.eval(host)
            }
        }
        data class Binary(val op: String, val left: Expr, val right: Expr) : Expr {
            override fun eval(host: ScriptHost): Float {
                val a = left.eval(host)
                if (op == "and" && a == 0f) return 0f
                if (op == "or" && a != 0f) return 1f
                val b = right.eval(host)
                return when (op) {
                    "+" -> a + b
                    "-" -> a - b
                    "*" -> a * b
                    "/" -> if (abs(b) < 0.000001f) 0f else a / b
                    "<" -> bool(a < b)
                    ">" -> bool(a > b)
                    "<=" -> bool(a <= b)
                    ">=" -> bool(a >= b)
                    "==" -> bool(a == b)
                    "!=" -> bool(a != b)
                    "and" -> bool(b != 0f)
                    "or" -> bool(b != 0f)
                    else -> 0f
                }
            }
        }
        data class Call(val name: String, val arguments: List<Expr>) : Expr {
            override fun eval(host: ScriptHost): Float {
                val a = arguments.map { it.eval(host) }
                return when (name) {
                    "sin" -> sin(a[0])
                    "cos" -> cos(a[0])
                    "abs" -> abs(a[0])
                    "min" -> min(a[0], a[1])
                    "max" -> max(a[0], a[1])
                    "clamp" -> a[0].coerceIn(min(a[1], a[2]), max(a[1], a[2]))
                    else -> 0f
                }
            }
        }
    }

    private class ScriptParser(source: String) {
        private data class SourceLine(val number: Int, val content: String)
        private val sourceSize = source.length
        private val lines = source.lines().mapIndexedNotNull { index, raw ->
            val trimmed = raw.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#") || trimmed.startsWith("//")) null
            else SourceLine(index + 1, trimmed)
        }
        private val issues = mutableListOf<ScriptDiagnostic>()
        private val handlers = mutableMapOf<ScriptEvent, List<Statement>>()
        private var position = 0

        fun parse(): ScriptProgram {
            if (sourceLengthTooLarge()) return ScriptProgram(emptyMap(), listOf(ScriptDiagnostic(1, "Script exceeds 16,000 characters")))
            while (position < lines.size) {
                val line = lines[position++]
                val name = line.content.removePrefix("on ").trim().uppercase()
                val event = if (line.content.startsWith("on ")) ScriptEvent.entries.firstOrNull { it.name == name } else null
                if (event == null) {
                    issues += ScriptDiagnostic(line.number, "Expected 'on start', 'on update', 'on tap', or 'on collision'")
                    continue
                }
                if (event in handlers) issues += ScriptDiagnostic(line.number, "Duplicate event handler")
                val body = block(depth = 0)
                if (body.terminator != "end") issues += ScriptDiagnostic(line.number, "Event needs a closing 'end'")
                handlers[event] = body.statements
            }
            if (handlers.isEmpty() && issues.isEmpty()) issues += ScriptDiagnostic(1, "Add at least one 'on ...' event block")
            return ScriptProgram(handlers, issues.take(30))
        }

        // The model limit is also checked here so a draft can be validated before it is saved.
        private fun sourceLengthTooLarge(): Boolean = sourceSize > 16_000

        private data class Block(val statements: List<Statement>, val terminator: String?)

        private fun block(depth: Int): Block {
            val instructions = mutableListOf<Statement>()
            if (depth > 8) {
                issues += ScriptDiagnostic(lines.getOrNull(position - 1)?.number ?: 1, "If nesting exceeds 8 levels")
                // Stop descending; even a hostile 16 KB input cannot overflow the parser stack.
                return Block(emptyList(), null)
            }
            while (position < lines.size) {
                val line = lines[position++]
                if (line.content == "end" || line.content == "else") return Block(instructions, line.content)
                if (line.content.startsWith("if ")) {
                    val condition = expression(line.content.substring(3), line.number)
                    val thenBlock = block(depth + 1)
                    val elseBlock = if (thenBlock.terminator == "else") block(depth + 1) else Block(emptyList(), thenBlock.terminator)
                    if (elseBlock.terminator != "end") issues += ScriptDiagnostic(line.number, "If needs a closing 'end'")
                    if (condition != null) instructions += Statement.If(condition, thenBlock.statements, elseBlock.statements, line.number)
                    continue
                }
                val instruction = try { statement(line) }
                catch (error: IllegalArgumentException) {
                    issues += ScriptDiagnostic(line.number, error.message ?: "Invalid statement")
                    null
                }
                if (instruction != null) instructions += instruction
                if (instructions.size > 256) {
                    issues += ScriptDiagnostic(line.number, "A block cannot contain more than 256 statements")
                    break
                }
            }
            return Block(instructions, null)
        }

        private fun statement(line: SourceLine): Statement? {
            val text = line.content
            val verb = text.substringBefore(' ').lowercase()
            val rest = text.substringAfter(' ', "").trim()
            if (rest.isEmpty()) throw IllegalArgumentException("$verb needs arguments")
            return when (verb) {
                "move", "velocity", "impulse" -> {
                    val args = splitArguments(rest)
                    require(args.size == 2) { "$verb expects two comma-separated expressions" }
                    val x = expression(args[0], line.number) ?: return null
                    val y = expression(args[1], line.number) ?: return null
                    when (verb) {
                        "move" -> Statement.Move(x, y, line.number)
                        "velocity" -> Statement.Velocity(x, y, line.number)
                        else -> Statement.Impulse(x, y, line.number)
                    }
                }
                "rotate" -> expression(rest, line.number)?.let { Statement.Rotate(it, line.number) }
                "scene" -> {
                    require(rest.length <= 100 && rest.none { it.isISOControl() }) { "Use a scene name or ID (up to 100 characters)" }
                    Statement.Scene(rest, line.number)
                }
                "set", "let", "add" -> {
                    val match = Regex("^([A-Za-z_][A-Za-z0-9_]*)\\s*(?:=\\s*|\\s+)(.+)$").matchEntire(rest)
                        ?: throw IllegalArgumentException("Use '$verb name = expression'")
                    val name = match.groupValues[1]
                    require(name !in setOf("dt", "time")) { "$name is read-only" }
                    expression(match.groupValues[2], line.number)?.let { Statement.Assign(name, it, verb == "add", line.number) }
                }
                "log" -> {
                    if (rest.startsWith('"')) {
                        require(rest.length >= 2 && rest.endsWith('"')) { "Close the log string with a quote" }
                        Statement.Print(rest.substring(1, rest.length - 1).replace("\\n", "\n").replace("\\\"", "\""), null, line.number)
                    } else expression(rest, line.number)?.let { Statement.Print(null, it, line.number) }
                }
                else -> throw IllegalArgumentException("Unknown command '$verb'")
            }
        }

        private fun expression(text: String, line: Int): Expr? = try { ExpressionParser(text).parse() }
        catch (error: IllegalArgumentException) {
            issues += ScriptDiagnostic(line, error.message ?: "Invalid expression")
            null
        }

        private fun splitArguments(text: String): List<String> {
            var depth = 0
            val parts = mutableListOf<String>()
            var start = 0
            text.forEachIndexed { index, char ->
                if (char == '(') depth++
                if (char == ')') depth--
                if (char == ',' && depth == 0) { parts += text.substring(start, index).trim(); start = index + 1 }
            }
            parts += text.substring(start).trim()
            return parts
        }
    }

    private class ExpressionParser(private val source: String) {
        private val lexeme = Regex("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+|[A-Za-z_][A-Za-z0-9_]*|==|!=|<=|>=|&&|\\|\\||[+*/()<>!,\\-])")
        private val tokens: List<String>
        private var index = 0
        private var depth = 0

        init {
            val found = mutableListOf<String>()
            var cursor = 0
            while (cursor < source.length) {
                if (source[cursor].isWhitespace()) { cursor++; continue }
                val match = lexeme.find(source, cursor)
                require(match != null && match.range.first == cursor) { "Unexpected character in expression" }
                found += match.value
                cursor = match.range.last + 1
                require(found.size <= 128) { "Expression is too long" }
            }
            tokens = found
        }

        fun parse(): Expr {
            require(tokens.isNotEmpty()) { "Expected an expression" }
            val result = or()
            require(index == tokens.size) { "Unexpected token '${tokens[index]}'" }
            return result
        }

        private fun or(): Expr {
            var left = and()
            while (peek() in listOf("or", "||")) { take(); left = Expr.Binary("or", left, and()) }
            return left
        }
        private fun and(): Expr {
            var left = comparison()
            while (peek() in listOf("and", "&&")) { take(); left = Expr.Binary("and", left, comparison()) }
            return left
        }
        private fun comparison(): Expr {
            var left = sum()
            while (peek() in listOf("<", ">", "<=", ">=", "==", "!=")) {
                val op = take(); left = Expr.Binary(op, left, sum())
            }
            return left
        }
        private fun sum(): Expr {
            var left = product()
            while (peek() == "+" || peek() == "-") { val op = take(); left = Expr.Binary(op, left, product()) }
            return left
        }
        private fun product(): Expr {
            var left = unary()
            while (peek() == "*" || peek() == "/") { val op = take(); left = Expr.Binary(op, left, unary()) }
            return left
        }
        private fun unary(): Expr = when (peek()) {
            "-", "+", "!" -> Expr.Unary(take(), unary())
            else -> atom()
        }
        private fun atom(): Expr {
            require(++depth < 32) { "Expression nesting exceeds 32 levels" }
            try {
                val token = take()
                if (token == "(") {
                    val expression = or()
                    expect(")")
                    return expression
                }
                token.toFloatOrNull()?.let { return Expr.Literal(it) }
                require(token.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) { "Expected a number or variable" }
                if (peek() != "(") return Expr.Variable(token)
                take()
                val args = mutableListOf<Expr>()
                if (peek() != ")") {
                    args += or()
                    while (peek() == ",") { take(); args += or() }
                }
                expect(")")
                val arity = mapOf("sin" to 1, "cos" to 1, "abs" to 1, "min" to 2, "max" to 2, "clamp" to 3)[token]
                require(arity != null && args.size == arity) { "Unknown function or wrong number of arguments: $token" }
                return Expr.Call(token, args)
            } finally { depth-- }
        }
        private fun peek(): String? = tokens.getOrNull(index)
        private fun take(): String = tokens.getOrNull(index++) ?: throw IllegalArgumentException("Incomplete expression")
        private fun expect(expected: String) { require(take() == expected) { "Expected '$expected'" } }
    }
}

private fun bool(value: Boolean): Float = if (value) 1f else 0f
