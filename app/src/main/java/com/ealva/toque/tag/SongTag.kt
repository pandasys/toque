/*
 * Copyright 2020 eAlva.com
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

@file:Suppress("MagicNumber")

package com.ealva.toque.tag

import android.net.Uri
import android.text.TextUtils
import androidx.annotation.WorkerThread
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.ealvalog.unaryPlus
import com.ealva.ealvalog.w
import com.ealva.toque.common.debug
import com.ealva.toque.media.MediaFormat
import com.ealva.toque.media.MediaType
import com.ealva.toque.media.Mp3Rating
import com.ealva.toque.media.STAR_NO_RATING
import com.ealva.toque.media.StarRating
import com.ealva.toque.media.toMp3Rating
import com.ealva.toque.media.toRating
import com.google.common.base.Optional
import com.google.common.base.Splitter
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.CacheStats
import com.google.common.collect.ImmutableSet
import ealvatag.audio.AudioFile
import ealvatag.audio.AudioFileIO
import ealvatag.audio.AudioHeader
import ealvatag.audio.NullAudioFile
import ealvatag.audio.exceptions.CannotReadException
import ealvatag.audio.exceptions.CannotWriteException
import ealvatag.audio.exceptions.InvalidAudioFrameException
import ealvatag.tag.FieldKey
import ealvatag.tag.NullTag
import ealvatag.tag.Tag
import ealvatag.tag.TagException
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit

private val LOG by lazyLogger(SongTag::class)

private val KHZ_DECIMAL_FORMAT = DecimalFormat("#.###")
private const val COMMA_SPACE = ", "

private fun StringBuilder.appendEncoding(header: AudioHeader) {
  append(header.encodingType)
}

private fun StringBuilder.appendFormattedChannels(channelCount: Int) {
  when (channelCount) {
    1 -> append("Mono")
    2 -> append("Stereo")
    else -> {
      append(channelCount)
      append(" chan")
    }
  }
}

private fun StringBuilder.appendFormattedBitRate(header: AudioHeader) {
  append(header.bitRate)
  if (header.isVariableBitRate) {
    append("kbps VBR")
  } else {
    append("kbps")
  }
}

private fun StringBuilder.appendFormattedSampleRate(header: AudioHeader) {
  append(KHZ_DECIMAL_FORMAT.format(header.sampleRate / 1000.0))
  append("kHz")
}

@Suppress("NOTHING_TO_INLINE")
private inline fun Optional<String>.orUnknown() = orNull().orUnknown()
@Suppress("NOTHING_TO_INLINE")
private inline fun String?.orUnknown(): String = if (isNullOrBlank()) SongTag.UNKNOWN else this

class SongTagField(val id: String, val contents: String) {
  override fun toString(): String {
    return "$id=\"$contents\""
  }
}

interface SongTagFieldIterator {
  fun onField(field: SongTagField): Boolean

  fun onError(message: String): Boolean
}

val UNKNOWN_SINGLETON = listOf(SongTag.UNKNOWN)

/**
 * Helper class to wrap the tag reader/writer library. Make it easier to use and wrap it to reduce
 * dependencies.
 *
 * Note: The timestamp fields are based on a subset of ISO 8601. When being as precise as possible
 * the format of a time string is yyyy-MM-ddTHH:mm:ss (year, "-", month, "-", day, "T", hour (out of
 * 24), ":", minutes, ":", seconds), but the precision may be reduced by removing as many time
 * indicators as wanted. Hence valid timestamps are yyyy, yyyy-MM, yyyy-MM-dd, yyyy-MM-ddTHH,
 * yyyy-MM-ddTHH:mm and yyyy-MM-ddTHH:mm:ss. All time stamps are UTC. For durations, use the slash
 * character as described in 8601, and for multiple non- contiguous dates, use multiple strings, if
 * allowed by the frame definition.
 *
 * The following are the tags we currently (2018-03-16) read and write.
 *  - Title
 *  - TitleSort (doesn't come from MusicBrainz)
 *  - Artist
 *  - ArtistSort
 *  - Album
 *  - AlbumSort
 *  - AlbumArtist
 *  - AlbumArtistSort
 *  - Composer
 *  - ComposerSort
 *  - Genre
 *  - Duration
 *  - TrackNumber
 *  - TotalTracks
 *  - DiscNumber
 *  - TotalDiscs
 *  - Year
 *  - Rating
 *  - Comment
 *  - ArtistMbid = Media (Track) Artist
 *  - ReleaseArtistMbid = Album Artist
 *  - ReleaseMbid = Album
 *  - TrackMbid
 *  - ReleaseGroupMbid
 *
 * Created by Eric on 6/15/2015.
 */
