package com.mobilispect.backend.websocket

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.model.ids.ImportId
import java.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Real-time import progress data sent to clients via WebSocket */
@Serializable
data class ImportProgress(
  @Serializable(with = ImportIdSerializer::class) val importId: ImportId,
  @Serializable(with = FeedIdSerializer::class) val feedId: FeedId,
  val currentStep: String,
  val error: String? = null,
)

/** Progress update message wrapper */
@Serializable
data class ProgressUpdate(
  val progress: ImportProgress? = null,
  val completed: Boolean = false,
  val error: String? = null,
  @Serializable(with = InstantSerializer::class) val finishedAt: Instant? = null,
  val durationSeconds: Long? = null,
)

/** Active imports response */
@Serializable
data class ActiveImportsResponse(val activeImports: List<String>, val error: String? = null)

/** Progress request message */
@Serializable
data class ProgressRequest(val importId: String)

object ImportIdSerializer : KSerializer<ImportId> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("ImportId", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: ImportId) {
    encoder.encodeString(value.toString())
  }

  override fun deserialize(decoder: Decoder): ImportId =
    ImportId.fromString(decoder.decodeString())
}

object FeedIdSerializer : KSerializer<FeedId> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("FeedId", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: FeedId) {
    encoder.encodeString(value.toString())
  }

  override fun deserialize(decoder: Decoder): FeedId = FeedId(decoder.decodeString())
}

object InstantSerializer : KSerializer<Instant> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: Instant) {
    encoder.encodeString(value.toString())
  }

  override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}
