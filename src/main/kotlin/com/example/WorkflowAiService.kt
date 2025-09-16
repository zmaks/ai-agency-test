package com.example

import com.example.util.Resources
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Service

@Service
class WorkflowAiService(
    private val chatClientBuilder: ChatClient.Builder,
) {

    private val logger = LoggerFactory.getLogger(WorkflowAiService::class.java)

    // Default system prompt to enforce JSON-only workflow modifications
    private val defaultSystemPrompt: String = """
You are a workflow modification expert. You will receive a JSON workflow definition and a modification request. Your task is to return a modified workflow JSON that implements the requested changes.

CRITICAL: You must respond with ONLY valid JSON. Do not include any explanatory text, comments, or markdown formatting. Start your response with { and end with }.

The workflow follows this structure:
- nodes: Array of workflow steps with id, type, name, explain, config, inputs, outputs
- connections: Array of connections between nodes
- Each node has a type like "youtrack.trigger.attachmentAdded", "logic.filter", "pdf.extractText", etc.

Common node types:
- youtrack.*: YouTrack integration nodes
- logic.*: Logic operations (filter, condition)
- pdf.*: PDF processing
- text.*: Text processing
- array.*: Array operations
- email.*: Email operations
- slack.*: Slack integration

When modifying workflows:
1. Preserve the existing structure and IDs where possible
2. Add new nodes with unique IDs (nX_name format)
3. Update connections to include new nodes
4. Maintain the workflow's logical flow
5. Update node names and explanations to be user-friendly
6. Return ONLY the modified JSON workflow, no other text
7. Use next property for nodes to link them, don't add additional connections.
8. Follow the example strictly. 
9. Use only provided node types.

""".trim()

    private val workflowExample = Resources.read("workflow.json")
    private val nodes = Resources.read("nodes.json")
    private val actions = Resources.read("actions.json")

    fun generate(request: WorkflowRequest): WorkflowResponse {
        val prompt = request.prompt
        val workflow = request.workflow

        logger.info("Workflow prompt: $prompt")
        val client = chatClientBuilder.build()

        val systemPrelude = buildString {
            // System prompt
            append(defaultSystemPrompt)
            append("\n\n")
            // Nodes
            append("Available node types:\n")
            append(nodes)
            append("\n\n")
            // Actions
            append("Available actions:\n")
            append(actions)
            append("\n\n")
            // Existing or example
            if (workflow?.isNotBlank() == true) {
                append("Existing workflow:\n")
                append(workflow)
            } else {
                append("Example workflow JSON (for guidance):\n")
                append(workflowExample)
            }
        }.trim()

        val content = client.prompt()
            .system(systemPrelude)
            .user(prompt)
            .call()
            .content()
            ?: error("No response content")

        //Files.writeString(Path("local/response-${Instant.now()}.json"), content)

        logger.info("Workflow response:\n $content")

        return WorkflowResponse(content = content)
    }
}
