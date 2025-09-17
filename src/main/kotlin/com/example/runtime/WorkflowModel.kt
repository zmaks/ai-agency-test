package com.example.runtime

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// Minimal data model for Task 11.1: parsing workflow JSON into Kotlin classes.
// The model aims to be tolerant to slightly different shapes found in the examples
// (e.g., both "input" and "params" keys on nodes).

@JsonIgnoreProperties(ignoreUnknown = true)
class Workflow {
    var name: String? = null
    var version: String? = null
    var nodes: List<NodeDef> = emptyList()
}

@JsonIgnoreProperties(ignoreUnknown = true)
class NodeDef {
    var id: String? = null
    var type: String? = null
    var name: String? = null
    var description: String? = null

    // Two possible input containers across examples
    var input: Map<String, Any?>? = null
    var params: Map<String, Any?>? = null

    // Some examples put actionId/provider at node root
    var actionId: String? = null
    var provider: String? = null
    var next: List<Edge>? = null
}

@JsonIgnoreProperties(ignoreUnknown = true)
class Edge {
    var nextNodeId: String?  = null
    var relationDescription: String? = null
    var invokeCondition: String? = null
}
