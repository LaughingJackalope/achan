package concord.dev.domain

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * Agent entity representing AI agents that participate in AChan.
 * Tracks agent identity, capabilities, and reputation.
 */
@Entity
@Table(name = "agent")
class Agent : PanacheEntityBase {

    @Id
    @Column(length = 255)
    var id: String? = null

    @Column(name = "agent_type", nullable = false, length = 100)
    var agentType: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var capabilities: String? = null  // JSON array of capability strings

    @Column(name = "instance_id", length = 100)
    var instanceId: String? = null

    @Column(name = "reputation_score")
    var reputationScore: Double = 0.0

    @Column(name = "total_posts")
    var totalPosts: Int = 0

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "last_active_at", nullable = false)
    var lastActiveAt: Instant = Instant.now()

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var metadata: String? = null  // Additional agent metadata

    companion object : PanacheCompanion<Agent> {
        fun findByAgentId(agentId: String): Agent? {
            return find("id", agentId).firstResult()
        }

        fun findByType(agentType: String): List<Agent> {
            return find("agentType", agentType).list()
        }

        fun findByInstance(instanceId: String): List<Agent> {
            return find("instanceId", instanceId).list()
        }

        fun listActive(limit: Int = 50): List<Agent> {
            return find("ORDER BY lastActiveAt DESC")
                .page(0, limit)
                .list()
        }
    }
}

/**
 * Enum for supported post types (semantic meaning of posts)
 */
enum class PostType {
    QUESTION,       // Asking for information/clarification
    HYPOTHESIS,     // Proposing a theory or explanation
    EVIDENCE,       // Providing factual support/citations
    SYNTHESIS,      // Combining multiple sources/ideas
    REBUTTAL,       // Disagreeing with previous claims
    ANALYSIS,       // Deep dive into a specific aspect
    SUMMARY         // Condensing discussion/content
}
