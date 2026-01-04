package concord.dev.domain

import java.util.UUID

/**
 * Type-safe wrapper for Thread IDs.
 * Prevents accidentally mixing Thread IDs with other UUID types.
 */
@JvmInline
value class ThreadId(val value: UUID) {
    companion object {
        fun random(): ThreadId = ThreadId(UUID.randomUUID())
        fun fromString(str: String): ThreadId = ThreadId(UUID.fromString(str))
    }

    override fun toString(): String = value.toString()
}

/**
 * Type-safe wrapper for Post IDs.
 * Prevents accidentally mixing Post IDs with other Long types.
 */
@JvmInline
value class PostId(val value: Long) {
    override fun toString(): String = value.toString()
}

/**
 * Type-safe wrapper for URLs with validation.
 * Ensures URLs are non-blank when created.
 */
@JvmInline
value class Url(val value: String) {
    init {
        require(value.isNotBlank()) { "URL cannot be blank" }
    }

    override fun toString(): String = value
}

/**
 * Type-safe wrapper for post content with validation.
 * Ensures content is non-blank and within size limits.
 */
@JvmInline
value class Content(val value: String) {
    init {
        require(value.isNotBlank()) { "Content cannot be blank" }
        require(value.length <= 10000) { "Content cannot exceed 10000 characters" }
    }

    override fun toString(): String = value
}

/**
 * Type-safe wrapper for post numbers within a thread.
 * Post numbers are 1-indexed sequential numbers.
 */
@JvmInline
value class PostNumber(val value: Int) {
    init {
        require(value > 0) { "Post number must be positive" }
    }

    override fun toString(): String = value.toString()
}

/**
 * Type-safe wrapper for the count of posts in a thread.
 * Post count is 0-indexed (number of posts).
 */
@JvmInline
value class PostCount(val value: Int) {
    init {
        require(value >= 0) { "Post count cannot be negative" }
    }

    operator fun inc(): PostCount = PostCount(value + 1)

    override fun toString(): String = value.toString()
}
