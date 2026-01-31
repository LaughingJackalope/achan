package concord.dev.service

import concord.dev.domain.*
import io.smallrye.reactive.messaging.kafka.Record
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import java.util.*

@ApplicationScoped
class CognitiveEventProducerService(
    @Channel("intent-declared-out") private val intentDeclaredEmitter: Emitter<Record<UUID, IntentDeclared>>,
    @Channel("context-materialized-out") private val contextMaterializedEmitter: Emitter<Record<UUID, ContextMaterialized>>,
    @Channel("act-proposed-out") private val actProposedEmitter: Emitter<Record<UUID, ActProposed>>,
    @Channel("act-committed-out") private val actCommittedEmitter: Emitter<Record<UUID, ActCommitted>>,
    @Channel("act-failed-out") private val actFailedEmitter: Emitter<Record<UUID, ActFailed>>,
    @Channel("consensus-vote-out") private val consensusVoteEmitter: Emitter<Record<UUID, ConsensusVote>>,
    @Channel("consensus-reached-out") private val consensusReachedEmitter: Emitter<Record<UUID, ConsensusReached>>
) {
    fun sendIntentDeclared(event: IntentDeclared) {
        intentDeclaredEmitter.send(Record.of(event.eventId, event))
    }

    fun sendContextMaterialized(event: ContextMaterialized) {
        contextMaterializedEmitter.send(Record.of(event.eventId, event))
    }

    fun sendActProposed(event: ActProposed) {
        actProposedEmitter.send(Record.of(event.eventId, event))
    }

    fun sendActCommitted(event: ActCommitted) {
        actCommittedEmitter.send(Record.of(event.eventId, event))
    }

    fun sendActFailed(event: ActFailed) {
        actFailedEmitter.send(Record.of(event.eventId, event))
    }

    fun sendConsensusVote(event: ConsensusVote) {
        consensusVoteEmitter.send(Record.of(event.eventId, event))
    }

    fun sendConsensusReached(event: ConsensusReached) {
        consensusReachedEmitter.send(Record.of(event.eventId, event))
    }
}
