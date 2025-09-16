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

    private val json = "\"{\\\"name\\\":\\\"Extract Issue IDs from PDF Attachment\\\",\\\"version\\\":\\\"1.0\\\",\\\"nodes\\\":[{\\\"id\\\":\\\"attachment_added\\\",\\\"type\\\":\\\"trigger\\\",\\\"name\\\":\\\"YouTrack Attachment Added Trigger\\\",\\\"description\\\":\\\"Triggered when an attachment is added to a YouTrack issue.\\\",\\\"next\\\":[{\\\"nextNodeId\\\":\\\"pick_ids\\\",\\\"relationDescription\\\":\\\"Pass event data to extract issue and attachment IDs\\\"}]},{\\\"id\\\":\\\"pick_ids\\\",\\\"type\\\":\\\"js\\\",\\\"name\\\":\\\"Extract Issue and Attachment IDs\\\",\\\"description\\\":\\\"Extracts issueId and attachmentId from the event payload.\\\",\\\"input\\\":{\\\"code\\\":\\\"return { issueId: inputs.event.issueId, attachmentId: inputs.event.attachment.id };\\\",\\\"args\\\":\\\"#attachment_added.output.event\\\"},\\\"next\\\":[{\\\"nextNodeId\\\":\\\"get_attachment\\\",\\\"relationDescription\\\":\\\"Use IDs to download attachment\\\"}]},{\\\"id\\\":\\\"get_attachment\\\",\\\"type\\\":\\\"action\\\",\\\"name\\\":\\\"YouTrack: Get Attachment\\\",\\\"description\\\":\\\"Fetches the file of the added attachment.\\\",\\\"input\\\":{\\\"actionId\\\":\\\"get_attachment\\\",\\\"provider\\\":\\\"youtrack\\\",\\\"actionInput\\\":{\\\"issueId\\\":\\\"#pick_ids.output.issueId\\\",\\\"attachmentId\\\":\\\"#pick_ids.output.attachmentId\\\"}},\\\"next\\\":[{\\\"nextNodeId\\\":\\\"extract_issue_ids\\\",\\\"relationDescription\\\":\\\"Provide downloaded file for issue ID extraction\\\"}]},{\\\"id\\\":\\\"extract_issue_ids\\\",\\\"type\\\":\\\"llm.extract\\\",\\\"name\\\":\\\"Extract Issue IDs from PDF\\\",\\\"description\\\":\\\"Extracts YouTrack issue keys from the PDF content.\\\",\\\"input\\\":{\\\"instructions\\\":\\\"Extract all YouTrack issue keys like ABC-123 from the attached file. Return a JSON object with a single field 'issues' listing all found issue keys as an array of strings.\\\",\\\"outputJsonSchema\\\":{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"issues\\\":{\\\"type\\\":\\\"array\\\",\\\"items\\\":{\\\"type\\\":\\\"string\\\"}}},\\\"required\\\":[\\\"issues\\\"]},\\\"mimeType\\\":\\\"#get_attachment.output.mimeType\\\",\\\"base64content\\\":\\\"#get_attachment.output.blobRef\\\",\\\"filename\\\":\\\"#get_attachment.output.filename\\\"}}]}]}\""


    @Test
    fun `should fix json`()  {
        val fixed = service.ensureValidJson(json)
        assertNotEquals(fixed, json)
    }
}