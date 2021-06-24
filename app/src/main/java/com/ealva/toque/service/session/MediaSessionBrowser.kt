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
import android.net.Uri
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
import com.ealva.toque.db.AlbumDao
import com.ealva.toque.db.ArtistDao
import com.ealva.toque.db.AudioDescription
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.DaoMessage
import com.ealva.toque.db.GenreDao
import com.ealva.toque.log._i
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
import com.ealva.toque.service.session.MediaSessionBrowser.Companion.ID_RECENT_ROOT
import com.ealva.toque.service.session.MediaSessionBrowser.Companion.ID_ROOT
import com.ealva.toque.service.session.MediaSessionBrowser.Companion.handleMedia
import com.ealva.toque.service.session.MediaSessionBrowser.Companion.makeMediaId
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapAll
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
      recentMediaProvider: RecentMediaProvider,
      scope: CoroutineScope,
      dispatcher: CoroutineDispatcher? = null
    ): MediaSessionBrowser = MediaSessionBrowserImpl(recentMediaProvider, scope, dispatcher)

    const val ID_ROOT = "ROOT_ID"
    const val ID_RECENT_ROOT = "RECENTS_ROOT_ID"
    const val ALBUM_PREFIX = "album"
    const val ARTIST_PREFIX = "artist"
    const val GENRE_PREFIX = "genre"
    const val PLAYLIST_PREFIX = "playlist"
    const val COMPOSER_PREFIX = "composer"
    const val ID_NO_MEDIA = "NO_MEDIA_ID"
    const val ID_NO_PLAYLIST = "NO_PLAYLIST_ID"

    fun makeMediaId(id: PersistentId): String {
      val prefix = when (id) {
        is MediaId -> return id.value.toString()
        is AlbumId -> ALBUM_PREFIX
        is ArtistId -> ARTIST_PREFIX
        is GenreId -> GENRE_PREFIX
        is PlaylistId -> PLAYLIST_PREFIX
        is ComposerId -> COMPOSER_PREFIX
        else -> throw IllegalArgumentException("Unrecognized PersistentId type for $id")
      }
      return "${prefix}_${id.value}"
    }

    /** Throws IllegalArgumentException if [mediaId] is not of a recognized type */
    suspend fun <T> handleMedia(
      mediaId: String,
      extras: Bundle,
      onMediaType: OnMediaType<T>
    ): T {
      val list = mediaId.split('_')
      return if (list.size > 1) {
        val id = list[1].toLongOrNull() ?: -1
        when (list[0]) {
          ARTIST_PREFIX -> onMediaType.onArtist(id.toArtistId(), extras)
          ALBUM_PREFIX -> onMediaType.onAlbum(id.toAlbumId(), extras)
          GENRE_PREFIX -> onMediaType.onGenre(id.toGenreId(), extras)
          COMPOSER_PREFIX -> onMediaType.onComposer(id.toComposerId(), extras)
          PLAYLIST_PREFIX -> onMediaType.onPlaylist(id.toPlaylistId(), extras)
          else -> throw IllegalArgumentException("Unrecognized MediaId:$mediaId")
        }
      } else {
        val id = mediaId.toLongOrNull()
          ?: throw IllegalArgumentException("Unrecognized MediaId:$mediaId")
        onMediaType.onMedia(id.toMediaId(), extras)
      }
    }
  }
}

private typealias ItemListResult = Result<List<MediaItem>, DaoMessage>

interface OnMediaType<T> {
  suspend fun onMedia(mediaId: MediaId, extras: Bundle): T
  suspend fun onArtist(artistId: ArtistId, extras: Bundle): T
  suspend fun onAlbum(albumId: AlbumId, extras: Bundle): T
  suspend fun onGenre(genreId: GenreId, extras: Bundle): T
  suspend fun onComposer(composerId: ComposerId, extras: Bundle): T
  suspend fun onPlaylist(playlistId: PlaylistId, extras: Bundle): T
}

