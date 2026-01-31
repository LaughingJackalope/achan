package concord.dev.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import concord.dev.service.ResponseSubscriptionManager
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped
import jakarta.websocket.*
import jakarta.websocket.server.PathParam
import jakarta.websocket.server.ServerEndpoint
import java.util.UUID

/**
 * WebSocket endpoint for real-time cognitive response delivery.
 * Clients connect to /v1/responses/subscribe/{intentId} and receive
 * the committed response as soon as it's available.
 */
@ApplicationScoped
@ServerEndpoint("/v1/responses/subscribe/{intentId}")
class ResponseWebSocket(
    private val subscriptionManager: ResponseSubscriptionManager,
    private val objectMapper: ObjectMapper
) {

    @OnOpen
    fun onOpen(session: Session, @PathParam("intentId") intentIdStr: String) {
        try {
            val intentId = UUID.fromString(intentIdStr)
            Log.infof("WebSocket opened: session=%s, intentId=%s", session.id, intentId)

            // Subscribe this session to the intent
            subscriptionManager.subscribe(intentId, session)

            // Send acknowledgment immediately (don't block on database check)
            sendAck(session, intentId)

            // Note: If result already exists, it will be pushed by the subscription manager
            // when the backend detects the subscriber. This avoids blocking operations.

        } catch (e: IllegalArgumentException) {
            Log.errorf("Invalid intentId format: %s", intentIdStr)
            session.close(CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, "Invalid intentId format"))
        } catch (e: Exception) {
            Log.errorf(e, "Error during WebSocket open for intentId: %s", intentIdStr)
            session.close(CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, "Server error"))
        }
    }

    @OnMessage
    fun onMessage(message: String, session: Session, @PathParam("intentId") intentIdStr: String) {
        // Handle ping/pong for connection health
        if (message == "ping") {
            try {
                session.asyncRemote.sendText("pong")
            } catch (e: Exception) {
                Log.errorf(e, "Failed to send pong to session %s", session.id)
            }
        } else {
            Log.debugf("Received unexpected message from session %s: %s", session.id, message)
        }
    }

    @OnClose
    fun onClose(session: Session, @PathParam("intentId") intentIdStr: String, closeReason: CloseReason) {
        try {
            val intentId = UUID.fromString(intentIdStr)
            Log.infof("WebSocket closed: session=%s, intentId=%s, reason=%s",
                session.id, intentId, closeReason.reasonPhrase)
            subscriptionManager.unsubscribe(intentId, session)
        } catch (e: IllegalArgumentException) {
            Log.warnf("Cleanup: Invalid intentId format during close: %s", intentIdStr)
        }
    }

    @OnError
    fun onError(session: Session, @PathParam("intentId") intentIdStr: String?, throwable: Throwable) {
        Log.errorf(throwable, "WebSocket error: session=%s, intentId=%s",
            session.id, intentIdStr ?: "unknown")

        // Cleanup subscription
        if (intentIdStr != null) {
            try {
                val intentId = UUID.fromString(intentIdStr)
                subscriptionManager.unsubscribe(intentId, session)
            } catch (e: IllegalArgumentException) {
                // Invalid intentId, just do full cleanup
                subscriptionManager.unsubscribeAll(session)
            }
        } else {
            subscriptionManager.unsubscribeAll(session)
        }
    }

    /**
     * Send acknowledgment that subscription was successful.
     * Uses async remote to avoid blocking the IO thread.
     */
    private fun sendAck(session: Session, intentId: UUID) {
        try {
            val ack = mapOf(
                "type" to "subscribed",
                "intentId" to intentId.toString(),
                "message" to "Waiting for cognitive response..."
            )
            session.asyncRemote.sendText(objectMapper.writeValueAsString(ack))
        } catch (e: Exception) {
            Log.errorf(e, "Failed to send ack to session %s", session.id)
        }
    }
}
