package com.example

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Service

@Service
class LlmJsonService(
    private val chatClientBuilder: ChatClient.Builder,
    private val objectMapper: ObjectMapper,
) {
    private val logger = org.slf4j.LoggerFactory.getLogger(LlmJsonService::class.java)

    fun ensureValidJson(content: String, attempt: Int = 1): String {
        if (attempt > 3) error("Failed to parse response after $attempt attempts")
        try {
            objectMapper.readValue(content, Map::class.java)
            return content
        } catch (e: Exception) {
            logger.warn("Failed to parse response, attempt $attempt", e)
            val json = fixJson(content)
            return ensureValidJson(json, attempt + 1)
        }
    }

    private fun fixJson(content: String): String {
        val client = chatClientBuilder.build()
        return client.prompt()
            .system("Fix given json. RETURN ONLY VALID JSON")
            .user(content)
            .call()
            .content()
            ?: error("No response content")
    }
}