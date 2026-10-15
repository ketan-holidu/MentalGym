package com.mentalgym.mcp

import com.mentalgym.service.DocPageView
import com.mentalgym.service.DocService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class DocsTools(private val docService: DocService) {

    @McpTool(
        name = "get_docs",
        description = "Generator, verifier and grader only. One stored document at a time. Returns a slice of its " +
            "text plus nextOffset when there is more, and the list of every doc with its docIndex.",
    )
    fun getDocs(
        @McpToolParam(description = "Concept id", required = true)
        conceptId: String,
        @McpToolParam(description = "Which document, 0-based, defaults to 0", required = false)
        docIndex: Int?,
        @McpToolParam(description = "Character offset to read from, defaults to 0", required = false)
        offset: Int?,
    ): DocPageView = docService.getDocPage(
        conceptId = uuid("conceptId", conceptId),
        docIndex = docIndex ?: 0,
        offset = offset ?: 0,
    )
}
