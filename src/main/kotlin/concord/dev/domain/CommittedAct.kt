package concord.dev.domain

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "committed_acts")
class CommittedAct(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "intent_event_id", nullable = false)
    var intentEventId: UUID,

    @Column(name = "proposal_event_id", nullable = false)
    var proposalEventId: UUID,

    @Column(name = "object_id", nullable = false)
    var objectId: String,

    @Column(columnDefinition = "TEXT")
    var action: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
) {
    companion object : PanacheCompanion<CommittedAct> {
        fun findByIntentEventId(intentEventId: UUID): CommittedAct? =
            find("intentEventId", intentEventId).firstResult()
    }
}
