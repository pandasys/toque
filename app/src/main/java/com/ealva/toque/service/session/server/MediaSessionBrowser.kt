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

package com.ealva.toque.service.session.server

import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import androidx.core.net.toUri
import androidx.media.MediaBrowserServiceCompat
import androidx.media.MediaBrowserServiceCompat.BrowserRoot
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.BuildConfig
import com.ealva.toque.R
import com.ealva.toque.common.Limit
import com.ealva.toque.common.fetch
import com.ealva.toque.db.AlbumDao
import com.ealva.toque.db.ArtistDao
import com.ealva.toque.db.AudioDescription
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.DaoMessage
import com.ealva.toque.db.GenreDao
import com.ealva.toque.log._e
import com.ealva.toque.log._i
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.persist.ComposerId
import com.ealva.toque.persist.GenreId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.persist.PersistentId
import com.ealva.toque.persist.PlaylistId
import com.ealva.toque.service.session.common.toCompatMediaId
import com.ealva.toque.service.session.common.toPersistentId
import com.ealva.toque.ui.library.ArtistType
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapAll
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

private val LOG by lazyLogger(MediaSessionBrowser::class)
private val MAX_LIST_SIZE = Limit(1000L)

typealias BrowserResult = MediaBrowserServiceCompat.Result<List<MediaItem>>

interface OnMediaType<T> {
  suspend fun onMedia(mediaId: MediaId, extras: Bundle, limit: Limit): T
  suspend fun onArtist(artistId: ArtistId, extras: Bundle, limit: Limit): T
  suspend fun onAlbum(albumId: AlbumId, extras: Bundle, limit: Limit): T
  suspend fun onGenre(genreId: GenreId, extras: Bundle, limit: Limit): T
  suspend fun onComposer(composerId: ComposerId, extras: Bundle, limit: Limit): T
  suspend fun onPlaylist(playlistId: PlaylistId, extras: Bundle, limit: Limit): T
}

interface MediaSessionBrowser {
  fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle): BrowserRoot?
  fun onLoadChildren(parentId: String, result: BrowserResult)
  fun onSearch(query: String, extras: Bundle, result: BrowserResult)

  companion object {
    const val ID_ROOT = "ROOT_ID"
    const val ID_RECENT_ROOT = "RECENTS_ROOT_ID"
    const val ID_NO_MEDIA = "NO_MEDIA_ID"
    const val ID_NO_PLAYLIST = "NO_PLAYLIST_ID"

    operator fun invoke(
      recentMediaProvider: RecentMediaProvider,
      audioMediaDao: AudioMediaDao,
      scope: CoroutineScope,
      dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): MediaSessionBrowser = MediaSessionBrowserImpl(
      audioMediaDao,
      recentMediaProvider,
      scope,
      dispatcher
    )
    /** Throws IllegalArgumentException if [mediaId] is not of a recognized type */
    suspend fun <T> handleMedia(
      mediaId: String,
      extras: Bundle,
      onMediaType: OnMediaType<T>
    ): T {
      return when (val id = mediaId.trim().toPersistentId()) {
        is MediaId -> onMediaType.onMedia(id, extras, MAX_LIST_SIZE)
        is ArtistId -> onMediaType.onArtist(id, extras, MAX_LIST_SIZE)
        is AlbumId -> onMediaType.onAlbum(id, extras, MAX_LIST_SIZE)
        is GenreId -> onMediaType.onGenre(id, extras, MAX_LIST_SIZE)
        is ComposerId -> onMediaType.onComposer(id, extras, MAX_LIST_SIZE)
        is PlaylistId -> onMediaType.onPlaylist(id, extras, MAX_LIST_SIZE)
        else -> throw IllegalArgumentException("Unrecognized media ID")
      }
    }
  }
}

private typealias ItemListResult = Result<List<MediaItem>, DaoMessage>

