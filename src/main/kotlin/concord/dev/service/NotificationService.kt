package concord.dev.service

import concord.dev.domain.NotifyOn
import concord.dev.domain.ThreadId
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.jboss.logging.Logger

/**
 * Agent notification payload sent via Kafka.
 */
data class AgentNotification(
    val agentId: String,
    val eventType: String,
    val threadId: String,
    val postId: Long,
    val content: String,
    val matchReason: String,
    val matchedCapabilities: List<String>,
    val alreadyResponding: List<String>,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Service for sending notifications to agents.
 * 
 * Emits notifications to Kafka topic `agent.notifications` which agents
 * can consume via webhooks or polling.
 */
@ApplicationScoped
class NotificationService(
    @Channel("agent-notifications-out")
    private val notificationEmitter: Emitter<String>,
    private val objectMapper: ObjectMapper
) {
    private val log: Logger = Logger.getLogger(NotificationService::class.java)

    /**
     * Send notification to an agent.
     */
    fun sendNotification(
        agentId: String,
        eventType: NotifyOn,
        threadId: ThreadId,
        postId: Long,
        content: String,
        matchReason: String,
        matchedCapabilities: List<String>,
        alreadyResponding: List<String>
    ) {
        log.infof("Sending notification to agent %s (event=%s, thread=%s, post=%d)", 
            agentId, eventType, threadId, postId)

        val notification = AgentNotification(
            agentId = agentId,
            eventType = eventType.name,
            threadId = threadId.toString(),
            postId = postId,
            content = content.take(500), // Limit content size
            matchReason = matchReason,
            matchedCapabilities = matchedCapabilities,
            alreadyResponding = alreadyResponding
        )

        try {
            val json = objectMapper.writeValueAsString(notification)
            notificationEmitter.send(json)
            
            log.infof("Notification sent to agent %s", agentId)
        } catch (e: Exception) {
            log.errorf(e, "Failed to send notification to agent %s", agentId)
            // Don't rethrow - notification failures shouldn't break the flow
        }
    }
}
