package com.example

import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/workflows")
class WorkflowController(
    private val chatGptService: WorkflowAiService
) {

    @PostMapping
    fun runWorkflow(@RequestBody request: WorkflowRequest): WorkflowResponse {
        return chatGptService.generate(request)
    }
}

data class WorkflowRequest(
    val prompt: String,
    val workflow: String? = null,
    val model: String? = null
)

data class WorkflowResponse(
    val content: String? = null,
    val error: String? = null
)
