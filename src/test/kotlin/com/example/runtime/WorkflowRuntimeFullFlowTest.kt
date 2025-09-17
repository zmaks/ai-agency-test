package com.example.runtime

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import java.nio.file.Files
import java.nio.file.Paths

@SpringBootTest
class WorkflowRuntimeFullFlowTest {

    @Test
    fun `full run on example workflow without trigger executes all nodes including add_comment`() {
        // Load example workflow JSON from structures and parse
        val baseDir = Paths.get("").toAbsolutePath()
        val path = baseDir.resolve("structures/workflow-example.json")
        assertTrue(Files.exists(path), "Workflow example JSON should exist at: ${'$'}path")
        val json = Files.readString(path)
        val wf = WorkflowParser.parse(json)

        // Remove trigger node (as required by the task)
        val nodesWithoutTrigger = wf.nodes.filter { !it.type.equals("trigger", ignoreCase = true) }
        val wfNoTrigger = Workflow().apply { nodes = nodesWithoutTrigger }

        // Run the workflow
        val ctxIn = ExecutionContext(options = RunOptions(debugIncludeResolvedInputs = true))
        val result = WorkflowRuntime.run(wfNoTrigger, ctxIn)
        val visited = result.visited
        println("[DEBUG_LOG] visited=" + visited)

        // Expect that the flow goes through all nodes including the final add_comment
        // Verify one-by-one without loops or containsAll
        val idxPick = visited.indexOf("pick_ids")
        assertTrue(idxPick >= 0, "Node pick_ids should be visited. Visited=$visited")

        val idxGet = visited.indexOf("get_attachment")
        assertTrue(idxGet >= 0, "Node get_attachment should be visited. Visited=$visited")
        assertTrue(idxGet > idxPick, "get_attachment should come after pick_ids. Visited=$visited")

        val idxExtract = visited.indexOf("mentions_extract")
        assertTrue(idxExtract >= 0, "Node mentions_extract should be visited. Visited=$visited")
        assertTrue(idxExtract > idxGet, "mentions_extract should come after get_attachment. Visited=$visited")

        val idxHas = visited.indexOf("has_mentions")
        assertTrue(idxHas >= 0, "Node has_mentions should be visited. Visited=$visited")
        assertTrue(idxHas > idxExtract, "has_mentions should come after mentions_extract. Visited=$visited")

        val idxCommentText = visited.indexOf("comment_text")
        assertTrue(idxCommentText >= 0, "Node comment_text should be visited. Visited=$visited")
        assertTrue(idxCommentText > idxHas, "comment_text should come after has_mentions. Visited=$visited")

        val idxAddComment = visited.indexOf("add_comment")
        assertTrue(idxAddComment >= 0, "Node add_comment should be visited. Visited=$visited")
        assertTrue(idxAddComment > idxCommentText, "add_comment should come after comment_text. Visited=$visited")

        // Additionally ensure add_comment node was executed (present in context)
        val ctx = result.context
        assertTrue(ctx.nodes.containsKey("add_comment"), "add_comment node should be executed")

        // And that we had no fatal errors that stopped the flow
        // (Individual node outputs may still be null/ok=false for stubs, which is fine)
        assertTrue(result.startNodeId == "pick_ids")
    }
}
