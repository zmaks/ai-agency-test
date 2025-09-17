package com.example.runtime.executor

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.message.Attachment
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import kotlinx.coroutines.runBlocking


private fun attachStrategy() = strategy("single_run") {


    val nodeCallLLMParseAttach by node<AttachmentExtractRequest, Message.Response> { r ->
        storage.set(userQueryKey, r.userMessage!!)
        storage.set(outputSchemaKey, r.outputSchema)
        llm.writeSession {
            updatePrompt {
                user("RETURN ONLY FILE TEXT!") {
                    attachment(
                        Attachment.File(
                            content = AttachmentContent.Binary.Base64(r.fileData!!),
                            format = r.fileName!!.substringAfterLast("."),
                            r.mimeType!!,
                            r.fileName
                        )
                    )
                }
            }
            requestLLMWithoutTools()
        }
    }
    val nodeCallLLM by nodeLLMRequest(allowToolCalls = false)

    edge(nodeStart forwardTo nodeCallLLMParseAttach)
    edge(nodeCallLLMParseAttach forwardTo nodeCallLLM transformed { "Transform file content into JSON according to JSON schema and user query.\nUser query: " + storage.get(userQueryKey) + "\nJson Schema: " + storage.get(outputSchemaKey) + "\n\nFile content:\n" + it.content + "\n\nReturn ONLY VALID json that corresponds the schema. Example: {\"a\": 1, \"b\": \"test\"}" })
    edge(nodeCallLLM forwardTo nodeFinish)
}

fun extractorAgentRun(attach: AttachmentExtractRequest): String {
    return runBlocking {
        simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")).execute(
            prompt("") {
                system { "You are a file parsing tool. You MUST return only JSON according to json schema. Don't add anything from your side" }
                user("Transform file content into JSON according to JSON schema and user query.\nUser query: " + attach.userMessage + "\nJson Schema: " + attach.outputSchema + "\n\nReturn ONLY VALID json that corresponds the schema. Example: {\"a\": 1, \"b\": \"test\"}") {
                    attachment(
                        Attachment.File(
                            content = AttachmentContent.Binary.Base64(attach.fileData),
                            format = attach.fileName.substringAfterLast("."),
                            attach.mimeType,
                            attach.fileName
                        )
                    )
                }
            },
            model = OpenAIModels.Chat.GPT5Mini,

            )
    }.first().content
}

val extractorAgent = AIAgent(
    id = "extractor",
    promptExecutor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")),
    strategy = attachStrategy(),
    agentConfig = AIAgentConfig(
        prompt = prompt(
            id = "chat",
            params = LLMParams(
                temperature = 1.0,
                numberOfChoices = 1
            )
        ) {
            system("You are a helpful assistant. Answer user questions concisely.")
        },
        model = OpenAIModels.Chat.GPT5Mini,
        maxAgentIterations = 30,
    ),
    toolRegistry = ToolRegistry.EMPTY,
    installFeatures = { }
)

data class AttachmentExtractRequest(
    val userMessage: String,
    val outputSchema: String,
    val fileData: String,
    val fileName: String,
    val mimeType: String,
)

val userQueryKey = createStorageKey<String>("user query")
val outputSchemaKey = createStorageKey<String>("output schema")