package concord.dev.domain

import java.time.Instant
import java.util.UUID

/**
 * Represents a single event in the shared cognitive space.
 * This is the base interface for all events that drive the system's reasoning process.
 */
sealed interface CognitiveEvent {
    /** The unique identifier for this event. */
    val eventId: UUID

    /** The identifier of the object this event pertains to (e.g., a URL, a thread ID). */
    val objectId: String

    /** A chain of event IDs that led to this event, establishing causality. */
    val causalityChain: List<UUID>

    /** The identifier of the agent that produced this event. */
    val agentId: String

    /** The timestamp of when the event was created. */
    val timestamp: Instant
}

/**
 * An event representing a declared intent from a user or another agent.
 * This is the starting point for a cognitive process.
 *
 * @param content The raw input content of the intent (e.g., user query).
 */
data class IntentDeclared(
    override val eventId: UUID = UUID.randomUUID(),
    override val objectId: String,
    override val causalityChain: List<UUID> = emptyList(),
    override val agentId: String,
    override val timestamp: Instant = Instant.now(),
    val content: String
) : CognitiveEvent

/**
 * An event representing the context gathered to address an intent.
 *
 * @param intentEventId The ID of the `IntentDeclared` event this context is for.
 * @param contextObjectIds A list of object IDs that form the materialized context.
 */
data class ContextMaterialized(
    override val eventId: UUID = UUID.randomUUID(),
    override val objectId: String,
    override val causalityChain: List<UUID>,
    override val agentId: String,
    override val timestamp: Instant = Instant.now(),
    val intentEventId: UUID,
    val intentContent: String,
    val contextObjectIds: List<String>
) : CognitiveEvent

/**
 * A proposed action from a cognitive agent. Multiple agents can propose actions for a given intent.
 *
 * @param intentEventId The ID of the `IntentDeclared` event this proposal is for.
 * @param action The proposed action (e.g., text response, tool call).
 * @param confidence The agent's confidence in this proposal (0.0 to 1.0).
 * @param priority The priority of this proposal, used for tie-breaking.
 */
data class ActProposed(
    override val eventId: UUID = UUID.randomUUID(),
    override val objectId: String,
    override val causalityChain: List<UUID>,
    override val agentId: String,
    override val timestamp: Instant = Instant.now(),
    val intentEventId: UUID,
    val action: String, // Could be a more structured object later
    val confidence: Double,
    val priority: Int = 0
) : CognitiveEvent

/**
 * An event representing the single, committed action chosen from one or more proposals.
 * This is the "winning" act that becomes part of the official record.
 *
 * @param proposalEventId The ID of the `ActProposed` event that was chosen.
 * @param action The committed action.
 */
data class ActCommitted(
    override val eventId: UUID = UUID.randomUUID(),
    override val objectId: String,
    override val causalityChain: List<UUID>,
    override val agentId: String, // The agent who committed the act (could be a "decider" agent)
    override val timestamp: Instant = Instant.now(),
    val intentEventId: UUID,
    val proposalEventId: UUID,
    val action: String // Could be a more structured object later
) : CognitiveEvent

/**
 * An event indicating that a part of the cognitive process has failed.
 *
 * @param failedEventId The ID of the event that failed.
 * @param reason A description of the failure.
 */
data class ActFailed(
    override val eventId: UUID = UUID.randomUUID(),
    override val objectId: String,
    override val causalityChain: List<UUID>,
    override val agentId: String,
    override val timestamp: Instant = Instant.now(),
    val failedEventId: UUID,
    val reason: String
) : CognitiveEvent
