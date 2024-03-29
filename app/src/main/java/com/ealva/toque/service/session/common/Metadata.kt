/*
 * Copyright 2021 Eric A. Snell
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ealva.toque.service.session.common

import android.net.Uri
import android.support.v4.media.RatingCompat
import androidx.core.net.toUri
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.toque.common.Millis
import com.ealva.toque.common.Rating
import com.ealva.toque.common.StarRating
import com.ealva.toque.common.Title
import com.ealva.toque.common.asMillis
import com.ealva.toque.persist.MediaId
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Serializable
data class Metadata(
  @Serializable(with = MediaIdAsLongSerializer::class)
  val id: MediaId,
  @Serializable(with = TitleAsStringSerializer::class)
  val title: Title,
  @Serializable(with = AlbumTitleAsStringSerializer::class)
  val albumTitle: AlbumTitle,
  @Serializable(with = ArtistNameAsStringSerializer::class)
  val albumArtist: ArtistName,
  @Serializable(with = ArtistNameAsStringSerializer::class)
  val artistName: ArtistName,
  @Serializable(with = DurationAsLongSerializer::class)
  val duration: Duration,
  val trackNumber: Int,
  @Serializable(with = UriAsStringSerializer::class)
  val localAlbumArt: Uri,
  @Serializable(with = UriAsStringSerializer::class)
  val albumArt: Uri,
  @Serializable(with = RatingAsIntSerializer::class)
  val rating: Rating,
  @Serializable(with = UriAsStringSerializer::class)
  val location: Uri,
  @Serializable(with = UriAsStringSerializer::class)
  val fileUri: Uri
) {
  val artwork: Uri
    get() = if (localAlbumArt !== Uri.EMPTY) localAlbumArt else albumArt

  /**
   * Indices is the Closed range of position values. Playback position occur outside this range
   */
  @Transient
  val playbackRange: ClosedRange<Millis> = Millis(0)..duration.inWholeMilliseconds.asMillis

  @OptIn(ExperimentalSerializationApi::class)
  fun toJsonString(): String {
    return if (this === NullMetadata) "" else Json.encodeToString(this)
  }

  companion object {
    @OptIn(ExperimentalSerializationApi::class)
    fun fromJsonString(serialized: String): Metadata {
      return if (serialized.isEmpty()) NullMetadata else Json.decodeFromString(serialized)
    }

    val NullMetadata = Metadata(
      MediaId.INVALID,
      Title(""),
      AlbumTitle(""),
      ArtistName(""),
      ArtistName(""),
      Duration.ZERO,
      -1,
      Uri.EMPTY,
      Uri.EMPTY,
      Rating.RATING_NONE,
      Uri.EMPTY,
      Uri.EMPTY
    )
  }
}

object MediaIdAsLongSerializer : KSerializer<MediaId> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("MediaId", PrimitiveKind.LONG)

  override fun serialize(encoder: Encoder, value: MediaId) = encoder.encodeLong(value())
  override fun deserialize(decoder: Decoder): MediaId = MediaId(decoder.decodeLong())
}

//object MillisAsLongSerializer : KSerializer<Millis> {
//  override val descriptor: SerialDescriptor =
//    PrimitiveSerialDescriptor("Millis", PrimitiveKind.LONG)
//
//  override fun serialize(encoder: Encoder, value: Millis) = encoder.encodeLong(value())
//  override fun deserialize(decoder: Decoder): Millis = Millis(decoder.decodeLong())
//}

object DurationAsLongSerializer : KSerializer<Duration> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("Duration", PrimitiveKind.LONG)

  override fun serialize(encoder: Encoder, value: Duration) =
    encoder.encodeLong(value.inWholeMilliseconds)

  override fun deserialize(decoder: Decoder): Duration =
    decoder.decodeLong().toDuration(DurationUnit.MILLISECONDS)
}

object UriAsStringSerializer : KSerializer<Uri> {
  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Uri", PrimitiveKind.STRING)
  override fun serialize(encoder: Encoder, value: Uri) = encoder.encodeString(value.toString())
  override fun deserialize(decoder: Decoder): Uri = decoder.decodeString().toUri()
}

object RatingAsIntSerializer : KSerializer<Rating> {
  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Rating", PrimitiveKind.INT)
  override fun serialize(encoder: Encoder, value: Rating) = encoder.encodeInt(value.value)
  override fun deserialize(decoder: Decoder): Rating = Rating(decoder.decodeInt())
}

object TitleAsStringSerializer : KSerializer<Title> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("Title", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: Title) = encoder.encodeString(value.value)
  override fun deserialize(decoder: Decoder): Title = Title(decoder.decodeString())
}

object AlbumTitleAsStringSerializer : KSerializer<AlbumTitle> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("AlbumTitle", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: AlbumTitle) =
    encoder.encodeString(value.value)

  override fun deserialize(decoder: Decoder): AlbumTitle = AlbumTitle(decoder.decodeString())
}

object ArtistNameAsStringSerializer : KSerializer<ArtistName> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("ArtistName", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: ArtistName) =
    encoder.encodeString(value.value)

  override fun deserialize(decoder: Decoder): ArtistName = ArtistName(decoder.decodeString())
}

fun StarRating.toCompat(): RatingCompat = if (isValid) {
  RatingCompat.newStarRating(RatingCompat.RATING_5_STARS, value)
} else {
  RatingCompat.newUnratedRating(RatingCompat.RATING_5_STARS)
}
