package concord.dev.api

import concord.dev.api.dto.ResponseRequest
import concord.dev.api.dto.ResponseAccepted
import concord.dev.domain.CommittedAct
import concord.dev.domain.IntentDeclared
import concord.dev.service.CognitiveEventProducerService
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.UUID

@Path("/v1/responses")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class ResponseResource(
    private val producerService: CognitiveEventProducerService,
    private val instanceConfig: concord.dev.config.InstanceConfig
) {

    @POST
    fun createResponse(request: ResponseRequest): Response {
        val intent = IntentDeclared(
            objectId = request.objectId,
            content = request.content,
            agentId = request.agentId,
            sourceInstance = instanceConfig.instanceId
        )
        producerService.sendIntentDeclared(intent)

        val responseDto = ResponseAccepted(intentId = intent.eventId)
        return Response.accepted(responseDto).build()
    }

    @GET
    @Path("/{intentId}")
    fun getResponse(@PathParam("intentId") intentId: UUID): Response {
        val committedAct = CommittedAct.findByIntentEventId(intentId)
        return if (committedAct != null) {
            Response.ok(committedAct).build()
        } else {
            Response.status(Response.Status.NOT_FOUND)
                .entity(mapOf("message" to "Response not yet available or intent ID not found."))
                .build()
        }
    }
}