private class MediaSessionBrowserImpl(
  private val audioMediaDao: AudioMediaDao,
  private val recentMediaProvider: RecentMediaProvider,
  private val scope: CoroutineScope,
  private val dispatcher: CoroutineDispatcher
) : MediaSessionBrowser, KoinComponent {
  private val artistDao: ArtistDao = audioMediaDao.artistDao
  private val albumDao: AlbumDao = audioMediaDao.albumDao
  private val genreDao: GenreDao = audioMediaDao.genreDao

  override fun onGetRoot(
    clientPackageName: String,
    clientUid: Int,
    rootHints: Bundle
  ): BrowserRoot = makeBrowserRoot(rootHints)

  private fun makeBrowserRoot(rootHints: Bundle): BrowserRoot {
    val rootExtras = Bundle().apply {
      putBoolean(MEDIA_SEARCH_SUPPORTED, true)
      putBoolean(CONTENT_STYLE_SUPPORTED, true)
      putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_GRID)
      putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST)
    }
    return if (rootHints.getBoolean(BrowserRoot.EXTRA_RECENT)) {
      LOG._e { it("Recent Root") }
      // Return a tree with a single playable media item for resumption
      rootExtras.putBoolean(BrowserRoot.EXTRA_RECENT, true)
      BrowserRoot(MediaSessionBrowser.ID_RECENT_ROOT, rootExtras)
    } else {
      LOG._e { it("Browser Tree Root") }
      BrowserRoot(MediaSessionBrowser.ID_ROOT, rootExtras)
    }
  }

  override fun onLoadChildren(
    parentId: String,
    result: MediaBrowserServiceCompat.Result<List<MediaItem>>
  ) {
    result.detach()
    scope.launch(dispatcher) { result.sendResult(getChildren(parentId)) }
  }

  private suspend fun getChildren(parentId: String): List<MediaItem> {
    suspend fun valueFromList(
      maker: suspend () -> Result<List<MediaItem>, DaoMessage>
    ): List<MediaItem> = when (val result = maker()) {
      is Ok -> result.value
      is Err -> emptyList()
    }

    return try {
      when (parentId) {
        MediaSessionBrowser.ID_ROOT -> makeRootList()
        MediaSessionBrowser.ID_RECENT_ROOT -> makeRecentTrack()
        ID_LIBRARY -> makeLibraryList()
        ID_ARTISTS -> valueFromList { makeArtistList() }
        ID_ALBUMS -> valueFromList { makeAlbumList() }
        ID_GENRES -> valueFromList { makeGenreList() }
        ID_TRACKS -> valueFromList { makeTrackList() }
        else -> {
          MediaSessionBrowser.handleMedia(
            parentId,
            Bundle.EMPTY,
            object : OnMediaType<List<MediaItem>> {
              // Media have no children
              override suspend fun onMedia(
                mediaId: MediaId,
                extras: Bundle,
                limit: Limit
              ): List<MediaItem> = emptyList()

              override suspend fun onArtist(
                artistId: ArtistId,
                extras: Bundle,
                limit: Limit
              ): List<MediaItem> =
                valueFromList { makeArtistAlbumList(artistId) }

              override suspend fun onAlbum(
                albumId: AlbumId,
                extras: Bundle,
                limit: Limit
              ): List<MediaItem> = valueFromList { makeAlbumTracksList(albumId) }

              override suspend fun onGenre(
                genreId: GenreId,
                extras: Bundle,
                limit: Limit
              ): List<MediaItem> = valueFromList { makeGenreTracksList(genreId) }

              override suspend fun onComposer(
                composerId: ComposerId,
                extras: Bundle,
                limit: Limit
              ): List<MediaItem> {
                TODO("Not yet implemented")
              }

              override suspend fun onPlaylist(
                playlistId: PlaylistId,
                extras: Bundle,
                limit: Limit
              ): List<MediaItem> {
                TODO("Not yet implemented")
              }
            }
          )
        }
      }.apply {
        if (isEmpty()) listOf(MediaItem(makeEmptyMediaDesc(parentId), MediaItemFlags.Playable))
      }
    } catch (e: Exception) {
      LOG.e(e) { it("Error getChildren(\"%s\")") }
      emptyList()
    }
  }

  private fun makeRootList(): List<MediaItem> = listOf(
    MediaItem(makePlaylistItemDesc(), MediaItemFlags.Browsable),
    MediaItem(makeLibraryItemDesc(), MediaItemFlags.Browsable)
  )

  private fun makeLibraryItemDesc() =
    makeItemDesc(ID_LIBRARY, fetch(R.string.Library), LIBRARY_ICON)

  private fun makePlaylistItemDesc() = makeItemDesc(
    ID_PLAYLISTS,
    fetch(R.string.Playlists),
    PLAYLIST_ICON,
    extras = getContentStyle(CONTENT_STYLE_GRID, CONTENT_STYLE_GRID)
  )

  private suspend fun makeRecentTrack(): List<MediaItem> {
    return recentMediaProvider.getRecentMedia()?.let { description ->
      listOf(MediaItem(description, MediaItemFlags.Playable))
    } ?: emptyList()
  }

  private fun makeLibraryList(): List<MediaItem> {
    return listOf(
      MediaItem(makeArtistListItemDesc(), MediaItemFlags.Browsable),
      MediaItem(makeAlbumListItemDesc(), MediaItemFlags.Browsable),
      MediaItem(makeGenreListItemDesc(), MediaItemFlags.Browsable),
      MediaItem(makeTrackListItemDesc(), MediaItemFlags.Browsable),
    )
  }

  private fun makeArtistListItemDesc(): MediaDescriptionCompat =
    makeItemDesc(ID_ARTISTS, fetch(R.string.Artists), ARTIST_ICON)

  private fun makeAlbumListItemDesc(): MediaDescriptionCompat = makeItemDesc(
    ID_ALBUMS,
    fetch(R.string.Albums),
    ALBUM_ICON,
    extras = getContentStyle(CONTENT_STYLE_GRID, CONTENT_STYLE_LIST)
  )

  private suspend fun makeArtistList(): Result<List<MediaItem>, DaoMessage> =
    artistDao
      .getAllArtistNames(MAX_LIST_SIZE)
      .mapAll {
        Ok(
          MediaItem(
            makeItemDesc(it.artistId, it.artistName.value, ARTIST_ICON),
            MediaItemFlags.Browsable
          )
        )
      }

  private fun makeItemDesc(
    id: String,
    name: String,
    icon: Uri,
    subtitle: String? = null,
    extras: Bundle = Bundle.EMPTY
  ): MediaDescriptionCompat = buildDescription {
    setMediaId(id)
    setTitle(name)
    setIconUri(icon)
    setSubtitle(subtitle)
    if (extras !== Bundle.EMPTY) setExtras(extras)
  }

  private fun makeItemDesc(id: PersistentId, name: String, icon: Uri, subtitle: String? = null) =
    makeItemDesc(id.toCompatMediaId(), name, icon, subtitle, Bundle.EMPTY)

  private suspend fun makeAlbumList(): Result<List<MediaItem>, DaoMessage> =
    albumDao
      .getAllAlbums(limit = MAX_LIST_SIZE)
      .mapAll {
        Ok(
          MediaItem(
            makeItemDesc(
              it.albumId,
              it.albumTitle.value,
              selectAlbumArt(it.albumLocalArt, it.albumArt, ALBUM_ICON),
              it.artistName.value,
            ),
            MediaItemFlags.Browsable
          )
        )
      }

  private suspend fun makeArtistAlbumList(
    artistId: ArtistId
  ): Result<List<MediaItem>, DaoMessage> = albumDao
    .getAllAlbumsFor(artistId, ArtistType.AlbumArtist, limit = MAX_LIST_SIZE)
    .mapAll {
      Ok(
        MediaItem(
          makeItemDesc(
            it.albumId,
            it.albumTitle.value,
            selectAlbumArt(it.albumLocalArt, it.albumArt, ALBUM_ICON),
            it.artistName.value
          ),
          MediaItemFlags.Browsable
        )
      )
    }

  private fun selectAlbumArt(albumLocalArt: Uri, albumArt: Uri, defaultValue: Uri): Uri {
    return when {
      albumLocalArt !== Uri.EMPTY -> albumLocalArt
      albumArt !== Uri.EMPTY -> albumArt
      else -> defaultValue
    }
  }

  private suspend fun makeGenreList(): Result<List<MediaItem>, DaoMessage> =
    genreDao
      .getAllGenreNames(MAX_LIST_SIZE)
      .mapAll {
        Ok(
          MediaItem(
            makeItemDesc(it.genreId, it.genreName.value, GENRE_ICON),
            MediaItemFlags.Browsable
          )
        )
      }

  private fun makeGenreListItemDesc(): MediaDescriptionCompat =
    makeItemDesc(ID_GENRES, fetch(R.string.Genres), GENRE_ICON)

  private suspend fun makeTrackList(): Result<List<MediaItem>, DaoMessage> =
    audioMediaDao
      .getAllAudio(limit = MAX_LIST_SIZE)
      .mapToMediaList()

  private fun makeTrackListItemDesc(): MediaDescriptionCompat =
    makeItemDesc(ID_TRACKS, fetch(R.string.Tracks), TRACK_ICON)

