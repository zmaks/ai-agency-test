package com.example.runtime

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.util.ArrayDeque

/**
 * Task 11.11 — Execution Engine loop (queue, visited set, next + invokeCondition evaluation,
 * termination, error policy).
 */
object WorkflowRuntime {
    private val logger = LoggerFactory.getLogger(WorkflowRuntime::class.java)

    data class RunResult(
        val context: ExecutionContext,
        val workflow: Workflow,
        val startNodeId: String?,
        val visited: List<String>
    )

    fun run(workflowJson: String, ctx: ExecutionContext): RunResult {
        val wf = WorkflowParser.parse(workflowJson)
        return run(wf, ctx)
    }

    fun run(workflow: Workflow, ctx: ExecutionContext): RunResult {
        val registry = NodeRegistry.default()

        if (workflow.nodes.isEmpty()) {
            logger.info("Workflow has no nodes; nothing to execute")
            return RunResult(ctx, workflow, startNodeId = null, visited = emptyList())
        }

        // Determine start node: prefer first trigger, else first node
        val startNode = workflow.nodes.firstOrNull { it.type.equals("trigger", ignoreCase = true) }
            ?: workflow.nodes.first()
        logger.info("Starting workflow run at node id='{}' type='{}'", startNode.id, startNode.type)

        val byId = workflow.nodes.associateBy { it.id }
        val queue: ArrayDeque<String> = ArrayDeque()
        val visited = linkedSetOf<String>()
        queue.add(startNode.id)

        while (queue.isNotEmpty()) {
            val nodeId = queue.removeFirst()
            val node = byId[nodeId]
            if (node == null) {
                // Unknown node id in queue — skip
                logger.warn("Queue contained unknown node id='{}'; skipping", nodeId)
                continue
            }
            if (visited.contains(nodeId)) {
                ctx.logNodeSkipped(nodeId, reason = "already visited")
                logger.debug("Node id='{}' already visited; skipping", nodeId)
                continue
            }

            // Optionally resolve inputs for debug
            val resolvedInputsForDebug = try { resolveInputsForDebug(node, ctx) } catch (_: Exception) { null }
            val logEntry = ctx.logNodeStart(nodeId, resolvedInputsForDebug)
            val inputSummary = summarizeForLog(resolvedInputsForDebug)
            logger.info("Executing node id='{}' type='{}' input={} ", node.id, node.type, inputSummary)

            val envelope: ResultEnvelope = try {
                val exec = registry.get(node.type!!)
                exec.execute(node, ctx)
            } catch (t: Throwable) {
                ResultEnvelope(
                    error = ErrorInfo(
                        message = t.message ?: t.toString(),
                        type = t::class.java.simpleName,
                        stackTrace = t.stackTraceToString()
                    ),
                    meta = mapOf("nodeId" to node.id, "type" to node.type)
                )
            }

            ctx.setNodeResult(nodeId, envelope)
            ctx.logNodeEnd(nodeId, envelope, logEntry)

            if (envelope.error == null) {
                logger.info(
                    "Node completed id='{}' type='{}' output={}",
                    node.id,
                    node.type,
                    summarizeForLog(envelope.output)
                )
            } else {
                logger.warn(
                    "Node error id='{}' type='{}' error='{}: {}'",
                    node.id,
                    node.type,
                    envelope.error.type ?: "Error",
                    envelope.error.message
                )
            }

            visited += nodeId

            // Stop on any node error
            if (envelope.error != null) {
                logger.info("Stopping workflow due to node error")
                break
            }

            // Evaluate next edges
            val edges = node.next ?: emptyList()
            var anySelected = false
            for (edge in edges) {
                val pass: Boolean = when (val cond = edge.invokeCondition) {
                    null, "" -> true
                    else -> {
                        val res = ExpressionEvaluator.evaluate(cond, ctx, currentNodeId = nodeId, currentEnvelope = envelope)
                        res as? Boolean ?: false
                    }
                }
                if (pass) {
                    val nextId = edge.nextNodeId
                    if (!visited.contains(nextId) && nextId!!.isNotBlank()) {
                        anySelected = true
                        logger.info(
                            "Next node selected from id='{}' -> nextId='{}' condition='{}'",
                            node.id,
                            nextId,
                            edge.invokeCondition ?: "<none>"
                        )
                        queue.add(nextId)
                    } else {
                        logger.debug("Next candidate '{}' skipped (visited or blank)", nextId)
                    }
                } else {
                    logger.debug(
                        "Edge condition evaluated to false at node id='{}' condition='{}'",
                        node.id,
                        edge.invokeCondition ?: "<none>"
                    )
                }
            }
            if (!anySelected && edges.isNotEmpty()) {
                logger.info("No next node selected from id='{}' — branch ends here", node.id)
            }
        }

        logger.info("Workflow finished. Start='{}' Visited={} nodes", startNode.id, visited.size)
        return RunResult(ctx, workflow, startNodeId = startNode.id, visited = visited.toList())
    }

    private fun resolveInputsForDebug(node: NodeDef, ctx: ExecutionContext): Any? {
        val inMap = node.input ?: node.params ?: return null
        fun resolveValue(v: Any?): Any? = when (v) {
            is String -> if (v.startsWith("#")) {
                // Use dummy envelope to allow evaluator; it only needs ctx
                ExpressionEvaluator.evaluate(v, ctx, currentNodeId = node.id, currentEnvelope = ResultEnvelope(output = null))
            } else v
            is Map<*, *> -> v.entries.associate { (k, vv) -> k.toString() to resolveValue(vv) }
            is List<*> -> v.map { resolveValue(it) }
            else -> v
        }
        return resolveValue(inMap)
    }

    // Keep log payloads readable and bounded
    private val summarizeMapper by lazy { ObjectMapper() }
    private fun summarizeForLog(value: Any?, maxLen: Int = 500): String? {
        if (value == null) return null
        val text = try {
            when (value) {
                is String -> value
                else -> summarizeMapper.writeValueAsString(value)
            }
        } catch (_: Exception) {
            value.toString()
        }
        return if (text.length <= maxLen) text else text.take(maxLen) + "…"
    }
}