class SongTag(
  file: File,
  ignoreArtwork: Boolean,
//  prefs: AppPreferences,
//  private val images: Images,
  createMissingTag: Boolean
) : AutoCloseable {

  val path: String = file.path
  private val type: MediaFormat = MediaFormat.mediaFormatFromExtension(path)

  //  private val readSortFields: Boolean = prefs.readTagSortFields
  private val readSortFields = true
  private var modified: Boolean = false

  private var audioFile: AudioFile
  private val supportedFields: ImmutableSet<FieldKey>
  private var tag: Tag

  init {
    try {
      audioFile = if (ignoreArtwork) AudioFileIO.readIgnoreArtwork(file) else AudioFileIO.read(file)
      tag = if (createMissingTag) {
        audioFile.tagOrSetNewDefault
      } else {
        audioFile.tag.or(NullTag.INSTANCE)
      }

      supportedFields = tag.supportedFields
    } catch (e: CannotReadException) {
      LOG.w { it("Can't open %s", file) }
      throw IOException(e)
    } catch (e: IOException) {
      LOG.w { it("Can't open %s", file) }
      throw IOException(e)
    } catch (e: CannotWriteException) {
      LOG.w { it("Can't open %s", file) }
      throw IOException(e)
    } catch (e: TagException) {
      LOG.w { it("Can't open %s", file) }
      throw IOException(e)
    } catch (e: InvalidAudioFrameException) {
      LOG.w { it("Can't open %s", file) }
      throw IOException(e)
    } catch (e: NullPointerException) {
      LOG.w { it("Can't open %s", file) }
      throw IOException(e)
    }
  }

  var starRating: StarRating
    get() {
      val ratingString = ratingAsString ?: return STAR_NO_RATING
      return try {
        val value = ratingString.toInt()
        when (type) {
          MediaFormat.Flac, MediaFormat.OggVorbisAudio -> value.toRating().toStarRating()
          else -> Mp3Rating(value).toStarRating()
        }
      } catch (e: NumberFormatException) {
        ratingString.toMp3Rating().toStarRating()
      }
    }
    set(value) {
      if (value.value == STAR_NO_RATING.value) {
        deleteField(FieldKey.RATING)
      } else {
        when (type) {
          MediaFormat.Flac, MediaFormat.OggVorbisAudio -> {
            setField(FieldKey.RATING, value.toRating().value)
          }
          else -> {
            setField(FieldKey.RATING, value.toMp3Rating().value)
          }
        }
      }
    }

  // some tags have more than just a 4 digit year, see class documentation.
  val year: Int
    get() {
      val yearText = yearString
      try {
        if (yearText.isNotEmpty()) {
          return Integer.parseInt(yearText.substring(0, yearText.length.coerceAtMost(4)))
        }
      } catch (e: NumberFormatException) {
        LOG.e {
          it(
            "Could not convert YEAR with value '%s' to an integer. %s %s",
            yearText,
            type,
            path
          )
        }
      }

      return 0
    }

  private val ratingAsString: String?
    get() = tag.getValue(FieldKey.RATING, 0).orNull()

  val fullDescription: String
    get() {
      return buildString(512) {
        try {
          val header = header
          appendEncoding(header)
          append(COMMA_SPACE)

          append(header.format)
          append(COMMA_SPACE)

          appendFormattedChannels(header.channelCount)
          append(COMMA_SPACE)

          appendFormattedBitRate(header)
          append(COMMA_SPACE)

          appendFormattedSampleRate(header)
          append(COMMA_SPACE)

          append(getTrackDuration(TimeUnit.SECONDS))
          append(" sec, ")

          append(audioFile.file.length() / 1000)
          append("kB")
        } catch (e: Exception) {
          val cause: Throwable = e.cause ?: e
          append("Error:").append(cause.message)
        }
      }
    }

  val artworkData: ByteArray?
    get() {
      val artwork = tag.firstArtwork.orNull()
      return artwork?.binaryData
    }

  val artworkField: ArtworkField
    get() = ArtworkField(tag.firstArtwork.orNull())

  val mediaType: MediaType
    get() = MediaType.Audio

  val nowPlaying: String
    get() = referenceAlbumArtist

  val duration: Long
    get() = getTrackDuration(TimeUnit.MILLISECONDS)

  var trackNumber: Int
    get() = getFirstAsInt(FieldKey.TRACK, FallbackSplit.First)
    set(value) {
      setField(FieldKey.TRACK, value)
    }

  var totalTracks: Int
    get() = getFirstAsInt(FieldKey.TRACK_TOTAL, FallbackSplit.Second)
    set(value) {
      setField(FieldKey.TRACK_TOTAL, value)
    }

  val title: String
    get() = tag.getValue(FieldKey.TITLE, 0).orUnknown()

  val titleSort: String
    get() = if (readSortFields)
      tag.getValue(FieldKey.TITLE_SORT, 0).or(title.toTitleSort())
    else
      title.toTitleSort()

  val album: String
    get() = tag.getValue(FieldKey.ALBUM, 0).orUnknown()

  var albumSort: String
    get() = if (readSortFields) {
      tag.getValue(FieldKey.ALBUM_SORT, 0).or(album.toAlbumSort()).orUnknown()
    } else {
      album.toAlbumSort()
    }
    set(value) {
      tag.setField(FieldKey.ALBUM_SORT, value)
    }

  val referenceAlbumArtist: String
    get() = tag.getValue(FieldKey.ALBUM_ARTIST, 0)
      .or(mediaArtists.first())
      .orUnknown()

  val referenceAlbumArtistSort: String
    get() = tag.getValue(FieldKey.ALBUM_ARTIST_SORT)
      .or(referenceAlbumArtist.toArtistSort())
      .orUnknown()

  val mediaArtists: List<String>
    get() = try {
      tag.getAll(FieldKey.ARTIST)
        .filterNot { it.isNullOrEmpty() }
        .ifEmpty { UNKNOWN_SINGLETON }
    } catch (e: Exception) {
      LOG.e(e) { +it }
      UNKNOWN_SINGLETON
    }

  val mediaArtistsSort: List<String>
    get() = try {
      tag.getAll(FieldKey.ARTIST_SORT)
        .filterNot { it.isNullOrEmpty() }
        .ifEmpty { mediaArtists }
        .map { it.toArtistSort() }
    } catch (e: Exception) {
      LOG.e(e) { +it }
      UNKNOWN_SINGLETON
    }

  val genre: String
    get() = tag.getValue(FieldKey.GENRE, 0).orUnknown()

  var totalDiscs: Int
    get() = getFirstAsInt(FieldKey.DISC_TOTAL, FallbackSplit.Second)
    set(value) {
      setField(FieldKey.DISC_TOTAL, value)
    }

  val width: Int
    get() = 0

  val height: Int
    get() = 0

  private val yearString: String
    get() = tag.getValue(FieldKey.YEAR, 0).or("")

  val composer: String
    get() = tag.getValue(FieldKey.COMPOSER, 0).orUnknown()

  val composerSort: String
    get() = if (readSortFields)
      tag.getValue(FieldKey.COMPOSER_SORT, 0).or(composer)
    else
      composer

  var discNumber: Int
    get() = getFirstAsInt(FieldKey.DISC_NO, FallbackSplit.First)
    set(value) {
      setField(FieldKey.DISC_NO, value)
    }

  val comment: String
    get() = tag.getValue(FieldKey.COMMENT, 0).or("")

  val acousticId: String
    get() = tag.getFirst(FieldKey.ACOUSTID_ID)

  val acousticIdFingerprint: String
    get() = tag.getFirst(FieldKey.ACOUSTID_FINGERPRINT)

  private val header: AudioHeader
    get() = audioFile.audioHeader

  fun iterateAllFields(iterator: SongTagFieldIterator) {
    try {
      tag.fields.forEach { field ->
        iterator.onField(SongTagField(field.id, field.toString()))
      }
    } catch (e: Exception) {
      LOG.e(e) { +it }
      iterator.onError(e.localizedMessage ?: e.toString())
    }
  }

  fun supportsField(fieldKey: FieldKey): Boolean {
    return supportedFields.contains(fieldKey)
  }

  private fun setModified() {
    this.modified = true
  }

  fun hasBeenModified(): Boolean {
    return modified
  }

  fun setCharset(encoding: Charset) {
    try {
      tag.setEncoding(encoding)
    } catch (e: Exception) {
      LOG.e(e) { it("Setting charset in %s %s", path, fullDescription) }
      // throw SongTagException(e)
    }
  }

  fun getTrackDuration(asUnit: TimeUnit): Long {
    return header.getDuration(asUnit, true)
  }

  private fun deleteField(key: FieldKey): Boolean {
    return try {
      tag.deleteField(key)
      setModified()
      true
    } catch (ex: Exception) {
      LOG.e { it("Could not delete %s. %s %s", key, type, path) }
      false
    }
  }

  fun saveChanges() {
    try {
      audioFile.save()
    } catch (e: CannotWriteException) {
      throw IOException(e)
    }
  }

  fun hasArtwork(): Boolean {
    return tag.supportedFields.contains(FieldKey.COVER_ART) && tag.hasField(FieldKey.COVER_ART)
  }

  fun removeArtwork(): Boolean {
    return try {
      tag.deleteArtwork()
      setModified()
      true
    } catch (e: RuntimeException) {
      LOG.e(e) { it("Could not delete artwork. %s %s", type, path) }
      false
    }
  }

  @WorkerThread
  fun setArtwork(@Suppress("UNUSED_PARAMETER") artworkUri: Uri): Boolean {
    TODO()
//    artworkUri.path?.let { pathname ->
//
//      try {
//        val tag = this.tag
//        var artwork: Artwork? = null
//
//        if (ContentResolver.SCHEME_FILE == artworkUri.scheme) {
//          val mediaFormat = MediaFormat.getFormatFromExtension(artworkUri.fileExtension)
//          if (mediaFormat == MediaFormat.Jpeg) {
//            artwork = ArtworkFactory.createArtworkFromFile(File(pathname))
//          }
//        }
//
//        if (artwork == null) {
//          val newArtwork = ArtworkFactory.getNew()
//          val imageData: Pair<ByteArray, Size2> = images.getAsJpeg(artworkUri, ORIGINAL_SIZE)
//          newArtwork.binaryData = imageData.first
//          newArtwork.width = imageData.second.width
//          newArtwork.height = imageData.second.height
//          newArtwork.mimeType = ImageFormats.getMimeTypeForBinarySignature(imageData.first)
//          newArtwork.description = ""
//          newArtwork.pictureType = PictureTypes.DEFAULT_ID
//          artwork = newArtwork
//        }
//
//        // remove any old artwork before adding the new one
//        val artworkList = tag.artworkList
//        if (artworkList != null && artworkList.size > 0) {
//          tag.deleteArtwork()
//          setModified()
//        }
//        tag.addArtwork(artwork)
//        setModified()
//        return true
//      } catch (e: UnsupportedOperationException) {
//        LOG.e(e) { +it }
//        throw e
//      } catch (e: FieldDataInvalidException) {
//        LOG.e { it("Could not set artwork. %s %s", type, path) }
//      } catch (e: IOException) {
//        LOG.e(e) { it("Could not set artwork. %s %s", type, path) }
//      }
//    }
//    return false
  }

  fun getLyrics(defValue: String): String {
    return tag.getValue(FieldKey.LYRICS, 0).or(defValue)
  }

  fun setLyrics(lyrics: String?): Boolean {
    return setOrDeleteField(FieldKey.LYRICS, lyrics)
  }

  fun setGenre(genre: String?): Boolean {
    return setOrDeleteField(FieldKey.GENRE, genre)
  }

  fun setTrack(track: Int): Boolean {
    return setOrDeleteField(FieldKey.TRACK, if (track > 0) track.toString() else "")
  }

  internal enum class FallbackSplit {
    First,
    Second
  }

  fun setTotalTracks(trackCount: Int): Boolean {
    return setOrDeleteField(FieldKey.TRACK_TOTAL, if (trackCount > 0) trackCount.toString() else "")
  }

  fun setDisc(disc: Int): Boolean {
    return setOrDeleteField(FieldKey.DISC_NO, if (disc > 0) disc.toString() else "")
  }

  fun setTotalDiscs(discCount: Int): Boolean {
    return setOrDeleteField(FieldKey.DISC_TOTAL, if (discCount > 0) discCount.toString() else "")
  }

  fun getTitle(defaultValue: String): String {
    return tag.getValue(FieldKey.TITLE, 0).or(defaultValue)
  }

  fun setTitle(title: String): Boolean {
    return setField(FieldKey.TITLE, title)
  }

  fun setYear(year: Int): Boolean {
    return setOrDeleteField(FieldKey.YEAR, if (year > 0) year.toString() else "")
  }

  fun setArtist(artist: String?): Boolean {
    return setOrDeleteField(FieldKey.ARTIST, artist)
  }

  fun setAlbum(album: String): Boolean {
    return setField(FieldKey.ALBUM, album)
  }

  fun setAlbumArtist(albumArtist: String): Boolean {
    return setField(FieldKey.ALBUM_ARTIST, albumArtist)
  }

  fun setComment(comment: String?): Boolean {
    return setOrDeleteField(FieldKey.COMMENT, comment)
  }

  fun setEncoder(encoder: String?): Boolean {
    return setOrDeleteField(FieldKey.ENCODER, encoder)
  }

  fun getLanguage(defaultValue: String): String {
    return tag.getValue(FieldKey.LANGUAGE, 0).or(defaultValue)
  }

  fun setLanguage(language: String?): Boolean {
    return setOrDeleteField(FieldKey.LANGUAGE, language)
  }

  var artistMbid: String?
    get() = tag.getValue(FieldKey.MUSICBRAINZ_ARTISTID).orNull()
    set(value) {
      tag.setField(FieldKey.MUSICBRAINZ_ARTISTID, value ?: "")
    }
  var releaseArtistMbid: String?
    get() = tag.getValue(FieldKey.MUSICBRAINZ_RELEASEARTISTID).orNull()
    set(value) {
      tag.setField(FieldKey.MUSICBRAINZ_RELEASEARTISTID, value ?: "")
    }
  var releaseMbid: String?
    get() = tag.getValue(FieldKey.MUSICBRAINZ_RELEASEID).orNull()
    set(value) {
      tag.setField(FieldKey.MUSICBRAINZ_RELEASEID, value ?: "")
    }
  var releaseTrackMbid: String?
    get() = tag.getValue(FieldKey.MUSICBRAINZ_RELEASE_TRACK_ID).orNull()
    set(value) {
      tag.setField(FieldKey.MUSICBRAINZ_RELEASE_TRACK_ID, value ?: "")
    }
  var releaseGroupMbid: String?
    get() = tag.getValue(FieldKey.MUSICBRAINZ_RELEASE_GROUP_ID).orNull()
    set(value) {
      tag.setField(FieldKey.MUSICBRAINZ_RELEASE_GROUP_ID, value ?: "")
    }

  @Suppress("ReturnCount")
  /**
   * Had a field not parsed correctly by tag lib and returned 10/10, which we'll now catch and
   * parse
   */
  private fun getFirstAsInt(key: FieldKey, fallbackSplit: FallbackSplit): Int {
    val fieldValue = tag.getValue(key, 0)
    if (fieldValue.isPresent) {
      val value = fieldValue.or("0").trim { it <= ' ' } // "0" not necessary, but safe
      try {
        return Integer.parseInt(value)
      } catch (e: NumberFormatException) {
        @Suppress("UnstableApiUsage")
        val list = Splitter.on('/')
          .trimResults()
          .omitEmptyStrings()
          .splitToList(value)
        try {
          if (fallbackSplit == FallbackSplit.First && list.isNotEmpty()) {
            return Integer.parseInt(list[0])
          } else {
            if (fallbackSplit == FallbackSplit.Second && list.size > 1) {
              return Integer.parseInt(list[1])
            }
          }
        } catch (ignored: NumberFormatException) {
          LOG.e {
            it(
              "Could not convert field %s with %s to an integer. %s %s",
              key,
              fieldValue.or("UNKNOWN"),
              type,
              path
            )
          }
        }
      }
    }
    return 0
  }

  override fun close() {
    tag = NullTag.INSTANCE
    audioFile = NullAudioFile.INSTANCE
  }

  private fun setField(key: FieldKey, intVal: Int) {
    setField(key, intVal.toString())
  }

  private fun setOrDeleteField(fieldKey: FieldKey, value: String?): Boolean {
    return if (!value.isNullOrEmpty()) {
      setField(fieldKey, value)
    } else {
      deleteField(fieldKey)
    }
  }

  /**
   * @param fieldKey key to use
   * @param value    cannot be null or empty string
   *
   * @return true if field set, false on error
   *
   * @throws IllegalArgumentException thrown if value is empty
   */
  private fun setField(fieldKey: FieldKey, value: String): Boolean {
    if (TextUtils.isEmpty(value)) {
      throw IllegalArgumentException()
    }
    return try {
      tag.setField(fieldKey, value)
      setModified()
      true
    } catch (e: UnsupportedOperationException) {
      LOG.w(e) { it("Could not set %s to %s. %s %s", fieldKey, value, type, path) }
      if (FieldKey.ALBUM_ARTIST == fieldKey) {
        throw e
      } else {
        false
      }
    } catch (e: Exception) {
      LOG.w { it("Could not set %s to %s. %s %s", fieldKey, value, type, path) }
      false
    }
  }

  companion object {
    const val UNKNOWN = "Unknown"

    fun make(
      file: File,
//      prefs: AppPreferences,
//      images: Images,
      createMissingTag: Boolean
    ): SongTag {
//      return SongTag(file, false, prefs, images, createMissingTag)
      return SongTag(file, ignoreArtwork = false, createMissingTag = createMissingTag)
    }

    fun makeIgnoreArtwork(file: File): SongTag {
      return SongTag(file, ignoreArtwork = true, createMissingTag = false)
    }

//    fun makeIgnoreArtwork(file: File, prefs: AppPreferences, images: Images): SongTag {
//      return SongTag(file, true, prefs, images, false)
//    }
  }
}

