package com.mentalgym.service

import com.mentalgym.config.MentalGymProperties
import com.mentalgym.domain.ConceptStatus
import com.mentalgym.domain.MentalGymException
import com.mentalgym.persistence.ConceptRepository
import com.mentalgym.persistence.DocRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private const val CHARS_PER_TOKEN = 4

data class DocInput(
    val url: String,
    val title: String?,
    val content: String,
)

@Service
class DocService(
    private val conceptService: ConceptService,
    private val conceptRepository: ConceptRepository,
    private val docRepository: DocRepository,
    private val properties: MentalGymProperties,
) {

    @Transactional
    fun saveDocs(conceptId: UUID, docs: List<DocInput>, replace: Boolean): SavedDocsView {
        val concept = conceptService.require(conceptId)
        if (docs.isEmpty()) throw MentalGymException("Pass at least one doc")
        docs.forEach {
            if (it.url.isBlank()) throw MentalGymException("A doc needs a url")
            if (it.content.isBlank()) throw MentalGymException("Doc ${it.url} has no text to store")
        }
        if (replace) docRepository.deleteByConcept(conceptId)

        val stored = docs.map { docRepository.insert(conceptId, it.url.trim(), it.title?.trim(), it.content) }
        if (concept.status != ConceptStatus.MASTERED) {
            conceptRepository.updateStatus(conceptId, ConceptStatus.READING)
        }

        val warnings = stored.filter { it.content.length / CHARS_PER_TOKEN > properties.docTokenSoftCap }
            .map { "${it.url} is about ${it.content.length / CHARS_PER_TOKEN} tokens, above the soft cap of ${properties.docTokenSoftCap}" }

        return SavedDocsView(
            conceptId = conceptId,
            status = conceptService.require(conceptId).status.wire,
            docs = docRepository.findByConcept(conceptId).map {
                DocView(
                    docId = it.id,
                    url = it.url,
                    title = it.title,
                    read = it.readAt != null,
                    characters = it.content.length,
                    fetchedAt = it.fetchedAt,
                )
            },
            warnings = warnings,
        )
    }

    fun getDocPage(conceptId: UUID, docIndex: Int, offset: Int): DocPageView {
        val concept = conceptService.require(conceptId)
        val docs = docRepository.findByConcept(conceptId)
        if (docs.isEmpty()) throw MentalGymException("Concept '${concept.name}' has no docs yet")
        if (docIndex !in docs.indices) {
            throw MentalGymException("docIndex must be between 0 and ${docs.size - 1}, got $docIndex")
        }
        val doc = docs[docIndex]
        if (offset < 0 || offset >= doc.content.length) {
            throw MentalGymException("offset must be between 0 and ${doc.content.length - 1}, got $offset")
        }
        val end = minOf(offset + properties.docChunkChars, doc.content.length)
        return DocPageView(
            conceptId = conceptId,
            docCount = docs.size,
            docIndex = docIndex,
            title = doc.title,
            url = doc.url,
            characters = doc.content.length,
            offset = offset,
            nextOffset = end.takeIf { it < doc.content.length },
            content = doc.content.substring(offset, end),
            docs = summaries(docs),
        )
    }

    fun summaries(conceptId: UUID, inlineText: Boolean = false): List<DocSummary> =
        summaries(docRepository.findByConcept(conceptId), inlineText)

    private fun summaries(docs: List<com.mentalgym.domain.Doc>, inlineText: Boolean = false): List<DocSummary> {
        val total = docs.sumOf { it.content.length }
        val inline = inlineText && total <= properties.inlineDocChars
        return docs.mapIndexed { index, doc ->
            DocSummary(
                docIndex = index,
                title = doc.title,
                url = doc.url,
                characters = doc.content.length,
                content = doc.content.takeIf { inline },
            )
        }
    }

    @Transactional
    fun markDocsRead(conceptId: UUID): MarkedReadView {
        val concept = conceptService.require(conceptId)
        val docs = docRepository.findByConcept(conceptId)
        if (docs.isEmpty()) throw MentalGymException("Concept '${concept.name}' has no docs yet, run mg-add first")
        val marked = docRepository.markAllRead(conceptId)
        if (concept.status != ConceptStatus.MASTERED) {
            conceptRepository.updateStatus(conceptId, ConceptStatus.READY_TO_TEST)
        }
        return MarkedReadView(
            conceptId = conceptId,
            status = conceptService.require(conceptId).status.wire,
            docsMarkedRead = marked,
            totalDocs = docs.size,
        )
    }
}
