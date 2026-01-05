package concord.dev.config

import com.pgvector.PGvector
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.usertype.UserType
import java.io.Serializable
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types

/**
 * Custom Hibernate UserType for mapping PostgreSQL vector type to PGvector.
 *
 * This enables Hibernate to properly serialize/deserialize pgvector columns,
 * allowing embeddings to be stored and queried natively.
 *
 * Usage:
 * ```kotlin
 * @Type(VectorUserType::class)
 * @Column(columnDefinition = "vector(768)")
 * var embedding: PGvector? = null
 * ```
 */
class VectorUserType : UserType<PGvector> {

    override fun getSqlType(): Int = Types.OTHER

    override fun returnedClass(): Class<PGvector> = PGvector::class.java

    override fun equals(x: PGvector?, y: PGvector?): Boolean {
        if (x === y) return true
        if (x == null || y == null) return false

        val xArray = x.toArray()
        val yArray = y.toArray()
        return xArray.contentEquals(yArray)
    }

    override fun hashCode(x: PGvector?): Int {
        return x?.toArray()?.contentHashCode() ?: 0
    }

    override fun nullSafeGet(
        rs: ResultSet,
        position: Int,
        session: SharedSessionContractImplementor?,
        owner: Any?
    ): PGvector? {
        val obj = rs.getObject(position) ?: return null

        // PostgreSQL returns the vector as a PGobject with type 'vector'
        // The value is a string like "[0.1,0.2,0.3]"
        return when (obj) {
            is PGvector -> obj
            is org.postgresql.util.PGobject -> PGvector(obj.value)
            is String -> PGvector(obj)
            else -> throw IllegalStateException("Unexpected vector value type: ${obj.javaClass.name}")
        }
    }

    override fun nullSafeSet(
        st: PreparedStatement,
        value: PGvector?,
        index: Int,
        session: SharedSessionContractImplementor?
    ) {
        if (value == null) {
            // PostgreSQL needs to know the exact type even for NULL values
            // Otherwise it defaults to bytea which causes a type mismatch
            st.setNull(index, Types.OTHER, "vector")
        } else {
            // PGvector already extends PGobject with type='vector'
            // Just set it directly
            st.setObject(index, value)
        }
    }

    override fun deepCopy(value: PGvector?): PGvector? {
        if (value == null) return null

        // Create a new PGvector with a copy of the float array
        val originalArray = value.toArray()
        val copiedArray = originalArray.copyOf()
        return PGvector(copiedArray)
    }

    override fun isMutable(): Boolean = false

    override fun disassemble(value: PGvector?): Serializable? {
        return deepCopy(value)
    }

    override fun assemble(cached: Serializable?, owner: Any?): PGvector? {
        return deepCopy(cached as? PGvector)
    }

    override fun replace(original: PGvector?, target: PGvector?, owner: Any?): PGvector? {
        return deepCopy(original)
    }
}