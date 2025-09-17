package com.example.runtime.executor

import com.example.runtime.*
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory

/**
 * Executor for nodes of type "js".
 *
 * Expected input:
 * - code: String (JS snippet). Typically contains a top-level `return ...;`.
 * - args: Optional. Either a #reference string to another node's output or an object to expose as `args`.
 *
 * Behavior:
 * - Resolves args when provided as a #reference using ExpressionEvaluator (as raw value).
 * - Constructs a JS program that defines `const args = <json>;` and then executes provided code.
 * - Uses ExpressionEvaluator to evaluate JavaScript, leveraging its reference resolver for any embedded #refs.
 * - Performs a minimal safety check (forbids obviously dangerous tokens). This is a best-effort PoC guardrail.
 */
class JsExecutor : NodeExecutor {
    private val logger = LoggerFactory.getLogger(JsExecutor::class.java)
    private val mapper: ObjectMapper = ObjectMapper()

    override fun execute(node: NodeDef, ctx: ExecutionContext): ResultEnvelope {
        logger.info("Executing node type='js' id='{}'", node.id)
        val input = node.input ?: node.params ?: emptyMap()
        val code = input["code"] as? String
            ?: run {
                logger.warn("JS node id='{}' missing 'code'", node.id)
                return ResultEnvelope(
                    error = ErrorInfo(
                        message = "js: missing 'code' string in input",
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

        // Basic guardrails: block known dangerous tokens/APIs for PoC
        val forbidden = listOf(
            "import", "require", "Packages", "Java", "Polyglot", "load",
            "eval(", "Function(", "this.constructor", "globalThis", "process", "JavaImporter"
        )
        if (forbidden.any { token -> code.contains(token, ignoreCase = true) }) {
            logger.warn("JS node id='{}' forbidden construct detected", node.id)
            return ResultEnvelope(
                error = ErrorInfo(
                    message = "js: forbidden construct detected in code",
                    type = "ForbiddenJs"
                ),
                meta = mapOf("nodeId" to node.id, "type" to node.type)
            )
        }

        val argsJson = try {
            mapper.writeValueAsString(argsObj)
        } catch (_: Exception) {
            // Fallback: args cannot be serialized nicely — expose as null
            "null"
        }

        val program = buildString {
            append("const args = ").append(argsJson).append(";\n")
            append(code.trim())
        }

        val result = try {
            // Evaluate via ExpressionEvaluator to benefit from #ref resolution if present
            ExpressionEvaluator.evaluate(program, ctx, node.id, ResultEnvelope(output = null))
        } catch (e: Throwable) {
            logger.error("JS node id='{}' evaluation failed", node.id, e)
            return ResultEnvelope(
                error = ErrorInfo(
                    message = "js evaluation failed: ${e.message}",
                    type = e::class.java.simpleName
                ),
                meta = mapOf("nodeId" to node.id, "type" to node.type)
            )
        }

        logger.debug("JS node id='{}' completed", node.id)
        return ResultEnvelope(
            output = result,
            meta = mapOf("nodeId" to node.id, "type" to node.type)
        )
    }
}
