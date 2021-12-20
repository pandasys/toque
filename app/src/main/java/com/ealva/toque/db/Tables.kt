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

@file:Suppress("MemberVisibilityCanBePrivate")

package com.ealva.toque.db

import com.ealva.toque.db.MediaTable.mediaType
import com.ealva.toque.db.QueueStateTable.id
import com.ealva.toque.db.smart.SmartPlaylistRuleTable
import com.ealva.toque.db.smart.SmartPlaylistTable
import com.ealva.welite.db.table.ForeignKeyAction
import com.ealva.welite.db.table.Table

val setOfAllTables = setOf(
  ArtistTable,
  AlbumTable,
  ArtistAlbumTable,
  MediaTable,
  ArtistMediaTable,
  ComposerTable,
  ComposerMediaTable,
  GenreTable,
  GenreMediaTable,
  EqPresetTable,
  EqPresetAssociationTable,
  QueueTable,
  QueueItemsTable,
  QueueStateTable,
  PlayListTable,
  PlayListMediaTable,
  SmartPlaylistTable,
  SmartPlaylistRuleTable
)

object ArtistTable : Table() {
  val id = long("ArtistId") { primaryKey() }
  val artistName = text("Artist") { collateNoCase() }
  val artistSort = text("ArtistSort") { collateNoCase() }
  val artistImage = text("ArtistImageUri") { default("") }
  val artistMbid = text("ArtistMbid") { default("") }
  val lastArtSearchTime = long("ArtistLastArtSearch") { default(0L) }
  val createdTime = long("ArtistCreated")
  val updatedTime = long("ArtistTimeUpdated") { default(0L) }

  init {
    uniqueIndex(artistName) // artist may appear only once
  }
}

object AlbumTable : Table() {
  val id = long("AlbumId") { primaryKey() }
  val albumTitle = text("Album") { collateNoCase() }
  val albumSort = text("AlbumSort") { collateNoCase() }
  val albumArtist = text("AlbumArtist") { collateNoCase() }
  val albumLocalArtUri = text("AlbumLocalArtUri") { default("") }
  val albumArtUri = text("AlbumArtUri") { default("") }
  val releaseMbid = text("AlbumReleaseMbid") { default("") }
  val releaseGroupMbid = text("AlbumReleaseGroupMbid") { default("") }
  val lastArtSearchTime = long("AlbumLastArtSearchTime") { default(0L) }
  val createdTime = long("AlbumCreated")
  val updatedTime = long("AlbumUpdated") { default(0L) }

  init {
    uniqueIndex(albumTitle, albumArtist)
  }
}

/**
 * Relationship between and Album and an Album Artist. There can be only 1 Album but an Album
 * Artist may have many Albums.
 */
object ArtistAlbumTable : Table() {
  val artistId = reference(
    "ArtistAlbum_ArtistId",
    ArtistTable.id,
    onDelete = ForeignKeyAction.CASCADE
  )
  val albumId = reference(
    "ArtistAlbum_AlbumId",
    AlbumTable.id,
    onDelete = ForeignKeyAction.CASCADE
  )
  val createdTime = long("ArtistAlbumCreated")
  override val primaryKey = PrimaryKey(artistId, albumId)

  init {
    uniqueIndex(albumId)
  }
}

/**
 * Table of all media, both video and audio. Don't have separate tables for audio/video because some
 * video may be played as audio. A lot of the metadata stored in this table is specific to the
 * [mediaType]. When video is played as audio some substitutions will need to be made for display
 * purposes and some metadata will be unavailable as it doesn't exist for video.
 */
@Suppress("unused")
object MediaTable : Table() {
  val id = long("MediaId") { primaryKey() }

  /** Location of the media - need not be local to the device. */
  val location = text("MediaUri")

  /**
   * If media is a local file and MediaStore returns the MediaStore.MediaColumns.DATA column, it
   * is stored here.
   */
  val fileUri = text("MediaFile")

