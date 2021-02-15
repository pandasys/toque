/*
 * Copyright 2021 eAlva.com
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

package com.ealva.toque.service.session

import android.content.Context
import android.os.Bundle
import android.provider.MediaStore
import android.provider.MediaStore.EXTRA_MEDIA_ALBUM
import android.provider.MediaStore.EXTRA_MEDIA_ARTIST
import android.provider.MediaStore.EXTRA_MEDIA_FOCUS
import android.provider.MediaStore.EXTRA_MEDIA_GENRE
import android.provider.MediaStore.EXTRA_MEDIA_TITLE
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
import com.ealva.toque.db.ArtistDao
import com.ealva.toque.log._e
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.persist.ComposerId
import com.ealva.toque.persist.GenreId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.persist.PersistentId
import com.ealva.toque.persist.PlaylistId
import com.ealva.toque.persist.toAlbumId
import com.ealva.toque.persist.toArtistId
import com.ealva.toque.persist.toComposerId
import com.ealva.toque.persist.toGenreId
import com.ealva.toque.persist.toMediaId
import com.ealva.toque.persist.toPlaylistId
import com.ealva.toque.service.session.MediaItemFlags.Browsable
import com.ealva.toque.service.session.MediaItemFlags.Playable
import com.ealva.toque.service.session.MediaSessionBrowser.Companion.ID_NO_MEDIA
import com.ealva.toque.service.session.MediaSessionBrowser.Companion.ID_NO_PLAYLIST
import com.ealva.toque.service.session.MediaSessionBrowser.Companion.ID_ROOT
import com.ealva.toque.service.session.MediaSessionBrowser.Companion.makeMediaId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val LOG by lazyLogger(MediaSessionBrowser::class)

typealias BrowserResult = MediaBrowserServiceCompat.Result<List<MediaItem>>

interface MediaSessionBrowser {

  fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle): BrowserRoot?
  fun onLoadChildren(parentId: String, result: BrowserResult)
  fun onSearch(query: String, extras: Bundle, result: BrowserResult)

  companion object {
    operator fun invoke(
      scope: CoroutineScope,
      dispatcher: CoroutineDispatcher? = null
    ): MediaSessionBrowser = MediaSessionBrowserImpl(scope, dispatcher)

    const val ID_ROOT = "ID_ROOT"
    const val ALBUM_PREFIX = "album"
    const val ARTIST_PREFIX = "artist"
    const val GENRE_PREFIX = "genre"
    const val PLAYLIST_PREFIX = "playlist"
    const val COMPOSER_PREFIX = "composer"
    const val ID_NO_MEDIA = "ID_NO_MEDIA"
    const val ID_NO_PLAYLIST = "ID_NO_PLAYLIST"

    fun makeMediaId(id: PersistentId): String {
      val prefix = when (id) {
        is MediaId -> return id.id.toString()
        is AlbumId -> ALBUM_PREFIX
        is ArtistId -> ARTIST_PREFIX
        is GenreId -> GENRE_PREFIX
        is PlaylistId -> PLAYLIST_PREFIX
        is ComposerId -> COMPOSER_PREFIX
        else -> throw IllegalArgumentException("Unrecognized PersistentId type for $id")
      }
      return "${prefix}_${id.id}"
    }

    fun handleMedia(mediaId: String, extras: Bundle, onMediaType: OnMediaType) {
      val list = mediaId.split('_')
      if (list.size > 1) {
        val id = list[1].toLongOrNull() ?: -1
        when (list[0]) {
          ARTIST_PREFIX -> onMediaType.onArtist(id.toArtistId(), extras)
          ALBUM_PREFIX -> onMediaType.onAlbum(id.toAlbumId(), extras)
          GENRE_PREFIX -> onMediaType.onGenre(id.toGenreId(), extras)
          COMPOSER_PREFIX -> onMediaType.onComposer(id.toComposerId(), extras)
          PLAYLIST_PREFIX -> onMediaType.onPlaylist(id.toPlaylistId(), extras)
        }
      } else {
        onMediaType.onMedia((mediaId.toLongOrNull() ?: -1).toMediaId(), extras)
      }
    }
  }
}

interface OnMediaType {
  fun onMedia(mediaId: MediaId, extras: Bundle)
  fun onArtist(artistId: ArtistId, extras: Bundle)
  fun onAlbum(albumId: AlbumId, extras: Bundle)
  fun onGenre(genreId: GenreId, extras: Bundle)
  fun onComposer(composerId: ComposerId, extras: Bundle)
  fun onPlaylist(playlistId: PlaylistId, extras: Bundle)
}

@OptIn(KoinApiExtension::class)
private class MediaSessionBrowserImpl(
  private val scope: CoroutineScope,
  dispatcher: CoroutineDispatcher?
) : MediaSessionBrowser, KoinComponent {
  private val dispatcher = dispatcher ?: Dispatchers.IO
  private val context: Context by inject()
  private val artistDao: ArtistDao by inject()
  private val packageValidator = PackageValidator(context, R.xml.allowed_media_browser_callers)

  override fun onGetRoot(
    clientPackageName: String,
    clientUid: Int,
    rootHints: Bundle
  ): BrowserRoot? {
    LOG._e { it("onGetRoot pkg=%s uid=%d", clientPackageName, clientUid) }
    val isKnownCaller = packageValidator.isKnownCaller(clientPackageName, clientUid)
    return if (isKnownCaller) {
      makeBrowserRoot()
    } else null
  }

  private fun makeBrowserRoot() = BrowserRoot(
    ID_ROOT,
    getContentStyle(CONTENT_STYLE_LIST_ITEM_HINT_VALUE, CONTENT_STYLE_LIST_ITEM_HINT_VALUE)
  )

  override fun onLoadChildren(
    parentId: String,
    result: MediaBrowserServiceCompat.Result<List<MediaItem>>
  ) {
    LOG._e { it("onLoadChildren of %s", parentId) }
    result.detach()
    scope.launch(dispatcher) { result.sendResult(getChildren(parentId)) }
  }

  override fun onSearch(
    query: String,
    extras: Bundle,
    result: MediaBrowserServiceCompat.Result<List<MediaItem>>
  ) {
    LOG._e { it("onSearch for %s extras:%s", query, extras) }
    result.detach()
    scope.launch(dispatcher) { result.sendResult(doSearch(query, extras)) }
  }

  private fun doSearch(query: String, extras: Bundle): List<MediaItem> {
    val focusedResults: List<MediaItem> = if (extras.isFocusedSearch()) {
      when (extras[EXTRA_MEDIA_FOCUS]) {
        MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE -> {
          extras.getString(EXTRA_MEDIA_GENRE)?.let { genre ->
            LOG._e { it("Search genre '%s'", genre) }
            emptyList()
          } ?: emptyList()
        }
        MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE -> {
          extras.getString(EXTRA_MEDIA_ARTIST)?.let { artist ->
            // song.artist == artist || song.albumArtist == artist)
            LOG._e { it("Search artist '%s'", artist) }
            emptyList()
          } ?: emptyList()
        }
        MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE -> {
          val artist = extras.getString(EXTRA_MEDIA_ARTIST, "")
          val album = extras.getString(EXTRA_MEDIA_ALBUM, "")
          LOG._e { it("Search album '%s' artist '%s'", album, artist) }
          // (song.artist == artist || song.albumArtist == artist) && song.album == album
          emptyList()
        }
        MediaStore.Audio.Media.ENTRY_CONTENT_TYPE -> {
          val title = extras.getString(EXTRA_MEDIA_TITLE, "")
          val album = extras.getString(EXTRA_MEDIA_ALBUM, "")
          val artist = extras.getString(EXTRA_MEDIA_ARTIST, "")
          LOG._e { it("Search media title '%s' album '%s' artist '%s'", title, album, artist) }
          // (song.artist == artist || song.albumArtist == artist) && song.album == album
          //                            && song.title == title
          emptyList()
        }
        else -> emptyList()
      }
    } else emptyList()

    return if (focusedResults.isEmpty()) {
      LOG._e { it("Unfocused or not found, query='%s'", query) }
      emptyList()
    } else {
      focusedResults
    }
  }

  private suspend fun getChildren(parentId: String): List<MediaItem> {
    return try {
      when (parentId) {
        ID_ROOT -> makeRootList()
        ID_LIBRARY -> makeLibraryList()
        ID_ARTISTS -> makeArtistsList()
        else -> emptyList()
      }.apply {
        if (isEmpty()) listOf(MediaItem(makeEmptyMediaDesc(parentId), Playable))
      }
    } catch (e: Exception) {
      LOG.e(e) { it("Error getChildren(\"%s\")") }
      emptyList()
    }
  }

  private suspend fun makeArtistsList(): List<MediaItem> = artistDao
    .getAllArtistNames()
    .map { MediaItem(makeArtisItemDesc(it.artistId, it.artistName), Browsable) }

  private fun makeArtisItemDesc(
    artistId: ArtistId,
    artistName: String
  ): MediaDescriptionCompat = buildDescription {
    setMediaId(makeMediaId(artistId))
    setTitle(artistName)
    setIconUri(DEFAULT_ARTIST_ICON)
//    setIconUri("$BASE_DRAWABLE_URI/${R.drawable.ic_auto_artist}".toUri())
  }

  private fun makeLibraryList(): List<MediaItem> {
    return listOf(
      MediaItem(makeArtistListItemDesc(), Browsable),
      MediaItem(makeAlbumListItemDesc(), Browsable),
      MediaItem(makeGenreListItemDesc(), Browsable),
      MediaItem(makeTrackListItemDesc(), Browsable),
    )
    /*
  //Tracks
  val tracksMediaDesc = MediaDescriptionCompat.Builder()
          .setMediaId(ID_TRACKS)
          .setTitle(res.getString(R.string.tracks))
          .setIconUri("${BASE_DRAWABLE_URI}/${R.drawable.ic_auto_audio}".toUri())
          .build()
  results.add(MediaBrowserCompat.MediaItem(tracksMediaDesc,
              MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
     */
  }

  private fun makeArtistListItemDesc(): MediaDescriptionCompat = buildDescription {
    setMediaId(ID_ARTISTS)
    setTitle(context.getString(R.string.Artists))
    setIconUri("$BASE_DRAWABLE_URI/${R.drawable.ic_auto_artist}".toUri())
  }

  private fun makeAlbumListItemDesc(): MediaDescriptionCompat = buildDescription {
    setMediaId(ID_ALBUMS)
    setTitle(context.getString(R.string.Albums))
    setIconUri("$BASE_DRAWABLE_URI/${R.drawable.ic_auto_album}".toUri())
    setExtras(
      getContentStyle(CONTENT_STYLE_GRID_ITEM_HINT_VALUE, CONTENT_STYLE_LIST_ITEM_HINT_VALUE)
    )
  }

  private fun makeGenreListItemDesc(): MediaDescriptionCompat = buildDescription {
    setMediaId(ID_GENRES)
    setTitle(context.getString(R.string.Genres))
    setIconUri("$BASE_DRAWABLE_URI/${R.drawable.ic_auto_drama_masks}".toUri())
  }

  private fun makeTrackListItemDesc(): MediaDescriptionCompat = buildDescription {
    setMediaId(ID_TRACKS)
    setTitle(context.getString(R.string.Tracks))
    setIconUri("$BASE_DRAWABLE_URI/${R.drawable.ic_auto_audio}".toUri())
  }

  private fun makeEmptyMediaDesc(parentId: String) = MediaDescriptionCompat.Builder()
    .setMediaId(ID_NO_MEDIA)
    .setIconUri(DEFAULT_TRACK_ICON)
    .setTitle(context.getString(R.string.No_media_found))
    .apply {
      when (parentId) {
        ID_ARTISTS -> setIconUri(DEFAULT_ARTIST_ICON)
        ID_ALBUMS -> setIconUri(DEFAULT_ALBUM_ICON)
        ID_GENRES -> setIconUri(null)
        ID_PLAYLISTS -> {
          setMediaId(ID_NO_PLAYLIST)
          setTitle(context.getString(R.string.No_playlist_found))
        }
        // ID_STREAMS -> emptyMediaDesc.setIconUri(DEFAULT_STREAM_ICON)
      }
    }
    .build()

  private fun makeRootList(): List<MediaItem> = listOf(
    MediaItem(makePlaylistItemDesc(), Browsable),
    MediaItem(makeLibraryItemDesc(), Browsable)
  )
