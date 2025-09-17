package com.example.runtime.executor

import com.example.SpringContext
import com.example.runtime.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import java.util.*

/**
 * Executor for nodes of type "llm.extract".
 *
 * Inputs:
 * - instructions: String
 * - outputJsonSchema: Object (shape for result JSON)
 * - Either `text` OR (`mimeType` + `base64content` [+ `filename`])
 *
 * Behavior (PoC):
 * - If `text` provided, use as rawText.
 * - Else, if base64content provided and mimeType indicates PDF or known binary, call Koog with
 *   a system message to return only file text and an attachment built from base64content.
 * - Then call Koog again to extract JSON per schema using instructions. If no OPENAI_API_KEY is present,
 *   use a deterministic stub extractor.
 *
 * Output:
 * { result: <object>, rawText: <string>?, meta: { model, mode, notes? } }
 */
class LlmExtractExecutor : NodeExecutor {
    private val logger = LoggerFactory.getLogger(LlmExtractExecutor::class.java)

    private fun err(node: NodeDef, message: String): ResultEnvelope {
        logger.warn("llm.extract node id='{}' bad input: {}", node.id, message)
        return ResultEnvelope(
            error = ErrorInfo(message = message, type = "BadInput"),
            meta = mapOf("nodeId" to node.id, "type" to node.type)
        )
    }
    private val mapper: ObjectMapper = ObjectMapper()

    override fun execute(node: NodeDef, ctx: ExecutionContext): ResultEnvelope {
        logger.info("Executing node type='{}' id='{}'", node.type, node.id)
        val input = node.input ?: node.params ?: emptyMap()

        val instructions = (input["instructions"] as? String)?.trim()
            ?: return err(node, "llm.extract: missing 'instructions'")
        val schemaObj: Any = input["outputJsonSchema"]
            ?: return err(node, "llm.extract: missing 'outputJsonSchema'")
        val schema = mapper.writeValueAsString(schemaObj)

        val textRaw = input["text"]
        val text: String? = when (textRaw) {
            is String -> if (textRaw.startsWith("#")) {
                ExpressionEvaluator.evaluate(textRaw, ctx, node.id, ResultEnvelope(output = null))?.toString()
            } else textRaw
            else -> null
        }

        val base64Raw = input["base64content"]
        val base64content: String? = when (base64Raw) {
            is String -> if (base64Raw.startsWith("#")) {
                ExpressionEvaluator.evaluate(base64Raw, ctx, node.id, ResultEnvelope(output = null))?.toString()
            } else base64Raw
            else -> null
        }
        val mimeType: String? = (resolveIfRef(input["mimeType"], ctx, node) as? String)
        val filename: String? = (resolveIfRef(input["filename"], ctx, node) as? String)

        val forceStub: Boolean = (resolveIfRef(input["forceStub"], ctx, node) as? Boolean) == true

        // 1) Obtain raw text to analyze
        val rawText: String = when {
            !text.isNullOrBlank() -> text
            !base64content.isNullOrBlank() -> {
                // Try to decode as UTF-8 text; if fails, keep stub text placeholder
                decodeBase64ToString(base64content) ?: "BINARY_CONTENT(${mimeType ?: "application/octet-stream"})"
            }
            else -> ""
        }

        // 2) Extract structured JSON
        val apiKey = System.getenv("SPRING_AI_OPENAI_APIKEY")
        val resultObject: Any?
        val meta = mutableMapOf<String, Any?>(
            "nodeId" to node.id,
            "type" to node.type
        )

        try {

            val chatModel = SpringContext.getBean(ChatModel::class.java)
            val system = SystemMessage(
                "You are an information extraction assistant. Given instructions and a JSON schema, produce ONLY valid minified JSON that matches the schema. Do not include any extra commentary."
            )
            val user = UserMessage(
                """
                    Instructions:
                    ${instructions}
                    JSON Schema (shape, not strict):
                    ${schema}
                    Text to analyze:
                    ${rawText}
                    """.trimIndent()
            )
            val prompt = Prompt(listOf(system, user))
            // Fallback to simple string call to avoid API differences
            val modelOutput = chatModel.call(system.text + "\n\n" + user.text)

            // Parse JSON strictly; if fails, wrap error
            resultObject = try {
                mapper.readValue(modelOutput, JsonNode::class.java)
            } catch (e: Exception) {
                logger.error("llm.extract node id='{}' invalid model JSON. output: {}", node.id, modelOutput, e)
                return ResultEnvelope(
                    error = ErrorInfo(
                        message = "llm.extract: model did not return valid JSON: ${e.message}",
                        type = "BadModelOutput"
                    ),
                    meta = meta(node, mode = "llm-json-parse-error")
                )
            }
            meta["mode"] = if (forceStub || apiKey.isNullOrBlank()) "stub" else "llm"
            meta["model"] = if (forceStub || apiKey.isNullOrBlank()) "stub" else "spring-ai:openai"
        } catch (e: Throwable) {
            logger.error("llm.extract node id='{}' extraction failed", node.id, e)
            return ResultEnvelope(
                error = ErrorInfo(
                    message = "llm.extract: extraction failed: ${e.message}",
                    type = e::class.java.simpleName
                ),
                meta = meta(node, mode = "llm-extract-failed")
            )
        }

        val out = linkedMapOf<String, Any?>(
            "result" to resultObject,
            "rawText" to rawText,
            "meta" to meta
        )
        logger.debug("llm.extract node id='{}' completed mode='{}'", node.id, meta["mode"])
        return ResultEnvelope(
            output = out,
            meta = meta(node, mode = meta["mode"]?.toString())
        )
    }

    private fun resolveIfRef(v: Any?, ctx: ExecutionContext, node: NodeDef): Any? = when (v) {
        is String -> if (v.startsWith("#")) ExpressionEvaluator.evaluate(v, ctx, node.id, ResultEnvelope(output = null)) else v
        else -> v
    }

    private fun decodeBase64ToString(b64: String): String? = try {
        val bytes = Base64.getDecoder().decode(b64)
        String(bytes, Charsets.UTF_8)
    } catch (_: Throwable) { null }

    private fun extFromMime(mimeType: String): String = when (mimeType.lowercase(Locale.getDefault())) {
        "application/pdf" -> "pdf"
        "text/plain" -> "txt"
        else -> "txt"
    }

    private fun meta(node: NodeDef, mode: String?): Map<String, Any?> = mapOf(
        "nodeId" to node.id,
        "type" to node.type,
        "mode" to mode
    )

    private fun containsSchemaProperty(schema: Map<*, *>, key: String): Boolean {
        val props = schema["properties"]
        return when (props) {
            is Map<*, *> -> props.containsKey(key)
            else -> false
        }
    }
}
