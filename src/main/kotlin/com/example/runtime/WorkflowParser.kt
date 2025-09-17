package com.example.runtime

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Minimal parser for Task 11.1 — turns a workflow JSON string into a Workflow model.
 */
object WorkflowParser {
    private val mapper: ObjectMapper = ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    fun parse(json: String): Workflow {
        require(json.isNotBlank()) { "workflow JSON must not be blank" }
        return mapper.readValue(json, Workflow::class.java)
    }
}