//  private suspend fun makeArtistTracksList(
//    artistId: ArtistId
//  ): Result<List<MediaItem>, DaoMessage> = audioMediaDao
//    .getAllAudioFor(artistId, MAX_LIST_SIZE)
//    .mapToMediaList()

  private suspend fun makeAlbumTracksList(
    albumId: AlbumId
  ): Result<List<MediaItem>, DaoMessage> = audioMediaDao
    .getAlbumAudio(id = albumId, limit = MAX_LIST_SIZE)
    .mapToMediaList()

  private fun Result<Iterable<AudioDescription>, DaoMessage>.mapToMediaList(): ItemListResult =
    mapAll { item ->
      Ok(
        MediaItem(
          makeItemDesc(
            item.mediaId,
            item.title(),
            selectAlbumArt(item.albumLocalArt, item.albumArt, TRACK_ICON)
          ),
          MediaItemFlags.Playable
        )
      )
    }

  private suspend fun makeGenreTracksList(
    genreId: GenreId
  ): Result<List<MediaItem>, DaoMessage> = audioMediaDao
    .getGenreAudio(genreId = genreId, limit = MAX_LIST_SIZE)
    .mapToMediaList()

  private fun makeEmptyMediaDesc(parentId: String) = MediaDescriptionCompat.Builder()
    .setMediaId(MediaSessionBrowser.ID_NO_MEDIA)
    .setIconUri(TRACK_ICON)
    .setTitle(fetch(R.string.No_media_found))
    .apply {
      when (parentId) {
        ID_ARTISTS -> setIconUri(ARTIST_ICON)
        ID_ALBUMS -> setIconUri(ALBUM_ICON)
        ID_GENRES -> setIconUri(null)
        ID_PLAYLISTS -> {
          setMediaId(MediaSessionBrowser.ID_NO_PLAYLIST)
          setTitle(fetch(R.string.No_playlist_found))
        }
        // ID_STREAMS -> emptyMediaDesc.setIconUri(DEFAULT_STREAM_ICON)
      }
    }
    .build()

  override fun onSearch(
    query: String,
    extras: Bundle,
    result: MediaBrowserServiceCompat.Result<List<MediaItem>>
  ) {
    LOG._i { it("onSearch for %s extras:%s", query, extras) }
    result.detach()
    scope.launch(dispatcher) { result.sendResult(doSearch(query, extras)) }
  }

  private fun doSearch(query: String, extras: Bundle): List<MediaItem> {
    val focusedResults: List<MediaItem> = if (extras.isFocusedSearch()) {
      when (extras[MediaStore.EXTRA_MEDIA_FOCUS]) {
        MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE -> {
          extras.getString(MediaStore.EXTRA_MEDIA_GENRE)?.let { genre ->
            LOG._i { it("Search genre '%s'", genre) }
            emptyList()
          } ?: emptyList()
        }
        MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE -> {
          extras.getString(MediaStore.EXTRA_MEDIA_ARTIST)?.let { artist ->
            // song.artist == artist || song.albumArtist == artist)
            LOG._i { it("Search artist '%s'", artist) }
            emptyList()
          } ?: emptyList()
        }
        MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE -> {
          val artist = extras.getString(MediaStore.EXTRA_MEDIA_ARTIST, "")
          val album = extras.getString(MediaStore.EXTRA_MEDIA_ALBUM, "")
          LOG._i {
            it(
              "Search album '%s' artist '%s'",
              album,
              artist
            )
          }
          // (song.artist == artist || song.albumArtist == artist) && song.album == album
          emptyList()
        }
        MediaStore.Audio.Media.ENTRY_CONTENT_TYPE -> {
          val title = extras.getString(MediaStore.EXTRA_MEDIA_TITLE, "")
          val album = extras.getString(MediaStore.EXTRA_MEDIA_ALBUM, "")
          val artist = extras.getString(MediaStore.EXTRA_MEDIA_ARTIST, "")
          LOG._i {
            it(
              "Search media title '%s' album '%s' artist '%s'",
              title,
              album,
              artist
            )
          }
          // (song.artist == artist || song.albumArtist == artist) && song.album == album
          //                            && song.title == title
          emptyList()
        }
        else -> emptyList()
      }
    } else emptyList()

    return if (focusedResults.isEmpty()) {
      LOG._i { it("Unfocused or not found, query='%s'", query) }
      emptyList()
    } else {
      focusedResults
    }
  }
