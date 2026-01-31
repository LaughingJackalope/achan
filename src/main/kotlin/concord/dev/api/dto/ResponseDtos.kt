package concord.dev.api.dto

import java.util.UUID

data class ResponseRequest(
    val objectId: String,
    val content: String,
    val agentId: String = "user"
)

data class ResponseAccepted(
    val intentId: UUID,
    val message: String = "Request accepted for processing."
)
