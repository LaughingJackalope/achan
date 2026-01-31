package concord.dev.api.dto

import concord.dev.domain.ThreadDigest
import concord.dev.domain.ThreadId
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant

/**
 * Represents a key claim in a thread discussion.
 */
data class KeyClaim(
    val claimId: String,
    val text: String,
    val supportScore: Double,
    val citations: List<Int>,
    val rebuttals: List<Int>
)

/**
 * Represents a related thread with similarity information.
 */
data class RelatedThread(
    val threadId: String,
    val similarity: Double,
    val relationship: String
)

/**
 * Complete thread digest response.
 * Matches the format specified in AGENT_DESIGN.md Phase 2.
 */
data class ThreadDigestResponse(
    val threadId: String,
    val url: String?,
    val summary: String,
    val keyClaims: List<KeyClaim>,
    val openQuestions: List<String>,
    val relatedThreads: List<RelatedThread>,
    val lastSynthesizedAt: Instant
) {
    companion object {
        fun from(digest: ThreadDigest, url: String?, objectMapper: ObjectMapper): ThreadDigestResponse {
            // Parse JSON fields
            val keyClaims = parseKeyClaims(digest.keyClaims, objectMapper)
            val openQuestions = parseOpenQuestions(digest.openQuestions, objectMapper)
            val relatedThreads = parseRelatedThreads(digest.relatedThreads, objectMapper)

            return ThreadDigestResponse(
                threadId = digest.threadId.toString(),
                url = url,
                summary = digest.summary ?: "",
                keyClaims = keyClaims,
                openQuestions = openQuestions,
                relatedThreads = relatedThreads,
                lastSynthesizedAt = digest.lastSynthesizedAt
            )
        }

        private fun parseKeyClaims(json: String?, objectMapper: ObjectMapper): List<KeyClaim> {
            if (json.isNullOrBlank()) return emptyList()
            
            return try {
                val rawList = objectMapper.readValue(json, List::class.java) as List<Map<String, Any>>
                rawList.map { claim ->
                    KeyClaim(
                        claimId = claim["claim_id"]?.toString() ?: "",
                        text = claim["text"]?.toString() ?: "",
                        supportScore = (claim["support_score"] as? Number)?.toDouble() ?: 0.0,
                        citations = (claim["citations"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList(),
                        rebuttals = (claim["rebuttals"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

        private fun parseOpenQuestions(json: String?, objectMapper: ObjectMapper): List<String> {
            if (json.isNullOrBlank()) return emptyList()
            
            return try {
                objectMapper.readValue(json, List::class.java) as? List<String> ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        private fun parseRelatedThreads(json: String?, objectMapper: ObjectMapper): List<RelatedThread> {
            if (json.isNullOrBlank()) return emptyList()
            
            return try {
                val rawList = objectMapper.readValue(json, List::class.java) as List<Map<String, Any>>
                rawList.map { thread ->
                    RelatedThread(
                        threadId = thread["thread_id"]?.toString() ?: "",
                        similarity = (thread["similarity"] as? Number)?.toDouble() ?: 0.0,
                        relationship = thread["relationship"]?.toString() ?: "unknown"
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