//      //Streams - Radio
//      val streamsMediaDesc = MediaDescriptionCompat.Builder()
//        .setMediaId(ID_STREAMS)
//        .setTitle(res.getString(R.string.streams))
//        .setIconUri("${BASE_DRAWABLE_URI}/${R.drawable.ic_menu_stream}".toUri())
//        .build()

  companion object {
    private const val ID_ARTISTS = "ID_ARTISTS"
    private const val ID_ALBUMS = "ID_ALBUMS"
    private const val ID_TRACKS = "ID_TRACKS"
    private const val ID_GENRES = "ID_GENRES"
    private const val ID_PLAYLISTS = "ID_PLAYLISTS"
    private const val ID_LIBRARY = "ID_LIBRARY"

    private const val BASE_DRAWABLE_URI =
      "android.resource://${BuildConfig.APPLICATION_ID}/drawable"
    private val ALBUM_ICON = "$BASE_DRAWABLE_URI/${R.drawable.ic_auto_album}".toUri()
    private val ARTIST_ICON = "$BASE_DRAWABLE_URI/${R.drawable.ic_auto_artist}".toUri()
    //private val STREAM_ICON = "$BASE_DRAWABLE_URI/${R.drawable.ic_auto_stream}".toUri()
    private val PLAYLIST_ICON = "$BASE_DRAWABLE_URI/${R.drawable.ic_auto_playlist}".toUri()
    private val GENRE_ICON = "$BASE_DRAWABLE_URI/${R.drawable.ic_auto_drama_masks}".toUri()
    private val TRACK_ICON = "$BASE_DRAWABLE_URI/${R.drawable.ic_auto_audio}".toUri()
    private val LIBRARY_ICON = "$BASE_DRAWABLE_URI/${R.drawable.ic_auto_library_music}".toUri()

    fun getContentStyle(browsableHint: Int, playableHint: Int): Bundle {
      return Bundle().apply {
        putBoolean(CONTENT_STYLE_SUPPORTED, true)
        putInt(CONTENT_STYLE_BROWSABLE_HINT, browsableHint)
        putInt(CONTENT_STYLE_PLAYABLE_HINT, playableHint)
      }
    }
  }
}

