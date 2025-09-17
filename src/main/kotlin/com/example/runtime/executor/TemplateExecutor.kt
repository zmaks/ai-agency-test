package com.example.runtime.executor

import com.example.runtime.ErrorInfo
import com.example.runtime.ExecutionContext
import com.example.runtime.ExpressionEvaluator
import com.example.runtime.NodeDef
import com.example.runtime.NodeExecutor
import com.example.runtime.ResultEnvelope
import org.slf4j.LoggerFactory
import kotlin.collections.get


/**
 * Executor for nodes of type "template".
 *
 * Expected input shape (from node.input or node.params):
 * - template: String containing JS template literal placeholders like ${'$'}{args.something}
 * - args: Optional. Either a #reference string to another node's output or an object to expose to the template.
 *
 * Behavior:
 * - Resolves args when provided as a #reference using ExpressionEvaluator (as raw value).
 * - Evaluates and returns rendered string via JS engine using backtick template literal.
 */
class TemplateExecutor : NodeExecutor {
    private val logger = LoggerFactory.getLogger(TemplateExecutor::class.java)
    override fun execute(node: NodeDef, ctx: ExecutionContext): ResultEnvelope {
        logger.info("Executing node type='template' id='{}'", node.id)
        val input = node.input ?: node.params ?: emptyMap()
        val template = input["template"] as? String
            ?: run {
                logger.warn("Template node id='{}' missing 'template'", node.id)
                return ResultEnvelope(
                    error = ErrorInfo(
                        message = "template: missing 'template' string in input",
                        type = "BadInput"
                    ),
                    meta = mapOf("nodeId" to node.id, "type" to node.type)
                )
            }

        val argsRaw = input["args"]
        val argsObj: Any? = when (argsRaw) {
            is String -> if (argsRaw.startsWith("#")) {
                // Use a dummy envelope to satisfy evaluator preconditions
                ExpressionEvaluator.evaluate(argsRaw, ctx, node.id, ResultEnvelope(output = null))
            } else argsRaw
            else -> argsRaw
        }

        val rendered = try {
            renderTemplate(template, argsObj)
        } catch (e: Throwable) {
            logger.error("Template node id='{}' evaluation failed", node.id, e)
            return ResultEnvelope(
                error = ErrorInfo(
                    message = "template evaluation failed: ${'$'}{e.message}",
                    type = e::class.java.simpleName
                ),
                meta = mapOf("nodeId" to node.id, "type" to node.type)
            )
        }

        logger.debug("Template node id='{}' completed", node.id)
        return ResultEnvelope(
            output = rendered,
            meta = mapOf("nodeId" to node.id, "type" to node.type)
        )
    }
}

private fun renderTemplate(tpl: String, args: Any?): String {
    // Replace occurrences of ${args.path} with resolved values from args object.
    val regex = Regex("\\$\\{args\\.([^}]+)}")
    return regex.replace(tpl) { m ->
        val path = m.groupValues[1]
        val value = resolveFromArgs(args, path)
        value?.toString() ?: ""
    }
}

private fun resolveFromArgs(root: Any?, path: String): Any? {
    if (root == null) return null
    val tokens = path.split('.')
    var curr: Any? = root
    for (tok in tokens) {
        if (curr == null) return null
        val (name, indices) = parseNameAndIndicesForTemplate(tok)
        if (name.isNotEmpty()) {
            curr = when (curr) {
                is Map<*, *> -> curr[name]
                is List<*> -> null
                else -> null
            }
        }
        for (idx in indices) {
            curr = when (curr) {
                is List<*> -> curr.getOrNull(idx)
                is Array<*> -> curr.getOrNull(idx)
                is Map<*, *> -> curr[idx]
                else -> null
            }
        }
    }
    return curr
}

private fun parseNameAndIndicesForTemplate(token: String): Pair<String, List<Int>> {
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
