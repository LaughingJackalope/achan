package concord.dev.domain

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "committed_acts")
class CommittedAct : PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "intent_event_id", nullable = false)
    var intentEventId: UUID? = null

    @Column(name = "proposal_event_id", nullable = false)
    var proposalEventId: UUID? = null

    @Column(name = "object_id", nullable = false)
    var objectId: String? = null

    @Column(columnDefinition = "TEXT")
    var action: String? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()

    companion object : PanacheCompanion<CommittedAct> {
        fun findByIntentEventId(intentEventId: UUID): CommittedAct? =
            find("intentEventId", intentEventId).firstResult()
    }
}
