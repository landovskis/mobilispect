package com.mobilispect.backend.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** A [kotlinx.serialization.KSerializer] that knows about `yyyymmdd` formatted dates. */
object LocalDateSerializer : KSerializer<LocalDate> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

  override fun deserialize(decoder: Decoder): LocalDate {
    val decoded = decoder.decodeString()
    return LocalDate.parse(decoded, DateTimeFormatter.BASIC_ISO_DATE)
  }

  override fun serialize(encoder: Encoder, value: LocalDate) {
    val encoded = value.format(DateTimeFormatter.ISO_DATE)
    encoder.encodeString(encoded)
  }
}
