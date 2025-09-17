package com.example.runtime.executor

import com.example.runtime.ErrorInfo
import com.example.runtime.ExecutionContext
import com.example.runtime.ExpressionEvaluator
import com.example.runtime.NodeDef
import com.example.runtime.NodeExecutor
import com.example.runtime.ResultEnvelope
import org.slf4j.LoggerFactory

/**
 * ActionProvider abstraction to support pluggable provider adapters.
 */
interface ActionProvider {
    /**
     * Provider identifier, e.g. "youtrack".
     */
    fun providerId(): String

    /**
     * Returns available actions for this provider. Minimal PoC shape:
     * - id: String (action id as used by nodes)
     * - name: String
     * - description: String
     */
    fun listActions(): List<Map<String, Any?>>

    /**
     * Runs an action returning a result object (or empty map if no output).
     */
    fun run(actionId: String, input: Map<String, Any?>): Any?
}

/**
 * First provider implementation — YouTrackActionProvider.
 * For the PoC, supports two hardcoded actions used in workflow-example.json:
 * - get_attachment(issueId, attachmentId) → { blobRef, mimeType, filename }
 * - add_comment(issueId, text) → { ok: true, id: "comment-1" }
 */
class YouTrackActionProvider : ActionProvider {
    override fun providerId(): String = "youtrack"

    override fun listActions(): List<Map<String, Any?>> = listOf(
        mapOf(
            "id" to "get_attachment",
            "name" to "Get Attachment",
            "description" to "Downloads the attachment and returns blobRef, mimeType, filename."
        ),
        mapOf(
            "id" to "add_comment",
            "name" to "Add Comment",
            "description" to "Posts the generated comment to the issue."
        )
    )

    override fun run(actionId: String, input: Map<String, Any?>): Any? {
        return when (actionId) {
            "get_attachment" -> {
                // Expect: issueId, attachmentId (ignored in stub)
                val issueId = input["issueId"]?.toString() ?: ""
                val attachmentId = input["attachmentId"]?.toString() ?: ""
                // Return a deterministic stub payload
                val sampleText = "See ABC-123 and XYZ-3 in this document"
                val base64 = java.util.Base64.getEncoder().encodeToString(sampleText.toByteArray())
                mapOf(
                    "blobRef" to base64,
                    "mimeType" to "application/pdf",
                    "filename" to "attachment.pdf"
                )
            }
            "add_comment" -> {
                // Expect: issueId, text
                val issueId = input["issueId"]?.toString() ?: ""
                val text = input["text"]?.toString() ?: ""
                mapOf(
                    "ok" to (issueId.isNotBlank() && text.isNotBlank()),
                    "id" to "comment-1"
                )
            }
            else -> {
                mapOf(
                    "ok" to false,
                    "error" to "Unknown actionId: ${'$'}actionId"
                )
            }
        }
    }
}

/**
 * Executor for nodes of type "action".
 *
 * Expected input shape (from node.input or node.params):
 * - provider: String (e.g., "youtrack")
 * - actionId: String (e.g., "get_attachment")
 * - actionInput: Object — may contain #references to previous nodes
 */
class ActionExecutor(
    providers: List<ActionProvider>
) : NodeExecutor {

    private val logger = LoggerFactory.getLogger(ActionExecutor::class.java)
    private val providersById: Map<String, ActionProvider> = providers.associateBy { it.providerId().lowercase() }

    override fun execute(node: NodeDef, ctx: ExecutionContext): ResultEnvelope {
        logger.info("Executing node type='action' id='{}'", node.id)
        val input = node.input ?: node.params ?: emptyMap()
        val providerId = (input["provider"] ?: node.provider)?.toString()
            ?: return ResultEnvelope(
                error = ErrorInfo(
                    message = "action: missing 'provider'",
                    type = "BadInput"
                ),
                meta = meta(node, providerId = null, actionId = null)
            )
        val actionId = (input["actionId"] ?: node.actionId)?.toString()
            ?: return ResultEnvelope(
                error = ErrorInfo(
                    message = "action: missing 'actionId'",
                    type = "BadInput"
                ),
                meta = meta(node, providerId = providerId, actionId = null)
            )

        val provider = providersById[providerId.lowercase()]
            ?: run {
                logger.warn("Action node id='{}' provider not found: '{}'", node.id, providerId)
                return ResultEnvelope(
                    error = ErrorInfo(
                        message = "action: provider '${'$'}providerId' not found",
                        type = "ProviderMissing"
                    ),
                    meta = meta(node, providerId = providerId, actionId = actionId)
                )
            }

        val rawActionInput = input["actionInput"]
        val resolvedActionInput = when (rawActionInput) {
            is Map<*, *> -> resolveRefs(rawActionInput as Map<String, Any?>, ctx, node.id!!)
            null -> emptyMap()
            else -> mapOf("value" to rawActionInput)
        }

        return try {
            val result = provider.run(actionId, resolvedActionInput)
            logger.debug("Action node id='{}' completed provider='{}' actionId='{}'", node.id, providerId, actionId)
            ResultEnvelope(
                output = result,
                meta = meta(node, providerId = providerId, actionId = actionId)
            )
        } catch (e: Throwable) {
            logger.error("Action node id='{}' failed provider='{}' actionId='{}'", node.id, providerId, actionId, e)
            ResultEnvelope(
                error = ErrorInfo(
                    message = "action run failed: ${'$'}{e.message}",
                    type = e::class.java.simpleName
                ),
                meta = meta(node, providerId = providerId, actionId = actionId)
            )
        }
    }

    private fun resolveRefs(obj: Map<String, Any?>, ctx: ExecutionContext, currentNodeId: String): Map<String, Any?> {
        fun resolve(value: Any?): Any? = when (value) {
            is String -> if (value.startsWith("#")) {
                // Provide a dummy envelope to allow ExpressionEvaluator to work
                ExpressionEvaluator.evaluate(value, ctx, currentNodeId, ResultEnvelope(output = null))
            } else value
            is Map<*, *> -> value.entries.associate { (k, v) -> k.toString() to resolve(v) }
            is List<*> -> value.map { resolve(it) }
            else -> value
        }
        return obj.entries.associate { (k, v) -> k to resolve(v) }
    }

    private fun meta(node: NodeDef, providerId: String?, actionId: String?): Map<String, Any?> = mapOf(
        "nodeId" to node.id,
        "type" to node.type,
        "provider" to providerId,
        "actionId" to actionId
    )
}
