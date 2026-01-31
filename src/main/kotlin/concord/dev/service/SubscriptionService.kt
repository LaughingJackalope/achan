package concord.dev.service

import concord.dev.domain.*
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import org.jboss.logging.Logger
import java.time.Instant

/**
 * Notification match result.
 */
data class NotificationMatch(
    val subscription: AgentSubscription,
    val matchReason: String,
    val similarity: Double? = null
)

/**
 * Service for managing agent subscriptions and matching notifications.
 * 
 * Handles:
 * - Creating/updating/deleting subscriptions
 * - Matching new posts against subscriptions
 * - Finding which agents should be notified
 */
@ApplicationScoped
class SubscriptionService(
    private val searchService: SearchService,
    private val objectMapper: ObjectMapper
) {
    private val log: Logger = Logger.getLogger(SubscriptionService::class.java)

    /**
     * Create a new subscription for an agent.
     */
    @Transactional
    fun createSubscription(
        agentId: String,
        subscriptionType: SubscriptionType,
        query: String? = null,
        threadId: ThreadId? = null,
        similarityThreshold: Double = 0.7,
        notifyOn: List<NotifyOn>,
        capabilities: List<String>? = null
    ): AgentSubscription {
        log.infof("Creating subscription for agent %s (type=%s)", agentId, subscriptionType)

        // Validate inputs
        when (subscriptionType) {
            SubscriptionType.SEMANTIC -> {
                require(!query.isNullOrBlank()) { "Query required for SEMANTIC subscription" }
            }
            SubscriptionType.URL_PATTERN -> {
                require(!query.isNullOrBlank()) { "Query required for URL_PATTERN subscription" }
            }
            SubscriptionType.THREAD_ID -> {
                require(threadId != null) { "Thread ID required for THREAD_ID subscription" }
            }
        }

        require(notifyOn.isNotEmpty()) { "At least one notifyOn event required" }
        require(similarityThreshold in 0.0..1.0) { "Similarity threshold must be between 0.0 and 1.0" }

        val subscription = AgentSubscription().apply {
            this.agentId = agentId
            this.subscriptionType = subscriptionType
            this.query = query
            this.threadId = threadId?.value
            this.similarityThreshold = similarityThreshold
            this.notifyOn = objectMapper.writeValueAsString(notifyOn.map { it.name })
            this.capabilities = capabilities?.let { objectMapper.writeValueAsString(it) }
            this.active = true
            this.createdAt = Instant.now()
            this.updatedAt = Instant.now()
        }

        subscription.persist()
        log.infof("Subscription created: id=%d", subscription.id)
        
        return subscription
    }

    /**
     * Get all active subscriptions for an agent.
     */
    fun getSubscriptions(agentId: String): List<AgentSubscription> {
        return AgentSubscription.findByAgentId(agentId)
    }

    /**
     * Deactivate a subscription.
     */
    @Transactional
    fun deleteSubscription(subscriptionId: Long): Boolean {
        val subscription = AgentSubscription.findById(subscriptionId) ?: return false
        subscription.active = false
        subscription.updatedAt = Instant.now()
        return true
    }

    /**
     * Deactivate all subscriptions for an agent.
     */
    @Transactional
    fun deleteAllSubscriptions(agentId: String): Long {
        return AgentSubscription.deactivateByAgentId(agentId)
    }

    /**
     * Match a new post against all active subscriptions.
     * Returns list of agents that should be notified.
     */
    fun matchPost(post: Post, thread: Thread, eventType: NotifyOn): List<NotificationMatch> {
        log.debugf("Matching post %d against subscriptions (event=%s)", post.id, eventType)

        val matches = mutableListOf<NotificationMatch>()

        // Get all subscriptions that listen for this event type
        val allSubscriptions = AgentSubscription.findAll().list()
            .filter { it.active }
            .filter { subscription ->
                val notifyEvents = parseNotifyOn(subscription.notifyOn)
                eventType in notifyEvents
            }

        log.debugf("Found %d active subscriptions for event %s", allSubscriptions.size, eventType)

        for (subscription in allSubscriptions) {
            val match = matchSubscription(subscription, post, thread, eventType)
            if (match != null) {
                matches.add(match)
            }
        }

        log.infof("Matched post %d to %d subscriptions", post.id, matches.size)
        return matches
    }

    /**
     * Match a single subscription against a post.
     */
    private fun matchSubscription(
        subscription: AgentSubscription,
        post: Post,
        thread: Thread,
        eventType: NotifyOn
    ): NotificationMatch? {
        when (subscription.subscriptionType) {
            SubscriptionType.SEMANTIC -> {
                return matchSemanticSubscription(subscription, post, eventType)
            }
            SubscriptionType.URL_PATTERN -> {
                return matchUrlPatternSubscription(subscription, thread, eventType)
            }
            SubscriptionType.THREAD_ID -> {
                return matchThreadIdSubscription(subscription, thread, eventType)
            }
            else -> return null
        }
    }

    /**
     * Match semantic subscription using embedding similarity.
     */
    private fun matchSemanticSubscription(
        subscription: AgentSubscription,
        post: Post,
        eventType: NotifyOn
    ): NotificationMatch? {
        val query = subscription.query ?: return null
        val content = post.content ?: return null

        // For MVP, use simple text matching instead of embeddings
        // TODO: Use SearchService with embeddings for better matching
        val similarity = calculateTextSimilarity(query, content)

        return if (similarity >= subscription.similarityThreshold) {
            NotificationMatch(
                subscription = subscription,
                matchReason = "Semantic match: '$query' (similarity=${"%.2f".format(similarity)})",
                similarity = similarity
            )
        } else {
            null
        }
    }

    /**
     * Simple text similarity (Jaccard index on words).
     * TODO: Replace with embedding-based similarity.
     */
    private fun calculateTextSimilarity(query: String, content: String): Double {
        val queryWords = query.lowercase().split(Regex("\\W+")).toSet()
        val contentWords = content.lowercase().split(Regex("\\W+")).toSet()
        
        val intersection = queryWords.intersect(contentWords).size
        val union = queryWords.union(contentWords).size
        
        return if (union > 0) intersection.toDouble() / union.toDouble() else 0.0
    }

    /**
     * Match URL pattern subscription.
     */
    private fun matchUrlPatternSubscription(
        subscription: AgentSubscription,
        thread: Thread,
        eventType: NotifyOn
    ): NotificationMatch? {
        val pattern = subscription.query ?: return null
        val url = thread.url ?: return null

        // Convert wildcard pattern to regex
        val regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .toRegex()

        return if (regex.matches(url)) {
            NotificationMatch(
                subscription = subscription,
                matchReason = "URL pattern match: '$pattern'"
            )
        } else {
            null
        }
    }

    /**
     * Match thread-specific subscription.
     */
    private fun matchThreadIdSubscription(
        subscription: AgentSubscription,
        thread: Thread,
        eventType: NotifyOn
    ): NotificationMatch? {
        return if (subscription.threadId == thread.id) {
            NotificationMatch(
                subscription = subscription,
                matchReason = "Thread subscription"
            )
        } else {
            null
        }
    }

    /**
     * Find agents already responding to a thread.
     * Used to avoid duplicate work.
     */
    fun findRespondingAgents(threadId: ThreadId): List<String> {
        val posts = Post.findByThreadId(threadId)
        return posts.mapNotNull { it.agentId }.distinct()
    }

    /**
     * Parse notifyOn JSON array to enum list.
     */
    private fun parseNotifyOn(json: String?): List<NotifyOn> {
        if (json.isNullOrBlank()) return emptyList()
        
        return try {
            val names = objectMapper.readValue(json, List::class.java) as List<String>
            names.mapNotNull { name ->
                try {
                    NotifyOn.valueOf(name)
                } catch (e: IllegalArgumentException) {
                    log.warnf("Unknown NotifyOn value: %s", name)
                    null
                }
            }
        } catch (e: Exception) {
            log.errorf(e, "Failed to parse notifyOn JSON: %s", json)
            emptyList()
        }
    }

    /**
     * Parse capabilities JSON array.
     */
    fun parseCapabilities(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        
        return try {
            objectMapper.readValue(json, List::class.java) as? List<String> ?: emptyList()
        } catch (e: Exception) {
            log.errorf(e, "Failed to parse capabilities JSON: %s", json)
            emptyList()
        }
    }
}