  /**
   * Name of the file as returned by the MediaStore. Does NOT have path information. Can be
   * displayed to the user or used to check the file extension:
   * ```displayName.substringAfterLast('.', "")```
   * returns the file extension without the dot '.' or an empty string if there is no file
   * extension.
   */
  val displayName = text("MediaDisplayName")

  /**
   * The type of media, [com.ealva.toque.service.media.MediaType] - audio or video
   */
  val mediaType = integer("MediaType")

  /** This is the id of a MediaFormat enum instance */
  val mediaFormat = integer("MediaFormat")
  val title = text("MediaTitle") { collateNoCase() }
  val titleSort = text("MediaTitleSort") { collateNoCase() }
  val albumId = reference("Media_AlbumId", AlbumTable.id)

  /** Artist associated with the album */
  val albumArtistId = reference("Media_AlbumArtistId", ArtistTable.id)

  /** Primary track artist. Primary = first in the list of artists */
  val artistId = reference("Media_ArtistId", ArtistTable.id)

  /**
   * Redundant data also stored parsed in GenreTable. This is to make smart playlist queries more
   * simple and for fast display of genres not parsed, ie. could have several genres joined in 1
   * string
   */
  val genre = text("MediaGenre") { collateNoCase() }

  val year = integer("MediaYear") { default(0) }
  val rating = integer("MediaRating") { default(-1) }
  val duration = long("MediaDuration") { default(0) }
  val trackNumber = integer("MediaTrackNumber") { default(0) }
  val totalTracks = integer("MediaTotalTracks") { default(0) }
  val discNumber = integer("MediaDiscNumber") { default(0) }
  val totalDiscs = integer("MediaTotalDiscs") { default(0) }
  val lastPlayedTime = long("MediaLastPlayedTime") { default(0) }
  val playedCount = integer("MediaPlayedCount") { default(0) }
  val lastSkippedTime = long("MediaLastSkippedTime") { default(0) }
  val skippedCount = integer("MediaSkippedCount") { default(0) }
  val bookmarkPosition = long("MediaBookmarkPosition") { default(0) }
  val createdTime = long("MediaTimeCreated")
  val updatedTime = long("MediaTimeUpdated") { default(0) }
  val contentStart = long("MediaContentStart") { default(-1) }
  val contentEnd = long("MediaContentEnd") { default(-1) }
  val comment = text("MediaContentComment") { default("") }
  val trackMbid = text("MediaTrackMbid") { default("") }
  val copyright = text("MediaCopyright") { default("") }
  val description = text("MediaDescription") { default("") }
  val setting = text("MediaSetting") { default("") }
  val language = text("MediaLanguage") { default("") }
  val nowPlaying = text("MediaNowPlaying") { default("") }
  val publisher = text("MediaPublisher") { default("") }
  val encodedBy = text("MediaEncodedBy") { default("") }
  val director = text("MediaDirector") { default("") }
  val season = text("MediaSeason") { default("") }
  val episode = text("MediaEpisode") { default("") }
  val showName = text("MediaShowName") { default("") }
  val actors = text("MediaActors") { default("") }

  init {
    // several of these indices exist for faster smart playlist functionality
    uniqueIndex(location) // media may appear only once
    uniqueIndex(fileUri)
    /*
     * Note: Adding all of the following indices resulted in a worst case cost of approx 3-4%
     * inserting over 2500 during initial scanning, which includes parsing all metadata (on a Galaxy
     * S10e, all media residing on internal storage). So, we'll keep the indices given many of them
     * greatly speed up complex smart playlist queries, especially when smart playlists refer to
     * other smart playlists.
     */
    index(genre)
    index(mediaType) // to find all audio or all video
    index(albumId) // for quick album info
    index(artistId) // to quickly find first track artist
    index(albumArtistId) // find album artist media (currently no association table for this)
    index(title) // quickly find a somewhat familiar title (eg. LIKE %Sunshine%)
    index(year) // smart playlist query (eg. find media in the 1970s)
    index(rating) // smart playlist query (eg. find all unrated or find all 5 star)
    index(createdTime) // smart playlist query (eg. find all "recently" added)
    index(playedCount) // smart playlist query (eg. find media not being played)
    index(lastPlayedTime) // smart playlist query (eg. find media not played in the last n days)
    index(skippedCount) // smart playlist query (eg. find music with lower interest)
    index(lastSkippedTime) // smart playlist query (eg. find music "recently" skipped)
    index(duration) // smart playlist query (eg. find all songs longer than 5 minutes)
    index(comment) // smart playlist query (eg. user can use this a a freeform search area)
  }
}

