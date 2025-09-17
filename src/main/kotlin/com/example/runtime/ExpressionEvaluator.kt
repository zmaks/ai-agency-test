package com.example.runtime

import com.fasterxml.jackson.databind.ObjectMapper
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value

/**
 * Unified evaluator that both resolves #references and evaluates boolean JS expressions.
 *
 * Rules:
 * - No special logic for #this: we only perform textual replacement '#this.' → '#<currentNodeId>.'
 * - References are of the form '#nodeId.output.deep.path[0].field' ('.output' may be omitted)
 * - Any resolution failure yields null; expression evaluation errors yield false.
 */
object ExpressionEvaluator {
    private val anyRefRegex = Regex("""#[A-Za-z_][A-Za-z0-9_\-]*(?:\.[A-Za-z0-9_\-\[\]]+)*""")

    fun evaluate(
        expression: String?,
        ctx: ExecutionContext,
        currentNodeId: String?,
        currentEnvelope: ResultEnvelope?
    ): Any? {
        if (expression.isNullOrBlank()) return null
        if (currentEnvelope == null || currentNodeId.isNullOrBlank()) return null

        // 1) Replace '#this.' with '#<currentNodeId>.' as requested
        var jsExpr = expression.replace("#this.", "#$currentNodeId.")

        // 2) Collect all #nodeId... references
        val refs: List<String> = anyRefRegex.findAll(jsExpr).map { it.value }.toSet().toList()

        // If expression is a single pure reference — return its resolved value directly
        val trimmedExpr = jsExpr.trim()
        if (refs.size == 1 && trimmedExpr == refs.first()) {
            return resolveReferenceString(refs.first(), ctx)
        }

        // 3) Replace occurrences in the expression with stable variable names
        val refVarNames: Map<String, String> = refs.mapIndexed { idx, ref -> ref to "ref$idx" }.toMap()
        for ((ref, varName) in refVarNames) {
            jsExpr = jsExpr.replace(ref, varName)
        }

        // If code contains statements/return, wrap into IIFE so 'return' works at top-level
        if (jsExpr.contains("return") || jsExpr.contains(';')) {
            jsExpr = "(function(){ ${jsExpr} })()"
        }

        return try {
            Context.newBuilder("js")
                .option("js.ecmascript-version", "2021")
                .allowAllAccess(true)
                .build().use { js ->
                    val bindings = js.getBindings("js")
                    // Bind all reference values
                    for ((ref, varName) in refVarNames) {
                        val value = resolveReferenceString(ref, ctx)
                        bindings.putMember(varName, toJsValue(js, value))
                    }
                    val result: Value = js.eval("js", jsExpr)
                    toKotlin(result)
                }
        } catch (_: Throwable) {
            null
        }
    }

    // --- Reference resolution (formerly ReferenceResolver) ---

    private fun resolveReferenceString(ref: String, ctx: ExecutionContext): Any? {
        if (!ref.startsWith("#")) return ref
        val trimmed = ref.trim()
        val tokens = splitReference(trimmed.substring(1))
        if (tokens.isEmpty()) return null

        // First token is node id
        val head = tokens.first()
        val tail = tokens.drop(1)

        val baseEnvelope: ResultEnvelope? = ctx.nodes[head]
        if (baseEnvelope == null) return null

        val (startValue, restTokens) = if (tail.firstOrNull() == "output") {
            baseEnvelope.output to tail.drop(1)
        } else {
            baseEnvelope.output to tail
        }
        return resolveDeepPath(startValue, restTokens)
    }

    private fun splitReference(expr: String): List<String> {
        val raw = expr.split('.')
        return raw.filter { it.isNotBlank() }
    }

    private fun resolveDeepPath(root: Any?, pathTokens: List<String>): Any? {
        var curr: Any? = root
        for (token in pathTokens) {
            if (curr == null) return null
            val (name, indices) = parseNameAndIndices(token)
            if (name.isNotEmpty()) {
                curr = accessProperty(curr, name)
            }
            for (idx in indices) {
                curr = accessIndex(curr, idx)
            }
        }
        return curr
    }

    private fun parseNameAndIndices(token: String): Pair<String, List<Int>> {
        var name = token
        val indices = mutableListOf<Int>()
        while (true) {
            val open = name.indexOf('[')
            if (open < 0) break
            val close = name.indexOf(']', startIndex = open + 1)
            if (close < 0) break
            val idxStr = name.substring(open + 1, close).trim()
            val idx = idxStr.toIntOrNull()
            if (idx != null) indices += idx
            name = name.removeRange(open, close + 1)
        }
        return name to indices
    }

    private fun accessProperty(target: Any?, prop: String): Any? {
        if (target == null) return null
        return when (target) {
            is Map<*, *> -> target[prop]
            is List<*> -> null
            else -> reflectProperty(target, prop)
        }
    }

    private fun accessIndex(target: Any?, index: Int): Any? {
        if (target == null) return null
        return when (target) {
            is List<*> -> target.getOrNull(index)
            is Array<*> -> target.getOrNull(index)
            is Map<*, *> -> target[index]
            else -> null
        }
    }

    private fun reflectProperty(target: Any, prop: String): Any? {
        val cls = target::class.java
        val cap = prop.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val candidates = listOf(
            "get${'$'}cap",
            "is${'$'}cap",
            prop
        )
        for (mName in candidates) {
            try {
                val m = cls.methods.firstOrNull { it.name == mName && it.parameterCount == 0 }
                if (m != null) return m.invoke(target)
            } catch (_: Exception) { }
        }
        return try {
            val f = cls.fields.firstOrNull { it.name == prop }
            f?.get(target)
        } catch (_: Exception) {
            null
        }
    }

    private fun toJsValue(ctx: Context, value: Any?): Any? {
        return value
    }

    private fun toKotlin(v: Value): Any? {
        return try {
            when {
                v.isNull -> null
                v.isBoolean -> v.asBoolean()
                v.isNumber -> v.asDouble()
                v.isString -> v.asString()
                v.isHostObject -> v.asHostObject<Any?>()
                v.hasArrayElements() -> {
                    val size = v.arraySize.toInt()
                    val list = ArrayList<Any?>(size)
                    for (i in 0 until size) list += toKotlin(v.getArrayElement(i.toLong()))
                    list
                }
                v.hasMembers() -> {
                    val map = linkedMapOf<String, Any?>()
                    for (k in v.memberKeys) {
                        val m = v.getMember(k)
                        // Skip functions if any
                        if (m.canExecute()) continue
                        map[k] = toKotlin(m)
                    }
                    map
                }
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }
}
