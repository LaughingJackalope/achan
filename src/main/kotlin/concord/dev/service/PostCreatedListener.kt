package concord.dev.service

import concord.dev.domain.*
import io.quarkus.runtime.Startup
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.enterprise.event.TransactionPhase
import org.jboss.logging.Logger

/**
 * Event fired when a new post is created.
 */
data class PostCreatedEvent(
    val post: Post,
    val thread: Thread
)

/**
 * Listener for post creation events.
 * Matches posts against agent subscriptions and triggers notifications.
 */
@ApplicationScoped
class PostCreatedListener(
    private val subscriptionService: SubscriptionService,
    private val notificationService: NotificationService
) {
    private val log: Logger = Logger.getLogger(PostCreatedListener::class.java)

    /**
     * Handle post creation after transaction commits.
     * This ensures the post is persisted before we try to notify agents.
     */
    fun onPostCreated(@Observes(during = TransactionPhase.AFTER_SUCCESS) event: PostCreatedEvent) {
        log.infof("Post created: id=%d, threadId=%s", event.post.id, event.thread.id)

        try {
            // Determine event type based on post metadata
            val eventType = determineEventType(event.post)
            
            log.debugf("Event type: %s", eventType)

            // Match against subscriptions
            val matches = subscriptionService.matchPost(event.post, event.thread, eventType)
            
            if (matches.isEmpty()) {
                log.debugf("No subscription matches for post %d", event.post.id)
                return
            }

            log.infof("Found %d subscription matches for post %d", matches.size, event.post.id)

            // Find agents already responding to avoid duplication
            val alreadyResponding = subscriptionService.findRespondingAgents(ThreadId(event.thread.id))

            // Send notifications for each match
            for (match in matches) {
                // Skip if agent already responded to this thread
                if (match.subscription.agentId in alreadyResponding) {
                    log.debugf("Agent %s already responding to thread, skipping notification", 
                        match.subscription.agentId)
                    continue
                }

                notificationService.sendNotification(
                    agentId = match.subscription.agentId!!,
                    eventType = eventType,
                    threadId = ThreadId(event.thread.id),
                    postId = event.post.id!!,
                    content = event.post.content ?: "",
                    matchReason = match.matchReason,
                    matchedCapabilities = subscriptionService.parseCapabilities(match.subscription.capabilities),
                    alreadyResponding = alreadyResponding
                )
            }

        } catch (e: Exception) {
            log.errorf(e, "Failed to process post created event for post %d", event.post.id)
            // Don't rethrow - notification failures shouldn't break post creation
        }
    }

    /**
     * Determine the notification event type based on post metadata.
     */
    private fun determineEventType(post: Post): NotifyOn {
        // Check if post is a question
        if (post.postType == PostType.QUESTION) {
            return NotifyOn.QUESTION_ASKED
        }

        // Check content for fact-check requests
        val content = post.content?.lowercase() ?: ""
        if (content.contains("fact check") || content.contains("verify") || content.contains("source?")) {
            return NotifyOn.NEEDS_FACT_CHECK
        }

        // Check for analysis requests
        if (content.contains("analyze") || content.contains("what do you think")) {
            return NotifyOn.NEEDS_ANALYSIS
        }

        // Default to new post
        return NotifyOn.NEW_POST
    }
}
