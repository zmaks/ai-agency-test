package com.example.runtime

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant

/**
 * Task 11.2 — Core runtime primitives: ExecutionContext, ResultEnvelope, and logging helpers.
 */

@JsonIgnoreProperties(ignoreUnknown = true)
data class ResultEnvelope(
    val output: Any? = null,
    val meta: Map<String, Any?>? = null,
    val error: ErrorInfo? = null
) {
    val ok: Boolean get() = error == null
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class ErrorInfo(
    val message: String,
    val type: String? = null,
    val stackTrace: String? = null,
    val cause: String? = null
)

enum class NodeRunStatus { OK, ERROR, SKIPPED }

@JsonIgnoreProperties(ignoreUnknown = true)
data class ExecutionLogEntry(
    val nodeId: String,
    val startedAt: Instant = Instant.now(),
    var finishedAt: Instant? = null,
    var durationMs: Long? = null,
    var status: NodeRunStatus? = null,
    var outputSummary: String? = null,
    var errorSummary: String? = null,
    var resolvedInputDebug: Any? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RunOptions(
    val stopOnError: Boolean = false,
    val debugIncludeResolvedInputs: Boolean = false,
)

/**
 * Shared execution context for a single workflow run.
 * - nodes: per-node envelopes accessible as ctx.nodes[nodeId]
 * - vars: general-purpose variables
 * - event: optional seed event for triggers
 * - errors: quick index of node errors
 * - logs: append-only execution log entries
 */
class ExecutionContext(
    val options: RunOptions = RunOptions(),
    val event: Any? = null
) {
    val nodes: MutableMap<String, ResultEnvelope> = linkedMapOf()
    val errors: MutableMap<String, ErrorInfo> = linkedMapOf()
    val logs: MutableList<ExecutionLogEntry> = mutableListOf()

    // local mapper for summaries
    private val mapper: ObjectMapper = ObjectMapper()

    fun logNodeStart(nodeId: String, resolvedInputDebug: Any? = null): ExecutionLogEntry {
        val entry = ExecutionLogEntry(nodeId = nodeId, resolvedInputDebug = if (options.debugIncludeResolvedInputs) resolvedInputDebug else null)
        logs += entry
        return entry
    }

    fun logNodeEnd(nodeId: String, envelope: ResultEnvelope, logEntry: ExecutionLogEntry? = null) {
        val entry = logEntry ?: logs.lastOrNull { it.nodeId == nodeId } ?: ExecutionLogEntry(nodeId)
        if (logEntry == null) logs += entry
        entry.finishedAt = Instant.now()
        entry.durationMs = entry.finishedAt!!.toEpochMilli() - entry.startedAt.toEpochMilli()
        if (envelope.error == null) {
            entry.status = NodeRunStatus.OK
            entry.outputSummary = summarize(envelope.output)
        } else {
            entry.status = NodeRunStatus.ERROR
            entry.errorSummary = "${envelope.error.type ?: "Error"}: ${envelope.error.message}"
            errors[nodeId] = envelope.error
        }
    }

    fun logNodeSkipped(nodeId: String, reason: String? = null) {
        val entry = ExecutionLogEntry(nodeId = nodeId)
        entry.finishedAt = Instant.now()
        entry.durationMs = entry.finishedAt!!.toEpochMilli() - entry.startedAt.toEpochMilli()
        entry.status = NodeRunStatus.SKIPPED
        entry.outputSummary = reason?.let { "skipped: $it" }
        logs += entry
    }

    fun setNodeResult(nodeId: String, envelope: ResultEnvelope) {
        nodes[nodeId] = envelope
        if (envelope.error != null) errors[nodeId] = envelope.error
    }

    fun traceDump(maxLines: Int = 200): String {
        return buildString {
            append("Execution trace (entries=").append(logs.size).append(")\n")
            logs.takeLast(maxLines).forEach { e ->
                append("- [").append(e.status ?: "?").append("] ")
                append(e.nodeId).append(" ")
                append("(").append(e.durationMs ?: 0).append("ms) ")
                e.outputSummary?.let { append("out=\"").append(it).append("\" ") }
                e.errorSummary?.let { append("err=\"").append(it).append("\" ") }
                if (options.debugIncludeResolvedInputs && e.resolvedInputDebug != null) {
                    append("args=").append(summarize(e.resolvedInputDebug)).append(" ")
                }
                append("\n")
            }
        }
    }

    private fun summarize(value: Any?, maxLen: Int = 500): String? {
        if (value == null) return null
        val text = try {
            when (value) {
                is String -> value
                else -> mapper.writeValueAsString(value)
            }
        } catch (_: Exception) {
            value.toString()
        }
        return if (text.length <= maxLen) text else text.take(maxLen) + "…"
    }
}
