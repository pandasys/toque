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

package com.ealva.toque.service.vlc

import android.net.Uri
import com.ealva.ealvabrainz.brainz.data.ArtistMbid
import com.ealva.ealvabrainz.brainz.data.ReleaseGroupMbid
import com.ealva.ealvabrainz.brainz.data.ReleaseMbid
import com.ealva.ealvabrainz.brainz.data.TrackMbid
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.ealvalog.w
import com.ealva.toque.common.Millis
import com.ealva.toque.file.fileExtension
import com.ealva.toque.file.isFileScheme
import com.ealva.toque.file.isNetworkScheme
import com.ealva.toque.service.media.EmbeddedArtwork
import com.ealva.toque.service.media.MediaFileTagInfo
import com.ealva.toque.service.media.MediaMetadataParser
import com.ealva.toque.service.media.MediaMetadataParserFactory
import com.ealva.toque.service.media.StarRating
import com.ealva.toque.tag.ArtistParser
import com.ealva.toque.tag.ArtworkField
import com.ealva.toque.tag.SongTag
import com.ealva.toque.tag.toAlbumSort
import com.ealva.toque.tag.toArtistSort
import com.ealva.toque.tag.toTitleSort
import ealvatag.audio.SupportedFileFormat
import org.videolan.libvlc.interfaces.IMedia
import java.io.File
import kotlin.math.min

private val LOG by lazyLogger(MediaMetadataParser::class)

fun Uri.tagCanParseExtension(): Boolean =
  SupportedFileFormat.fromExtension(fileExtension) !== SupportedFileFormat.UNKNOWN

class VlcMediaMetadataParserFactory(
  private val libVlcSingleton: LibVlcSingleton
) : MediaMetadataParserFactory {
  override fun make(): MediaMetadataParser = MediaMetadataParserImpl(libVlcSingleton)
}

private class MediaMetadataParserImpl(private val libVlc: LibVlcSingleton) : MediaMetadataParser {
  override suspend fun parseMetadata(
    uri: Uri,
    artistParser: ArtistParser,
    ignoreArtwork: Boolean
  ): MediaFileTagInfo {
    return if (uri.isFileScheme() && uri.tagCanParseExtension()) {
      try {
        FileTagInfo(File(uri.path.orEmpty()), artistParser, ignoreArtwork)
      } catch (e: Exception) {
        LOG.e(e) { it("Could not open file '%s' uri=%s", uri.path.orEmpty(), uri) }
        libVlc.withInstance { VlcTagInfo(uri, it, artistParser) }
      }
    } else {
      LOG.w { it("Fallback to LibVLC as no parser found for %s", uri) }
      libVlc.withInstance { VlcTagInfo(uri, it, artistParser) }
    }
  }
}

private class FileTagInfo private constructor(
  private val tag: SongTag,
  private val artistParser: ArtistParser
) : MediaFileTagInfo {
  override val duration: Millis
    get() = Millis(tag.duration)
  override val title: String
    get() = tag.title
  override val titleSort: String
    get() = tag.titleSort
  override val artists: List<String>
    get() = artistParser.parseAll(tag.mediaArtists)
  override val artistsSort: List<String>
    get() = artistParser.parseAll(tag.mediaArtistsSort)
  override val album: String
    get() = tag.album
  override val albumSort: String
    get() = tag.albumSort
  override val albumArtist: String
    get() = tag.referenceAlbumArtist
  override val albumArtistSort: String
    get() = tag.referenceAlbumArtistSort
  override val composer: String
    get() = tag.composer
  override val composerSort: String
    get() = tag.composerSort
  override val genre: String
    get() = tag.genre
  override val genres: List<String>
    get() = tag.genre
      .splitToSequence("/")
      .filter { it.isNotBlank() }
      .mapTo(ArrayList()) { it.trim() }
  override val trackNumber: Int
    get() = tag.trackNumber
  override val totalTracks: Int
    get() = tag.totalTracks
  override val discNumber: Int
    get() = tag.discNumber
  override val totalDiscs: Int
    get() = tag.totalDiscs
  override val year: Int
    get() = tag.year
  override val rating: StarRating
    get() = tag.starRating
  override val comment: String
    get() = tag.comment
  override val lyrics: String
    get() = tag.getLyrics("")
  override val artistMbid: ArtistMbid?
    get() = tag.artistMbid?.toArtistMbid()
  override val releaseArtistMbid: ArtistMbid?
    get() = tag.releaseArtistMbid?.toArtistMbid()
  override val releaseMbid: ReleaseMbid?
    get() = tag.releaseMbid?.toReleaseMbid()
  override val trackMbid: TrackMbid?
    get() = tag.releaseTrackMbid?.toTrackMbid()
  override val releaseGroupMbid: ReleaseGroupMbid?
    get() = tag.releaseGroupMbid?.toReleaseGroupMbid()
  override val language: String
    get() = tag.getLanguage("")

  override val copyright: String = ""
  override val description: String = ""
  override val setting: String = ""
  override val nowPlaying: String = ""
  override val publisher: String = ""
  override val encodedBy: String = ""
  override val director: String = ""
  override val season: String = ""
  override val episode: String = ""
  override val showName: String = ""
  override val actors: String = ""
  override val fullDescription: String
    get() = tag.fullDescription
  override val embeddedArtwork: EmbeddedArtwork
    get() = SongTagEmbeddedArtwork(tag.artworkField)

  override fun toString(): String = asString()
  override fun close() {
    tag.close()
  }

  companion object {
    operator fun invoke(
      file: File,
      artistParser: ArtistParser,
      ignoreArtwork: Boolean = true
    ): MediaFileTagInfo = FileTagInfo(
      SongTag(
        file,
        ignoreArtwork = ignoreArtwork,
        createMissingTag = false
      ),
      artistParser
    )
  }
}