//      //Streams - Radio
//      val streamsMediaDesc = MediaDescriptionCompat.Builder()
//        .setMediaId(ID_STREAMS)
//        .setTitle(res.getString(R.string.streams))
//        .setIconUri("${BASE_DRAWABLE_URI}/${R.drawable.ic_menu_stream}".toUri())
//        .build()

  private fun makeLibraryItemDesc() = buildDescription {
    setMediaId(ID_LIBRARY)
    setTitle(context.getString(R.string.Library))
    setIconUri("$BASE_DRAWABLE_URI/${R.drawable.ic_auto_library_music}".toUri())
  }

  private fun makePlaylistItemDesc() = buildDescription {
    setMediaId(ID_PLAYLISTS)
    setTitle(context.getString(R.string.Playlists))
    setIconUri("$BASE_DRAWABLE_URI/${R.drawable.ic_auto_playlist}".toUri())
    setExtras(
      getContentStyle(CONTENT_STYLE_GRID_ITEM_HINT_VALUE, CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
    )
  }

  companion object {
    private const val ID_ARTISTS = "ID_ARTISTS"
    private const val ID_ALBUMS = "ID_ALBUMS"
    private const val ID_TRACKS = "ID_TRACKS"
    private const val ID_GENRES = "ID_GENRES"
    private const val ID_PLAYLISTS = "ID_PLAYLISTS"
    private const val ID_LIBRARY = "ID_LIBRARY"

    private const val BASE_DRAWABLE_URI = "android.resource://${BuildConfig.APP_ID}/drawable"
    private val DEFAULT_ALBUM_ICON =
      "$BASE_DRAWABLE_URI/${R.drawable.ic_auto_album_unknown}".toUri()
    private val DEFAULT_ARTIST_ICON =
      "$BASE_DRAWABLE_URI/${R.drawable.ic_auto_artist_unknown}".toUri()
    private val DEFAULT_STREAM_ICON =
      "$BASE_DRAWABLE_URI/${R.drawable.ic_auto_stream_unknown}".toUri()
    private val DEFAULT_PLAYLIST_ICON =
      "$BASE_DRAWABLE_URI/${R.drawable.ic_auto_playlist_unknown}".toUri()
    val DEFAULT_TRACK_ICON = "$BASE_DRAWABLE_URI/${R.drawable.ic_auto_nothumb}".toUri()

    fun getContentStyle(browsableHint: Int, playableHint: Int): Bundle {
      return Bundle().apply {
        putBoolean(CONTENT_STYLE_SUPPORTED, true)
        putInt(CONTENT_STYLE_BROWSABLE_HINT, browsableHint)
        putInt(CONTENT_STYLE_PLAYABLE_HINT, playableHint)
      }
    }
  }
}

private fun Bundle.isFocusedSearch(): Boolean {
  return this !== Bundle.EMPTY && containsKey(EXTRA_MEDIA_FOCUS)
}

enum class MediaItemFlags(private val flags: Int) {
  Playable(MediaItem.FLAG_PLAYABLE),
  Browsable(MediaItem.FLAG_BROWSABLE),
  PlayAndBrowse(MediaItem.FLAG_PLAYABLE or MediaItem.FLAG_BROWSABLE);

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
const val CONTENT_STYLE_LIST_ITEM_HINT_VALUE = 1

/** Specifies that the corresponding items should be presented as grids. */
const val CONTENT_STYLE_GRID_ITEM_HINT_VALUE = 2

/**
 * Specifies that the corresponding items should be presented as lists and are represented by a
 * vector icon. This adds a small margin around the icons instead of filling the full available
 * area.
 */
const val CONTENT_STYLE_CATEGORY_LIST_ITEM_HINT_VALUE = 3

/**
 * Specifies that the corresponding items should be presented as grids and are represented by a
 * vector icon. This adds a small margin around the icons instead of filling the full available
 * area.
 */
const val CONTENT_STYLE_CATEGORY_GRID_ITEM_HINT_VALUE = 4
