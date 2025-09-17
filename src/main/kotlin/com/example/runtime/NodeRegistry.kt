package com.example.runtime

import com.example.runtime.executor.ActionExecutor
import com.example.runtime.executor.JsExecutor
import com.example.runtime.executor.LlmExtractExecutor
import com.example.runtime.executor.TemplateExecutor
import com.example.runtime.executor.YouTrackActionProvider

/**
 * Task 11.5 — NodeRegistry and base NodeExecutor interface.
 */
interface NodeExecutor {
    fun execute(node: NodeDef, ctx: ExecutionContext): ResultEnvelope
}

class NodeRegistry private constructor(
    private val executors: MutableMap<String, NodeExecutor>
) {
    fun register(type: String, executor: NodeExecutor): NodeRegistry {
        executors[type.lowercase()] = executor
        return this
    }

    fun get(type: String): NodeExecutor {
        val key = type.lowercase()
        return executors[key] ?: MissingExecutor(key)
    }

    companion object {
        fun default(): NodeRegistry {
            val reg = NodeRegistry(mutableMapOf())
            // Register known types with stub executors for now
            reg.register("trigger", StubNotImplementedExecutor("trigger"))
            reg.register("js", JsExecutor())
            reg.register("action", ActionExecutor(listOf(YouTrackActionProvider())))
            reg.register("llm.extract", LlmExtractExecutor())
            reg.register("llm.call", StubNotImplementedExecutor("llm.call"))
            reg.register("template", TemplateExecutor())
            reg.register("exit", StubExitExecutor())
            return reg
        }
    }
}

private class MissingExecutor(private val type: String) : NodeExecutor {
    override fun execute(node: NodeDef, ctx: ExecutionContext): ResultEnvelope {
        return ResultEnvelope(
            error = ErrorInfo(
                message = "No executor registered for node type '$type'",
                type = "ExecutorMissing"
            ),
            meta = mapOf(
                "nodeId" to node.id,
                "type" to node.type
            )
        )
    }
}

private class StubNotImplementedExecutor(private val type: String) : NodeExecutor {
    override fun execute(node: NodeDef, ctx: ExecutionContext): ResultEnvelope {
        return ResultEnvelope(
            output = null,
            error = ErrorInfo(
                message = "Executor for type '$type' not implemented",
                type = "NotImplemented"
            ),
            meta = mapOf(
                "nodeId" to node.id,
                "type" to node.type
            )
        )
    }
}

private class StubExitExecutor : NodeExecutor {
    override fun execute(node: NodeDef, ctx: ExecutionContext): ResultEnvelope {
        return ResultEnvelope(
            output = mapOf("ok" to true),
            meta = mapOf(
                "nodeId" to node.id,
                "type" to node.type,
                "note" to "exit stub"
            )
        )
    }
}