/**
 * If [isBlank] returns "", else strips any leading non letter or digit chars and
 * returns the result. If consists of all non letter or digit chars, returns self
 */
fun String.toTitleSort(): String {
  if (isBlank()) {
    return ""
  }
  val substringIndex = indexOfFirst { it.isLetterOrDigit() }
  return if (substringIndex >= 0) substring(substringIndex) else this
}

/**
 * Convert this to an artist sort field, caching the result to prevent many instances. Strips
 * prefix "The ", it exists, and appends it to the end. "The Beatles" becomes "Beatles, The". If
 * [isBlank], returns ""
 */
fun String.toArtistSort(): String {
  return artistSortCache.getUnchecked(this)
}

/**
 * Convert this to an album sort field, caching the result to prevent many instances. Strips
 * any non letter or digit prefix, unless only consists of non letter or digits. If [isBlank]
 * returns ""
 */
fun String.toAlbumSort(): String {
  return albumSortCache.getUnchecked(this)
}

private const val ARTICLE = "The "
private const val ARTICLE_LENGTH = ARTICLE.length
private val artistSortCache = CacheBuilder.newBuilder()
  .maximumSize(2000)
  .expireAfterAccess(3, TimeUnit.MINUTES)
  .also { debug { it.recordStats() } }
  .build(object : CacheLoader<String, String>() {
    override fun load(artist: String): String {
      return if (artist.isBlank()) {
        ""
      } else {
        if (artist.startsWith(ARTICLE)) {
          "${artist.substring(ARTICLE_LENGTH)}, ${artist.substring(0, ARTICLE_LENGTH - 1)}"
        } else {
          artist
        }
      }
    }
  })

private val albumSortCache = CacheBuilder.newBuilder()
  .maximumSize(2000)
  .expireAfterAccess(3, TimeUnit.MINUTES)
  .also { debug { it.recordStats() } }
  .build(object : CacheLoader<String, String>() {
    override fun load(album: String): String {
      if (album.isBlank()) {
        return ""
      }
      val substringIndex = album.indexOfFirst { it.isLetterOrDigit() }
      return if (substringIndex >= 0) album.substring(substringIndex) else album
    }
  })

val artistSortCacheStats: CacheStats
  get() = artistSortCache.stats()

val albumSortCacheStats: CacheStats
  get() = albumSortCache.stats()