class SongTagEmbeddedArtwork(private val artwork: ArtworkField) : EmbeddedArtwork {
  override val exists: Boolean
    get() = artwork.exists()
  override val isUrl: Boolean
    get() = artwork.isUrl
  override val isBinary: Boolean
    get() = artwork.isBinaryData
  override val data: ByteArray
    get() = artwork.data
  override val url: String
    get() = artwork.url
  override val pictureType: String
    get() = artwork.pictureDescription
}

private const val END_OF_DATE_INDEX = 3

@Suppress("NOTHING_TO_INLINE")
private inline fun String?.orUnknown(): String = if (isNullOrBlank()) "Unknown" else this

private class VlcTagInfo private constructor(
  private val media: IMedia,
  artistParser: ArtistParser
) : MediaFileTagInfo {
  private fun meta(id: Int): String? = media.getMeta(id)
  private val _artists = artistParser.parseAll(listOf(meta(IMedia.Meta.Artist).orUnknown()))
  private val _artistsSort = _artists.map { it.toArtistSort() }
  override val duration: Millis
    get() = Millis(media.duration)
  override val title: String
    get() = meta(IMedia.Meta.Title).orUnknown()
  override val titleSort: String
    get() = title.toTitleSort()
  override val artists: List<String>
    get() = _artists
  override val artistsSort: List<String>
    get() = _artistsSort
  override val album: String
    get() = meta(IMedia.Meta.Album).orUnknown()
  override val albumSort: String
    get() = album.toAlbumSort()
  override val albumArtist: String
    get() = meta(IMedia.Meta.AlbumArtist).orUnknown()
  override val albumArtistSort: String
    get() = albumArtist.toArtistSort()
  override val composer: String = ""
  override val composerSort: String = ""
  override val genre: String
    get() = meta(IMedia.Meta.Genre).orUnknown()
  override val genres: List<String>
    get() = meta(IMedia.Meta.Genre)
      .orUnknown()
      .splitToSequence("/")
      .filter { it.isNotBlank() }
      .mapTo(ArrayList()) { it.trim() }
  override val trackNumber: Int
    get() = meta(IMedia.Meta.TrackNumber)?.toIntOrNull() ?: 0
  override val totalTracks: Int
    get() = meta(IMedia.Meta.TrackTotal)?.toIntOrNull() ?: 0
  override val discNumber: Int
    get() = meta(IMedia.Meta.DiscNumber)?.toIntOrNull() ?: 0
  override val totalDiscs: Int = 0

  override val year: Int
    get() {
      val date = meta(IMedia.Meta.Date).orEmpty()
      return date.substring(0, min(END_OF_DATE_INDEX, date.length)).toIntOrNull() ?: 0
    }
  override val rating: StarRating
    get() = StarRating.STAR_NONE
  override val comment: String = ""
  override val lyrics: String = ""
  override val artistMbid: ArtistMbid? = null
  override val releaseArtistMbid: ArtistMbid? = null
  override val releaseMbid: ReleaseMbid? = null
  override val trackMbid: TrackMbid? = null
  override val releaseGroupMbid: ReleaseGroupMbid? = null
  override val language: String
    get() = meta(IMedia.Meta.Language).orEmpty()
  override val copyright: String
    get() = meta(IMedia.Meta.Copyright).orEmpty()
  override val description: String
    get() = meta(IMedia.Meta.Description).orEmpty()
  override val setting: String
    get() = meta(IMedia.Meta.Setting).orEmpty()
  override val nowPlaying: String
    get() = meta(IMedia.Meta.NowPlaying).orUnknown()
  override val publisher: String
    get() = meta(IMedia.Meta.Publisher).orEmpty()
  override val encodedBy: String
    get() = meta(IMedia.Meta.EncodedBy).orEmpty()
  override val director: String
    get() = meta(IMedia.Meta.Director).orEmpty()
  override val season: String
    get() = meta(IMedia.Meta.Season).orEmpty()
  override val episode: String
    get() = meta(IMedia.Meta.Episode).orEmpty()
  override val showName: String
    get() = meta(IMedia.Meta.ShowName).orEmpty()
  override val actors: String
    get() = meta(IMedia.Meta.Actors).orEmpty()
  override val fullDescription: String
    get() = ""
  override val embeddedArtwork: EmbeddedArtwork
    get() = VlcEmbeddedArtwork()

  override fun close() {
    media.release()
  }

  override fun toString(): String = asString()

  companion object {
    operator fun invoke(
      uri: Uri,
      libVlc: LibVlc,
      artistParser: ArtistParser
    ): MediaFileTagInfo {
      return VlcTagInfo(
        libVlc.makeNativeMedia(uri).apply { if (!isParsed) parse(parseFlagFromUri(uri)) },
        artistParser
      )
    }

    private fun parseFlagFromUri(uri: Uri): Int =
      if (uri.isNetworkScheme()) PARSE_NETWORK else PARSE_LOCAL

    /** Parse metadata if the file is local. Doesn't bother with artwork */
    private const val PARSE_LOCAL = IMedia.Parse.ParseLocal

    /** Parse metadata even if over a network connection. Doesn't bother with artwork */
    private const val PARSE_NETWORK = IMedia.Parse.ParseNetwork
  }
}