fun Bundle.isFocusedSearch(): Boolean {
  return this !== Bundle.EMPTY && containsKey(MediaStore.EXTRA_MEDIA_FOCUS)
}

/**
 * Seems Android auto doesn't support an item being both playable and browsable. So need to add
 * media items like "Shuffle All" and "Play All" in the contents of browsable items such as
 * Album, Artist, Genre...
 */
enum class MediaItemFlags(private val flags: Int) {
  Playable(MediaItem.FLAG_PLAYABLE),
  Browsable(MediaItem.FLAG_BROWSABLE),
  /** Not usable until clients actually support it */
  @Suppress("unused") PlayAndBrowse(MediaItem.FLAG_PLAYABLE or MediaItem.FLAG_BROWSABLE);

  val asCompat: Int
    get() = flags
}

fun MediaItem(description: MediaDescriptionCompat, flags: MediaItemFlags): MediaItem {
  return MediaItem(description, flags.asCompat)
}

/** Declares that ContentStyle is supported */
const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"

/** Bundle extra indicating the presentation hint for playable media items. */
const val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"

/** Bundle extra indicating the presentation hint for browsable media items. */
const val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"

/** Specifies the corresponding items should be presented as lists. */
const val CONTENT_STYLE_LIST = 1

/** Specifies that the corresponding items should be presented as grids. */
const val CONTENT_STYLE_GRID = 2

//
///**
// * Specifies that the corresponding items should be presented as lists and are represented by a
// * vector icon. This adds a small margin around the icons instead of filling the full available
// * area.
// */
//const val CONTENT_STYLE_CATEGORY_LIST = 3
//
///**
// * Specifies that the corresponding items should be presented as grids and are represented by a
// * vector icon. This adds a small margin around the icons instead of filling the full available
// * area.
// */
//const val CONTENT_STYLE_CATEGORY_GRID = 4

const val MEDIA_SEARCH_SUPPORTED = "android.media.browse.SEARCH_SUPPORTED"

inline fun buildDescription(
  builderAction: MediaDescriptionCompat.Builder.() -> Unit
): MediaDescriptionCompat = MediaDescriptionCompat.Builder().apply(builderAction).build()
