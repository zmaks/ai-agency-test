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

    override fun listActions(): List<Map<String, Any?>> {
        // Load actions from classpath resource and expose only required fields
        // id, provider, description, inputSchema, outputSchema
        return try {
            val json = com.example.util.Resources.read("actions.json")
            val mapper = com.fasterxml.jackson.databind.ObjectMapper()
            val typeRef = object : com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Any?>>>() {}
            val all: List<Map<String, Any?>> = mapper.readValue(json, typeRef)
            all.filter { (it["provider"] as? String)?.lowercase() == providerId() }
                .map { a ->
                    mapOf(
                        "id" to a["id"],
                        "provider" to a["provider"],
                        "description" to a["description"],
                        "inputSchema" to a["inputSchema"],
                        "outputSchema" to a["outputSchema"]
                    )
                }
        } catch (e: Exception) {
            // Fallback to minimal hardcoded stubs if resource is missing
            listOf(
                mapOf(
                    "id" to "add_issue_comment",
                    "provider" to providerId(),
                    "description" to "Adds a new comment to the specified issue. Supports Markdown.",
                    "inputSchema" to null,
                    "outputSchema" to null
                )
            )
        }
    }

    override fun run(actionId: String, input: Map<String, Any?>): Any? {
        return when (actionId) {
            "get_attachment" -> {
                // Expect: issueId, attachmentId (ignored in stub)
                val issueId = input["issueId"]?.toString() ?: ""
                val attachmentId = input["attachmentId"]?.toString() ?: ""
                // Return a deterministic stub payload
                val sampleText = "See ABC-123 and XYZ-3 in this document"
                val base64 = "JVBERi0xLjQKJdPr6eEKMSAwIG9iago8PC9UaXRsZSAoVW50aXRsZWQgZG9jdW1lbnQgLSBHb29nbGUgRG9jcykKL0NyZWF0b3IgKE1vemlsbGEvNS4wIFwoTWFjaW50b3NoOyBJbnRlbCBNYWMgT1MgWCAxMF8xNV83XCkgQXBwbGVXZWJLaXQvNTM3LjM2IFwoS0hUTUwsIGxpa2UgR2Vja29cKSBDaHJvbWUvMTM5LjAuMC4wIFNhZmFyaS81MzcuMzYpCi9Qcm9kdWNlciAoU2tpYS9QREYgbTEzOSkKL0NyZWF0aW9uRGF0ZSAoRDoyMDI1MDkxNDE5MDEyNiswMCcwMCcpCi9Nb2REYXRlIChEOjIwMjUwOTE0MTkwMTI2KzAwJzAwJyk+PgplbmRvYmoKMyAwIG9iago8PC9jYSAxCi9CTSAvTm9ybWFsPj4KZW5kb2JqCjUgMCBvYmoKPDwvRmlsdGVyIC9GbGF0ZURlY29kZQovTGVuZ3RoIDQxMD4+IHN0cmVhbQp4nM2UTUvEMBCG7/kVcxZMZ5JMMoFScD8qHhTUgmdZ3QVxV3ddwZ8vTZft+hGx4MH2Mp2HzDuTvKk2NqYHEBCO9cFniAZmS7VWLTHMCNZEryUwbO7VzRGs1FpZTYbT2j6aLRVB+16dQhdsFqo4tbB4UcXF0+p6u3mdbaEsi/Px2QQQqmo0GScZIQ+E7FuFecpgW6ULNgs1alRx/Xy7KsviZLZ9vX1s7t+2UNbTujZoJhVUFbS1itoBOe25nUWgmSvqBiSIrULUgQN5B81SlYhoK2ge1PS866K5gxLRhJQk1Cw+BqaeSCJRhyAYye2BxW6J0YZIxHJPTEdIO7RIB8WoW+O0CIlQ6EGnL5rYkth9ftes0xgsku0B+wSCtiZG8X4P3Oj7Smwyefk+7+qUt9qwj+FA2Q1t1XFGIaOcL5RRzo5GfySc3eys8s8d/WDr8d7WufZa406bv7odJmpiQ8H/69sx2CnjjHmzJ5k/+5ADA89+sK2zQ7i4A4JC3vNnMGA/XNbZQ38Lu336lbW/NvjR25fqMoXvS2ZyuwplbmRzdHJlYW0KZW5kb2JqCjIgMCBvYmoKPDwvVHlwZSAvUGFnZQovUmVzb3VyY2VzIDw8L1Byb2NTZXQgWy9QREYgL1RleHQgL0ltYWdlQiAvSW1hZ2VDIC9JbWFnZUldCi9FeHRHU3RhdGUgPDwvRzMgMyAwIFI+PgovRm9udCA8PC9GNCA0IDAgUj4+Pj4KL01lZGlhQm94IFswIDAgNjEyIDc5Ml0KL0NvbnRlbnRzIDUgMCBSCi9TdHJ1Y3RQYXJlbnRzIDAKL1RhYnMgL1MKL1BhcmVudCA2IDAgUj4+CmVuZG9iago2IDAgb2JqCjw8L1R5cGUgL1BhZ2VzCi9Db3VudCAxCi9LaWRzIFsyIDAgUl0+PgplbmRvYmoKNyAwIG9iago8PC8gWzIgMCBSIC9YWVogMCA3OTIgMF0+PgplbmRvYmoKMTIgMCBvYmoKPDwvVHlwZSAvU3RydWN0RWxlbQovUyAvTm9uU3RydWN0Ci9QIDExIDAgUgovUGcgMiAwIFIKL0sgMD4+CmVuZG9iagoxMSAwIG9iago8PC9UeXBlIC9TdHJ1Y3RFbGVtCi9TIC9EaXYKL1AgMTAgMCBSCi9LIDEyIDAgUj4+CmVuZG9iagoxMCAwIG9iago8PC9UeXBlIC9TdHJ1Y3RFbGVtCi9TIC9Ob25TdHJ1Y3QKL1AgOSAwIFIKL0sgMTEgMCBSPj4KZW5kb2JqCjkgMCBvYmoKPDwvVHlwZSAvU3RydWN0RWxlbQovUyAvRG9jdW1lbnQKL0xhbmcgKGVuKQovUCA4IDAgUgovSyAxMCAwIFI+PgplbmRvYmoKMTMgMCBvYmoKWzEyIDAgUl0KZW5kb2JqCjE0IDAgb2JqCjw8L1R5cGUgL1BhcmVudFRyZWUKL051bXMgWzAgMTMgMCBSXT4+CmVuZG9iago4IDAgb2JqCjw8L1R5cGUgL1N0cnVjdFRyZWVSb290Ci9LIDkgMCBSCi9QYXJlbnRUcmVlTmV4dEtleSAxCi9QYXJlbnRUcmVlIDE0IDAgUj4+CmVuZG9iagoxNSAwIG9iago8PC9UeXBlIC9DYXRhbG9nCi9QYWdlcyA2IDAgUgovRGVzdHMgNyAwIFIKL01hcmtJbmZvIDw8L1R5cGUgL01hcmtJbmZvCi9NYXJrZWQgdHJ1ZT4+Ci9TdHJ1Y3RUcmVlUm9vdCA4IDAgUgovVmlld2VyUHJlZmVyZW5jZXMgPDwvVHlwZSAvVmlld2VyUHJlZmVyZW5jZXMKL0Rpc3BsYXlEb2NUaXRsZSB0cnVlPj4KL0xhbmcgKGVu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MDAwMCA2NTUzNSBmIAowMDAwMDAwMDE1IDAwMDAwIG4gCjAwMDAwMDA4MjYgMDAwMDAgbiAKMDAwMDAwMDMwOSAwMDAwMCBuIAowMDAwMDE2MDQ1IDAwMDAwIG4gCjAwMDAwMDAzNDYgMDAwMDAgbiAKMDAwMDAwMTA0MyAwMDAwMCBuIAowMDAwMDAxMDk4IDAwMDAwIG4gCjAwMDAwMDE1MTQgMDAwMDAgbiAKMDAwMDAwMTM1NCAwMDAwMCBuIAowMDAwMDAxMjgzIDAwMDAwIG4gCjAwMDAwMDEyMTcgMDAwMDAgbiAKMDAwMDAwMTE0MCAwMDAwMCBuIAowMDAwMDAxNDM0IDAwMDAwIG4gCjAwMDAwMDE0NTkgMDAwMDAgbiAKMDAwMDAwMTYwNCAwMDAwMCBuIAowMDAwMDAxODExIDAwMDAwIG4gCjAwMDAwMTQ5ODUgMDAwMDAgbiAKMDAwMDAxNTIyMSAwMDAwMCBuIAowMDAwMDE1NjY3IDAwMDAwIG4gCnRyYWlsZXIKPDwvU2l6ZSAyMAovUm9vdCAxNSAwIFIKL0luZm8gMSAwIFI+PgpzdGFydHhyZWYKMTYxODQKJSVFT0YK"//java.util.Base64.getEncoder().encodeToString(sampleText.toByteArray())
                val filename = "attachment-$attachmentId.pdf"
                mapOf(
                    "blobRef" to base64,
                    "mimeType" to "application/pdf",
                    "filename" to filename
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
