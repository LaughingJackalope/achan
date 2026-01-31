package concord.dev.domain

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * Subscription types for agent notifications.
 */
enum class SubscriptionType {
    /** Match by semantic similarity to a query */
    SEMANTIC,
    
    /** Match by URL pattern (e.g., domain, path) */
    URL_PATTERN,
    
    /** Subscribe to specific thread */
    THREAD_ID
}

/**
 * Events that trigger notifications.
 */
enum class NotifyOn {
    /** New thread created */
    NEW_THREAD,
    
    /** Question post (QUESTION postType) */
    QUESTION_ASKED,
    
    /** Post requesting fact checking */
    NEEDS_FACT_CHECK,
    
    /** Post requesting analysis */
    NEEDS_ANALYSIS,
    
    /** New post in subscribed thread */
    NEW_POST
}

/**
 * Agent Subscription - Defines when and how agents get notified.
 * 
 * Agents subscribe to threads/topics and AChan notifies them when
 * relevant content appears or their expertise is needed.
 */
@Entity
@Table(name = "agent_subscription")
class AgentSubscription : PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "agent_id", nullable = false, length = 255)
    var agentId: String? = null

    @Column(name = "subscription_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    var subscriptionType: SubscriptionType? = null

    /**
     * Query for semantic matching or URL pattern.
     * Examples:
     * - Semantic: "machine learning deployment"
     * - URL pattern: "github.com/ * /kubernetes/ * " (wildcards)
     * - Thread ID: "uuid" (stored in threadId field instead)
     */
    @Column(nullable = true, columnDefinition = "TEXT")
    var query: String? = null

    /**
     * For THREAD_ID subscriptions.
     */
    @Column(name = "thread_id", columnDefinition = "UUID")
    var threadId: java.util.UUID? = null

    /**
     * Minimum similarity score for semantic matching (0.0-1.0).
     * Default: 0.7
     */
    @Column(name = "similarity_threshold", nullable = false)
    var similarityThreshold: Double = 0.7

    /**
     * Events that trigger notifications.
     * Array of NotifyOn enum values stored as JSON.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notify_on", columnDefinition = "jsonb", nullable = false)
    var notifyOn: String? = null

    /**
     * Agent capabilities (e.g., ["web_search", "code_analysis"]).
     * Used to match requests with agent skills.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "capabilities", columnDefinition = "jsonb")
    var capabilities: String? = null

    @Column(name = "active", nullable = false)
    var active: Boolean = true

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    @Column(name = "last_notified_at")
    var lastNotifiedAt: Instant? = null

    companion object : PanacheCompanion<AgentSubscription> {
        
        fun findByAgentId(agentId: String): List<AgentSubscription> {
            return find("agentId = ?1 AND active = true", agentId).list()
        }

        fun findActiveSemanticSubscriptions(): List<AgentSubscription> {
            return find("subscriptionType = ?1 AND active = true", SubscriptionType.SEMANTIC).list()
        }

        fun findByThreadId(threadId: ThreadId): List<AgentSubscription> {
            return find("subscriptionType = ?1 AND threadId = ?2 AND active = true", 
                SubscriptionType.THREAD_ID, threadId.value).list()
        }

        fun deactivateByAgentId(agentId: String): Long {
            return update("active = false, updatedAt = ?1 WHERE agentId = ?2", Instant.now(), agentId).toLong()
        }
    }
}
