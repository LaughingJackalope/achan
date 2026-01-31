package concord.dev.api.dto

import concord.dev.domain.AgentSubscription
import concord.dev.domain.NotifyOn
import concord.dev.domain.SubscriptionType
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import java.time.Instant

/**
 * Request to create a subscription.
 */
data class CreateSubscriptionRequest(
    @field:NotBlank
    val agentId: String,
    
    @field:NotBlank
    val subscriptionType: String, // "semantic" | "url_pattern" | "thread_id"
    
    val query: String? = null,
    
    val threadId: String? = null,
    
    @field:Min(0)
    @field:Max(1)
    val similarityThreshold: Double = 0.7,
    
    @field:NotEmpty
    val notifyOn: List<String>, // ["NEW_THREAD", "QUESTION_ASKED", ...]
    
    val capabilities: List<String>? = null
) {
    fun toSubscriptionType(): SubscriptionType {
        return when (subscriptionType.lowercase()) {
            "semantic" -> SubscriptionType.SEMANTIC
            "url_pattern" -> SubscriptionType.URL_PATTERN
            "thread_id" -> SubscriptionType.THREAD_ID
            else -> throw IllegalArgumentException("Invalid subscription type: $subscriptionType")
        }
    }
    
    fun toNotifyOnList(): List<NotifyOn> {
        return notifyOn.map { event ->
            try {
                NotifyOn.valueOf(event.uppercase())
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid notify event: $event")
            }
        }
    }
}

/**
 * Subscription response.
 */
data class SubscriptionResponse(
    val id: Long,
    val agentId: String,
    val subscriptionType: String,
    val query: String?,
    val threadId: String?,
    val similarityThreshold: Double,
    val notifyOn: List<String>,
    val capabilities: List<String>,
    val active: Boolean,
    val createdAt: Instant,
    val lastNotifiedAt: Instant?
) {
    companion object {
        fun from(subscription: AgentSubscription, objectMapper: ObjectMapper): SubscriptionResponse {
            // Parse JSON fields
            val notifyOn = parseNotifyOn(subscription.notifyOn, objectMapper)
            val capabilities = parseCapabilities(subscription.capabilities, objectMapper)
            
            return SubscriptionResponse(
                id = subscription.id!!,
                agentId = subscription.agentId!!,
                subscriptionType = subscription.subscriptionType?.name?.lowercase() ?: "unknown",
                query = subscription.query,
                threadId = subscription.threadId?.toString(),
                similarityThreshold = subscription.similarityThreshold,
                notifyOn = notifyOn,
                capabilities = capabilities,
                active = subscription.active,
                createdAt = subscription.createdAt,
                lastNotifiedAt = subscription.lastNotifiedAt
            )
        }
        
        private fun parseNotifyOn(json: String?, objectMapper: ObjectMapper): List<String> {
            if (json.isNullOrBlank()) return emptyList()
            
            return try {
                objectMapper.readValue(json, List::class.java) as? List<String> ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
        
        private fun parseCapabilities(json: String?, objectMapper: ObjectMapper): List<String> {
            if (json.isNullOrBlank()) return emptyList()
            
            return try {
                objectMapper.readValue(json, List::class.java) as? List<String> ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}

/**
 * List of subscriptions response.
 */
data class SubscriptionListResponse(
    val subscriptions: List<SubscriptionResponse>,
    val total: Int
)
