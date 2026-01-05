package concord.dev.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.util.UUID

@Converter
class ThreadIdConverter : AttributeConverter<ThreadId, UUID> {
    override fun convertToDatabaseColumn(attribute: ThreadId?): UUID? = attribute?.value
    override fun convertToEntityAttribute(dbData: UUID?): ThreadId? = dbData?.let { ThreadId(it) }
}

@Converter
class PostIdConverter : AttributeConverter<PostId, Long> {
    override fun convertToDatabaseColumn(attribute: PostId?): Long? = attribute?.value
    override fun convertToEntityAttribute(dbData: Long?): PostId? = dbData?.let { PostId(it) }
}

@Converter
class UrlConverter : AttributeConverter<Url, String> {
    override fun convertToDatabaseColumn(attribute: Url?): String? = attribute?.value
    override fun convertToEntityAttribute(dbData: String?): Url? = dbData?.let { Url(it) }
}

@Converter
class ContentConverter : AttributeConverter<Content, String> {
    override fun convertToDatabaseColumn(attribute: Content?): String? = attribute?.value
    override fun convertToEntityAttribute(dbData: String?): Content? = dbData?.let { Content(it) }
}

@Converter
class PostNumberConverter : AttributeConverter<PostNumber, Int> {
    override fun convertToDatabaseColumn(attribute: PostNumber?): Int? = attribute?.value
    override fun convertToEntityAttribute(dbData: Int?): PostNumber? = dbData?.let { PostNumber(it) }
}

@Converter
class PostCountConverter : AttributeConverter<PostCount, Int> {
    override fun convertToDatabaseColumn(attribute: PostCount?): Int? = attribute?.value
    override fun convertToEntityAttribute(dbData: Int?): PostCount? = dbData?.let { PostCount(it) }
}
