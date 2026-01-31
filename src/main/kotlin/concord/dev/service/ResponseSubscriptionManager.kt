package concord.dev.service

import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped
import jakarta.websocket.Session
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages WebSocket subscriptions for real-time cognitive response delivery.
 * Tracks which WebSocket sessions are waiting for which intentIds.
 */
@ApplicationScoped
class ResponseSubscriptionManager {

    // Map of intentEventId -> Set of WebSocket sessions waiting for that result
    private val subscriptions = ConcurrentHashMap<UUID, MutableSet<Session>>()

    /**
     * Subscribe a WebSocket session to receive the result for a specific intentId.
     *
     * @param intentId The UUID of the intent the client is waiting for
     * @param session The WebSocket session to send the result to
     */
    fun subscribe(intentId: UUID, session: Session) {
        subscriptions.compute(intentId) { _, sessions ->
            val set = sessions ?: ConcurrentHashMap.newKeySet()
            set.add(session)
            Log.infof("Session %s subscribed to intent %s. Total subscribers: %d",
                session.id, intentId, set.size)
            set
        }
    }

    /**
     * Unsubscribe a WebSocket session from a specific intentId.
     *
     * @param intentId The UUID of the intent
     * @param session The WebSocket session to unsubscribe
     */
    fun unsubscribe(intentId: UUID, session: Session) {
        subscriptions.computeIfPresent(intentId) { _, sessions ->
            sessions.remove(session)
            Log.infof("Session %s unsubscribed from intent %s. Remaining subscribers: %d",
                session.id, intentId, sessions.size)
            if (sessions.isEmpty()) null else sessions
        }
    }

    /**
     * Unsubscribe a session from all intents (useful for cleanup on disconnect).
     *
     * @param session The WebSocket session to unsubscribe completely
     */
    fun unsubscribeAll(session: Session) {
        subscriptions.forEach { (intentId, sessions) ->
            if (sessions.remove(session)) {
                Log.infof("Removed session %s from intent %s during cleanup", session.id, intentId)
            }
        }
        // Clean up empty sets
        subscriptions.entries.removeIf { it.value.isEmpty() }
    }

    /**
     * Push a response to all subscribed WebSocket sessions for a given intentId.
     *
     * @param intentId The UUID of the intent that has been resolved
     * @param response The response payload to send
     * @return Number of sessions that received the message
     */
    fun pushResponse(intentId: UUID, response: String): Int {
        val sessions = subscriptions[intentId] ?: return 0

        var successCount = 0
        val sessionsToRemove = mutableSetOf<Session>()

        sessions.forEach { session ->
            try {
                if (session.isOpen) {
                    session.basicRemote.sendText(response)
                    successCount++
                    Log.infof("Pushed response for intent %s to session %s", intentId, session.id)
                } else {
                    Log.warnf("Session %s for intent %s is closed, marking for removal",
                        session.id, intentId)
                    sessionsToRemove.add(session)
                }
            } catch (e: Exception) {
                Log.errorf(e, "Failed to push response for intent %s to session %s",
                    intentId, session.id)
                sessionsToRemove.add(session)
            }
        }

        // Clean up dead sessions
        sessionsToRemove.forEach { sessions.remove(it) }
        if (sessions.isEmpty()) {
            subscriptions.remove(intentId)
        }

        return successCount
    }

    /**
     * Get the number of active sessions subscribed to a specific intent.
     */
    fun getSubscriberCount(intentId: UUID): Int {
        return subscriptions[intentId]?.size ?: 0
    }

    /**
     * Get total number of active subscriptions across all intents.
     */
    fun getTotalSubscriptions(): Int {
        return subscriptions.values.sumOf { it.size }
    }
}