private class MediaSessionBrowserImpl(
  private val recentMediaProvider: RecentMediaProvider,
  private val scope: CoroutineScope,
  dispatcher: CoroutineDispatcher?
) : MediaSessionBrowser, KoinComponent {
  private val dispatcher = dispatcher ?: Dispatchers.IO
  private val context: Context by inject()
  private val artistDao: ArtistDao by inject()
  private val albumDao: AlbumDao by inject()
  private val genreDao: GenreDao by inject()
  private val audioMediaDao: AudioMediaDao by inject()
  private val packageValidator = PackageValidator(context, R.xml.allowed_media_browser_callers)

  override fun onGetRoot(
    clientPackageName: String,
    clientUid: Int,
    rootHints: Bundle
  ): BrowserRoot? {
    val isKnownCaller = packageValidator.isKnownCaller(clientPackageName, clientUid)
    return if (isKnownCaller) {
      makeBrowserRoot(rootHints)
    } else null
  }

  private fun makeBrowserRoot(rootHints: Bundle): BrowserRoot {
    val rootExtras = Bundle().apply {
      putBoolean(MEDIA_SEARCH_SUPPORTED, true)
      putBoolean(CONTENT_STYLE_SUPPORTED, true)
      putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_GRID)
      putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST)
    }
    return if (rootHints.getBoolean(BrowserRoot.EXTRA_RECENT)) {
      // Return a tree with a single playable media item for resumption.
      rootExtras.putBoolean(BrowserRoot.EXTRA_RECENT, true)
      BrowserRoot(ID_RECENT_ROOT, rootExtras)
    } else
      BrowserRoot(ID_ROOT, rootExtras)
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
        ID_ROOT -> makeRootList()
        ID_RECENT_ROOT -> makeRecentTrack()
        ID_LIBRARY -> makeLibraryList()
        ID_ARTISTS -> valueFromList { makeArtistList() }
        ID_ALBUMS -> valueFromList { makeAlbumList() }
        ID_GENRES -> valueFromList { makeGenreList() }
        ID_TRACKS -> valueFromList { makeTrackList() }
        else -> {
          handleMedia(
            parentId,
            Bundle.EMPTY,
            object : OnMediaType<List<MediaItem>> {
              // Media have no children
              override suspend fun onMedia(
                mediaId: MediaId,
                extras: Bundle
              ): List<MediaItem> = emptyList()

              override suspend fun onArtist(artistId: ArtistId, extras: Bundle): List<MediaItem> =
                valueFromList { makeArtistAlbumList(artistId) }

              override suspend fun onAlbum(
                albumId: AlbumId,
                extras: Bundle
              ): List<MediaItem> = valueFromList { makeAlbumTracksList(albumId) }

              override suspend fun onGenre(
                genreId: GenreId,
                extras: Bundle
              ): List<MediaItem> = valueFromList { makeGenreTracksList(genreId) }

              override suspend fun onComposer(
                composerId: ComposerId,
                extras: Bundle
              ): List<MediaItem> {
                TODO("Not yet implemented")
              }

              override suspend fun onPlaylist(
                playlistId: PlaylistId,
                extras: Bundle
              ): List<MediaItem> {
                TODO("Not yet implemented")
              }
            }
          )
        }
      }.apply {
        if (isEmpty()) listOf(MediaItem(makeEmptyMediaDesc(parentId), Playable))
      }
    } catch (e: Exception) {
      LOG.e(e) { it("Error getChildren(\"%s\")") }
      emptyList()
    }
  }

  private fun makeRootList(): List<MediaItem> = listOf(
    MediaItem(makePlaylistItemDesc(), Browsable),
    MediaItem(makeLibraryItemDesc(), Browsable)
  )

  private fun makeLibraryItemDesc() =
    makeItemDesc(ID_LIBRARY, context.getString(R.string.Library), LIBRARY_ICON)

  private fun makePlaylistItemDesc() = makeItemDesc(
    ID_PLAYLISTS,
    context.getString(R.string.Playlists),
    PLAYLIST_ICON,
    extras = getContentStyle(CONTENT_STYLE_GRID, CONTENT_STYLE_GRID)
  )

  private fun makeRecentTrack(): List<MediaItem> {
    return recentMediaProvider.getRecentMedia()?.let { description ->
      listOf(MediaItem(description, Playable))
    } ?: emptyList()
  }

  private fun makeLibraryList(): List<MediaItem> {
    return listOf(
      MediaItem(makeArtistListItemDesc(), Browsable),
      MediaItem(makeAlbumListItemDesc(), Browsable),
      MediaItem(makeGenreListItemDesc(), Browsable),
      MediaItem(makeTrackListItemDesc(), Browsable),
    )
  }

  private fun makeArtistListItemDesc(): MediaDescriptionCompat =
    makeItemDesc(ID_ARTISTS, context.getString(R.string.Artists), ARTIST_ICON)

  private fun makeAlbumListItemDesc(): MediaDescriptionCompat = makeItemDesc(
    ID_ALBUMS,
    context.getString(R.string.Albums),
    ALBUM_ICON,
    extras = getContentStyle(CONTENT_STYLE_GRID, CONTENT_STYLE_LIST)
  )

  private suspend fun makeArtistList(): Result<List<MediaItem>, DaoMessage> = artistDao
    .getAllArtists(MAX_LIST_SIZE)
    .mapAll {
      Ok(MediaItem(makeItemDesc(it.artistId, it.artistName, ARTIST_ICON), Browsable))
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
    makeItemDesc(makeMediaId(id), name, icon, subtitle)

  private suspend fun makeAlbumList(): Result<List<MediaItem>, DaoMessage> = albumDao
    .getAllAlbums(MAX_LIST_SIZE)
    .mapAll {
      Ok(
        MediaItem(
          makeItemDesc(
            it.albumId,
            it.albumName,
            selectAlbumArt(it.albumLocalArt, it.albumArt, ALBUM_ICON),
            it.artistName.value,
          ),
          Browsable
        )
      )
    }

  private suspend fun makeArtistAlbumList(
    artistId: ArtistId
  ): Result<List<MediaItem>, DaoMessage> = albumDao
    .getAllAlbumsFor(artistId, MAX_LIST_SIZE)
    .mapAll {
      Ok(
        MediaItem(
          makeItemDesc(
            it.albumId,
            it.albumName,
            selectAlbumArt(it.albumLocalArt, it.albumArt, ALBUM_ICON),
            it.artistName.value
          ),
          Browsable
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

  private suspend fun makeGenreList(): Result<List<MediaItem>, DaoMessage> = genreDao
    .getAllGenreNames(MAX_LIST_SIZE)
    .mapAll { Ok(MediaItem(makeItemDesc(it.genreId, it.genreName, GENRE_ICON), Browsable)) }

  private fun makeGenreListItemDesc(): MediaDescriptionCompat =
    makeItemDesc(ID_GENRES, context.getString(R.string.Genres), GENRE_ICON)

  private suspend fun makeTrackList(): Result<List<MediaItem>, DaoMessage> = audioMediaDao
    .getAllAudio(MAX_LIST_SIZE)
    .mapToMediaList()

  private fun makeTrackListItemDesc(): MediaDescriptionCompat =
    makeItemDesc(ID_TRACKS, context.getString(R.string.Tracks), TRACK_ICON)

//  private suspend fun makeArtistTracksList(
//    artistId: ArtistId
//  ): Result<List<MediaItem>, DaoMessage> = audioMediaDao
//    .getAllAudioFor(artistId, MAX_LIST_SIZE)
//    .mapToMediaList()

  private suspend fun makeAlbumTracksList(
    albumId: AlbumId
  ): Result<List<MediaItem>, DaoMessage> = audioMediaDao
    .getAllAudioFor(albumId, MAX_LIST_SIZE)
    .mapToMediaList()

  private fun Result<Iterable<AudioDescription>, DaoMessage>.mapToMediaList(): ItemListResult =
    mapAll { item ->
      Ok(
        MediaItem(
          makeItemDesc(
            item.mediaId,
            item.title.value,
            selectAlbumArt(item.albumLocalArt, item.albumArt, TRACK_ICON)
          ),
          Playable
        )
      )
    }

  private suspend fun makeGenreTracksList(
    genreId: GenreId
  ): Result<List<MediaItem>, DaoMessage> = audioMediaDao
    .getAllAudioFor(genreId, MAX_LIST_SIZE)
    .mapToMediaList()

  private fun makeEmptyMediaDesc(parentId: String) = MediaDescriptionCompat.Builder()
    .setMediaId(ID_NO_MEDIA)
    .setIconUri(TRACK_ICON)
    .setTitle(context.getString(R.string.No_media_found))
    .apply {
      when (parentId) {
        ID_ARTISTS -> setIconUri(ARTIST_ICON)
        ID_ALBUMS -> setIconUri(ALBUM_ICON)
        ID_GENRES -> setIconUri(null)
        ID_PLAYLISTS -> {
          setMediaId(ID_NO_PLAYLIST)
          setTitle(context.getString(R.string.No_playlist_found))
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
      when (extras[EXTRA_MEDIA_FOCUS]) {
        MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE -> {
          extras.getString(EXTRA_MEDIA_GENRE)?.let { genre ->
            LOG._i { it("Search genre '%s'", genre) }
            emptyList()
          } ?: emptyList()
        }
        MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE -> {
          extras.getString(EXTRA_MEDIA_ARTIST)?.let { artist ->
            // song.artist == artist || song.albumArtist == artist)
            LOG._i { it("Search artist '%s'", artist) }
            emptyList()
          } ?: emptyList()
        }
        MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE -> {
          val artist = extras.getString(EXTRA_MEDIA_ARTIST, "")
          val album = extras.getString(EXTRA_MEDIA_ALBUM, "")
          LOG._i { it("Search album '%s' artist '%s'", album, artist) }
          // (song.artist == artist || song.albumArtist == artist) && song.album == album
          emptyList()
        }
        MediaStore.Audio.Media.ENTRY_CONTENT_TYPE -> {
          val title = extras.getString(EXTRA_MEDIA_TITLE, "")
          val album = extras.getString(EXTRA_MEDIA_ALBUM, "")
          val artist = extras.getString(EXTRA_MEDIA_ARTIST, "")
          LOG._i { it("Search media title '%s' album '%s' artist '%s'", title, album, artist) }
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
    private val STREAM_ICON = "$BASE_DRAWABLE_URI/${R.drawable.ic_auto_stream}".toUri()
    private val PLAYLIST_ICON = "$BASE_DRAWABLE_URI/${R.drawable.ic_auto_playlist}".toUri()
    private val GENRE_ICON = "$BASE_DRAWABLE_URI/${R.drawable.ic_auto_drama_masks}".toUri()
    private val TRACK_ICON = "$BASE_DRAWABLE_URI/${R.drawable.ic_auto_audio}".toUri()
    private val LIBRARY_ICON = "$BASE_DRAWABLE_URI/${R.drawable.ic_auto_library_music}".toUri()

    private const val MAX_LIST_SIZE = 1000L

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
const val CONTENT_STYLE_LIST = 1

/** Specifies that the corresponding items should be presented as grids. */
const val CONTENT_STYLE_GRID = 2

/**
 * Specifies that the corresponding items should be presented as lists and are represented by a
 * vector icon. This adds a small margin around the icons instead of filling the full available
 * area.
 */
const val CONTENT_STYLE_CATEGORY_LIST = 3

/**
 * Specifies that the corresponding items should be presented as grids and are represented by a
 * vector icon. This adds a small margin around the icons instead of filling the full available
 * area.
 */
const val CONTENT_STYLE_CATEGORY_GRID = 4

const val MEDIA_SEARCH_SUPPORTED = "android.media.browse.SEARCH_SUPPORTED"
