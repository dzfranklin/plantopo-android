package com.plantopo.plantopo.recording.data.model

import com.plantopo.plantopo.recording.data.db.RecordingStatus
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = RecordingSerializer::class)
data class Recording(
    val meta: RecordingMeta,
    val points: List<TrackPoint>
) {
    val id: String
        get() = meta.id
}

@Serializable
private data class RecordingSurrogate(
    val id: String,
    val name: String?,
    val startTime: Long,
    val endTime: Long?,
    val status: RecordingStatus,
    val pointCount: Int,
    val points: List<TrackPoint>
)

object RecordingSerializer : KSerializer<Recording> {
    override val descriptor: SerialDescriptor = RecordingSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Recording) {
        val surrogate = RecordingSurrogate(
            id = value.meta.id,
            name = value.meta.name,
            startTime = value.meta.startTime,
            endTime = value.meta.endTime,
            status = value.meta.status,
            pointCount = value.meta.pointCount,
            points = value.points
        )
        encoder.encodeSerializableValue(RecordingSurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): Recording {
        val surrogate = decoder.decodeSerializableValue(RecordingSurrogate.serializer())
        return Recording(
            meta = RecordingMeta(
                id = surrogate.id,
                name = surrogate.name,
                startTime = surrogate.startTime,
                endTime = surrogate.endTime,
                status = surrogate.status,
                pointCount = surrogate.pointCount
            ),
            points = surrogate.points
        )
    }
}