object ComposerTable : Table() {
  val id = long("ComposerId") { primaryKey() }
  val composer = text("Composer") { collateNoCase() }
  val composerSort = text("ComposerSort") { collateNoCase() }
  val createdTime = long("ComposerCreated")

  init {
    uniqueIndex(composer)
  }
}

object ComposerMediaTable : Table() {
  val composerId = reference(
    "ComposerMedia_ComposerId",
    ComposerTable.id,
    onDelete = ForeignKeyAction.CASCADE
  )
  val mediaId = reference(
    "ComposerMedia_MediaId",
    MediaTable.id,
    onDelete = ForeignKeyAction.CASCADE
  )
  val createdTime = long("ComposerMediaCreated")
  override val primaryKey = PrimaryKey(composerId, mediaId)

  init {
    index(mediaId)
  }
}

object GenreTable : Table() {
  val id = long("GenreId") { primaryKey() }
  val genre = text("Genre") { collateNoCase() }
  val createdTime = long("GenreCreated")

  init {
    uniqueIndex(genre) // a genre should only appear once
  }
}

object GenreMediaTable : Table() {
  val genreId = reference("GenreMedia_GenreId", GenreTable.id, ForeignKeyAction.CASCADE)
  val mediaId = reference(
    "GenreMedia_MediaId",
    MediaTable.id,
    onDelete = ForeignKeyAction.CASCADE
  )
  val createdTime = long("GenreMediaCreated")
  override val primaryKey: PrimaryKey = PrimaryKey(genreId, mediaId)

  init {
    index(mediaId) // to query all genre for a piece of media
  }
}

/**
 * Contains association between an artist and a particular piece of media - a "Track". Album
 * artists appear in this table too but the relationship ID is kept directly in the MediaTable -
 * [MediaTable.albumArtistId]
 */
object ArtistMediaTable : Table() {
  val artistId = reference(
    "ArtistMedia_ArtistId",
    ArtistTable.id,
    onDelete = ForeignKeyAction.CASCADE
  )
  val mediaId = reference(
    "ArtistMedia_MediaId",
    MediaTable.id,
    onDelete = ForeignKeyAction.CASCADE
  )
  val createdTime = long("ArtistMediaCreated")
  override val primaryKey = PrimaryKey(artistId, mediaId)

  init {
    index(mediaId) // quickly find all artists for a piece of media
  }
}

object EqPresetTable : Table() {
  val id = long("PresetId") { primaryKey().autoIncrement() }
  val presetName = text("PresetName")
  val preAmp = float("PresetPreAmp") { default(DEFAULT_PREAMP_VALUE) }

  /** 31 Hz */
  val band0 = float("PresetBand0") { default(DEFAULT_BAND_VALUE) }

  /** 63 Hz */
  val band1 = float("PresetBand1") { default(DEFAULT_BAND_VALUE) }

  /** 125 Hz */
  val band2 = float("PresetBand2") { default(DEFAULT_BAND_VALUE) }

  /** 250 Hz */
  val band3 = float("PresetBand3") { default(DEFAULT_BAND_VALUE) }

  /** 500 Hz */
  val band4 = float("PresetBand4") { default(DEFAULT_BAND_VALUE) }

  /** 1 kHz */
  val band5 = float("PresetBand5") { default(DEFAULT_BAND_VALUE) }

  /** 2 kHz */
  val band6 = float("PresetBand6") { default(DEFAULT_BAND_VALUE) }

