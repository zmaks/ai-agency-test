package com.example

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertNotEquals

@SpringBootTest
@ActiveProfiles("local")
class LLMJsonServiceTest @Autowired constructor(
    private val service: LlmJsonService
) {

    private val json = "\"{\\\"name\\\":\\\"Extract Issue IDs from Attached PDF\\\",\\\"version\\\":\\\"1\\\",\\\"nodes\\\":[{\\\"id\\\":\\\"attachment_added\\\",\\\"type\\\":\\\"trigger\\\",\\\"name\\\":\\\"Attachment Added Trigger\\\",\\\"description\\\":\\\"Triggered when an attachment is added in YouTrack.\\\",\\\"next\\\":[{\\\"nextNodeId\\\":\\\"get_attachment\\\",\\\"relationDescription\\\":\\\"Pass attachment info to download file.\\\"}]},{\\\"id\\\":\\\"get_attachment\\\",\\\"type\\\":\\\"action\\\",\\\"name\\\":\\\"Download Attachment\\\",\\\"description\\\":\\\"Downloads the attached file from YouTrack.\\\",\\\"input\\\":{\\\"actionId\\\":\\\"get_attachment\\\",\\\"provider\\\":\\\"youtrack\\\",\\\"actionInput\\\":{\\\"issueId\\\":\\\"#attachment_added.output.event.issueId\\\",\\\"attachmentId\\\":\\\"#attachment_added.output.event.attachmentId\\\"}},\\\"next\\\":[{\\\"nextNodeId\\\":\\\"extract_issue_ids\\\",\\\"relationDescription\\\":\\\"Pass downloaded attachment for extraction.\\\"}]},{\\\"id\\\":\\\"extract_issue_ids\\\",\\\"type\\\":\\\"llm.extract\\\",\\\"name\\\":\\\"Extract Issue IDs from PDF\\\",\\\"description\\\":\\\"Extracts YouTrack issue identifiers from the downloaded PDF file using LLM.\\\",\\\"input\\\":{\\\"instructions\\\":\\\"Extract all YouTrack issue keys like ABC-123 from the provided document. Return strict JSON with an array of issue IDs under 'issueIds'.\\\",\\\"outputJsonSchema\\\":{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"issueIds\\\":{\\\"type\\\":\\\"array\\\",\\\"items\\\":{\\\"type\\\":\\\"string\\\"}}},\\\"required\\\":[\\\"issueIds\\\"]},\\\"mimeType\\\":\\\"#get_attachment.output.mimeType\\\",\\\"base64content\\\":\\\"#get_attachment.output.blobRef\\\",\\\"filename\\\":\\\"#get_attachment.output.filename\\\"}}]}]}\""

    @Test
    fun `should fix json`()  {
        val fixed = service.ensureValidJson(json)

        println(fixed)

        assertNotEquals(fixed, json)
    }
}