package com.example

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("local")
class WorkflowAiServiceTest @Autowired constructor(
    private val service: WorkflowAiService
) {

    @Test
    fun `should generate new workflow`() {
        // Given
        val request = WorkflowRequest(
            prompt = "Workflow to extract issue ids from attached pdf and add comment to the original issue with extracted issue IDs",
        )

        // When
        val response = service.generate(request)

        // Then
        assertTrue { response.content?.isNotBlank() == true }
    }
}