  /** 4 kHz */
  val band7 = float("PresetBand7") { default(DEFAULT_BAND_VALUE) }

  /** 8 kHz */
  val band8 = float("PresetBand8") { default(DEFAULT_BAND_VALUE) }

  /** 16 kHz */
  val band9 = float("PresetBand9") { default(DEFAULT_BAND_VALUE) }

  val updatedTime = long("EqPresetTimeUpdated") { default(0L) }

  val bandColumns = arrayOf(band0, band1, band2, band3, band4, band5, band6, band7, band8, band9)

  const val DEFAULT_PREAMP_VALUE: Float = 12F
  const val DEFAULT_BAND_VALUE: Float = 0F

  init {
    uniqueIndex(presetName)
  }
}

object EqPresetAssociationTable : Table() {
  /**
   * This cannot be a reference to the id of the [EqPresetTable] because it may be a system
   * preset
   */
  val presetId = long("EqPresetAssoc_PresetId")
  val isSystemPreset = bool("EqPresetAssocIsSystem")
  val associationType = integer("EqPresetAssocAssocType")

  /**
   * The meaning of this columns depends on the [associationType]. When [PresetAssociationType] is:
   * * [Default][PresetAssociationType.Default] this id must be zero
   * * [Media][PresetAssociationType.Media] this id is a Media ID
   * * [Album][PresetAssociationType.Album] this id is an Album ID
   * * [Output][PresetAssociationType.Output] this id is an AudioOutputRoute.id
   */
  val associationId = long("EqPresetAssocAssocId") { default(0) }

  override val primaryKey = PrimaryKey(presetId, associationType, associationId)

  init {
    index(associationType, associationId)
  }
}

/**
 * Stores the state of a queue in the media player service. The [id] is specific to a queue and
 * IDs can't overlap or they corrupt each other's data
 */
object QueueStateTable : Table() {
  val id = integer("QueueId") { primaryKey() }
  val mediaId = long("QueueState_MediaId") { default(-1) }
  val queueIndex = integer("QueueState_QueueIndex") { default(0) }
  val playbackPosition = long("QueueState_PlaybackPosition") { default(0) }
  val timePlayed = long("QueueState_TimePlayed") { default(0) }
  val countingFrom = long("QueueState_CountingFrom") { default(0) }
}

object PlayListTable : Table() {
  val id = long("PlayListID") { primaryKey() }
  val playListName = text("PlayListName") { collateNoCase() }

  /** [PlayListType] */
  val playListType = integer("PlayListType")
  val createdTime = long("PlayListTimeCreated")
  val updatedTime = long("PlayListUpdated") { default(0) }

  /** This column should be equal to [PlayListType.sortPosition] */
  val sort = integer("PlayListSort")

  init {
    uniqueIndex(playListName)
    // PlayListMediaTable defines column as ForeignKey with cascade on delete, no trigger necessary
    //deleteTrigger(
    //  name = "DeletePlayListTrigger",
    //  beforeAfter = Trigger.BeforeAfter.BEFORE
    //) {
    //  PlayListMediaTable.delete { PlayListMediaTable.playListId eq old(PlayListTable.id) }
    //}
  }
}

object PlayListMediaTable : Table() {
  val id = long("PlayListMediaId") { primaryKey() }
  val playListId = reference(
    name = "PlayListMedia_PlayListId",
    refColumn = PlayListTable.id,
    onDelete = ForeignKeyAction.CASCADE
  )
  val mediaId = reference(
    name = "PlayListMedia_MediaId",
    refColumn = MediaTable.id,
    onDelete = ForeignKeyAction.CASCADE
  )

  /**
   * Position within the playlist - no contract that it starts at 0 or 1, just that it sorts
   * properly based on song position. Query result row index indicates actual playlist index
   */
  val sort = long("PlayListMedia_Sort")

  init {
    /** For quickly getting the playlist entries */
    index(playListId)
    /** To quickly find which playlists a piece of media is in */
    index(mediaId)
  }
}
