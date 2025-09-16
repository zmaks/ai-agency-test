package com.example

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/workflows")
class WorkflowController(
    private val chatGptService: WorkflowAiService
) {

    @PostMapping
    fun runWorkflow(@RequestBody request: WorkflowRequest): ResponseEntity<WorkflowResponse> {
        if (request.prompt.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(WorkflowResponse(error = "prompt must not be blank"))
        }
        return try {
            val result = chatGptService.chat(request)
            ResponseEntity.ok(WorkflowResponse(content = result.workflow))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(WorkflowResponse(error = e.message))
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(WorkflowResponse(error = e.message ?: "Unexpected error"))
        }
    }
}

data class WorkflowRequest(
    val prompt: String,
    val workflow: Map<Any, Any>? = null,
    val model: String = "gpt-4o-mini"
)

data class WorkflowResponse(
    val content: Map<*,*>? = null,
    val error: String? = null
)