fun StringBuilder.appendIndentedLine(title: String, obj: Any?, isLast: Boolean = false) {
  append("   ")
  append(title)
  append(':')
  if (isLast) {
    appendLine(obj)
  } else {
    append(obj)
    appendLine(',')
  }
}

fun MediaFileTagInfo.asString(): String {
  return buildString {
    appendLine("MediaMetadata[")
    appendIndentedLine("title", title)
    appendIndentedLine("titleSort", titleSort)
    appendIndentedLine("artists", artists)
    appendIndentedLine("artistsSort", artistsSort)
    appendIndentedLine("album", album)
    appendIndentedLine("albumSort", albumSort)
    appendIndentedLine("albumArtist", albumArtist)
    appendIndentedLine("albumArtistSort", albumArtistSort)
    appendIndentedLine("composer", composer)
    appendIndentedLine("composerSort", composerSort)
    appendIndentedLine("genres", genres)
    appendIndentedLine("trackNumber", trackNumber)
    appendIndentedLine("totalTracks", totalTracks)
    appendIndentedLine("discNumber", discNumber)
    appendIndentedLine("totalDiscs", totalDiscs)
    appendIndentedLine("year", year)
    appendIndentedLine("rating", rating)
    appendIndentedLine("comment", comment)
    appendIndentedLine("lyrics", lyrics)
    appendIndentedLine("artistMbid", artistMbid)
    appendIndentedLine("releaseArtistMbid", releaseArtistMbid)
    appendIndentedLine("releaseMbid", releaseMbid)
    appendIndentedLine("trackMbid", trackMbid)
    appendIndentedLine("releaseGroupMbid", releaseGroupMbid)
    appendIndentedLine("language", language)
    appendIndentedLine("copyright", copyright)
    appendIndentedLine("description", description)
    appendIndentedLine("setting", setting)
    appendIndentedLine("nowPlaying", nowPlaying)
    appendIndentedLine("publisher", publisher)
    appendIndentedLine("encodedBy", encodedBy)
    appendIndentedLine("director", director)
    appendIndentedLine("season", season)
    appendIndentedLine("episode", episode)
    appendIndentedLine("showName", showName)
    appendIndentedLine("actors", actors, true)
    appendLine("]")
  }
}

fun String.toArtistMbid() = ArtistMbid(this)
fun String.toReleaseMbid() = ReleaseMbid(this)
fun String.toReleaseGroupMbid() = ReleaseGroupMbid(this)
fun String.toTrackMbid() = TrackMbid(this)

class VlcEmbeddedArtwork : EmbeddedArtwork{
  override val exists: Boolean
    get() = false
  override val isUrl: Boolean
    get() = false
  override val isBinary: Boolean
    get() = false
  override val data: ByteArray
    get() = ByteArray(0)
  override val url: String
    get() = ""
  override val pictureType: String
    get() = ""
}
