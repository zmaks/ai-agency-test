package com.example

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.beans.factory.annotation.Autowired
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.test.assertTrue

@SpringBootTest
class WorkflowAiServiceTest @Autowired constructor(
    private val service: WorkflowAiService
) {

    @Test
    fun `should generate new workflow`() {
        // Given
        val request = WorkflowRequest(
            prompt = "Workflow to add a comment with all changes when issue changed",
            workflow = null,
            model = "test-model"
        )

        // When
        val response = service.generate(request)

        // Then
        assertTrue { response.content?.isNotBlank() == true }
    }
}
