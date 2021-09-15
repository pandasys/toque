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

package com.ealva.toque.db

import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvabrainz.common.ComposerName
import com.ealva.toque.persist.HasConstId
import com.ealva.toque.prefs.AppPrefs
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok

private val allOrderByList: List<OrderByItem> by lazy {
  listOf(MediaTable.TITLE_ORDER)
}

//enum class SongListType(
//  override val id: Int
//) : HasConstId {
//  All(1),
//  Album(2),
//  Artist(3),
//  Composer(4)
//}

enum class SongListType(
  override val id: Int
) : HasConstId {
  All(1),
//  {
//    override suspend fun getNextList(
//      audioMediaDao: AudioMediaDao,
//      appPrefs: AppPrefs,
//      name: String
//    ): AudioIdList {
//      return Album.getNextList(audioMediaDao, appPrefs, "") // get first album list
//    }
//  },
  Album(2),
//  {
//    override suspend fun getNextList(
//      audioMediaDao: AudioMediaDao,
//      appPrefs: AppPrefs,
//      name: String
//    ): AudioIdList {
//      return when (val result = audioMediaDao.getNextAlbumList(AlbumTitle(name))) {
//        is Ok -> result.value
//        is Err -> Artist.getNextList(audioMediaDao, appPrefs, "")
//      }
//    }
//  },
  Artist(3),
//  {
//    override suspend fun getNextList(
//      audioMediaDao: AudioMediaDao,
//      appPrefs: AppPrefs,
//      name: String
//    ): AudioIdList {
//      return nextList(
//        appPrefs,
//        name,
//        audioMediaDao.artists,
//        Genre,
//        defaultOrderByItems,
//        audioMediaDao.mediaFileTable::getSongsForArtists
//      )
//    }
//  },
  Composer(4),
//    override suspend fun getNextList(
//      audioMediaDao: AudioMediaDao,
//      appPrefs: AppPrefs,
//      name: String
//    ): AudioIdList {
//      return EMPTY_MEDIA_ID_LIST
////      return nextList(
////        appPrefs,
////        name,
////        audioMediaDao.composerTable,
////        Folder,
////        defaultOrderByItems,
////        audioMediaDao.mediaFileTable::getSongsForComposers
////      )
//    }
//  },
  Genre(5),
//    override suspend fun getNextList(
//      audioMediaDao: AudioMediaDao,
//      appPrefs: AppPrefs,
//      name: String
//    ): AudioIdList {
//      return EMPTY_MEDIA_ID_LIST
////      return nextList(
////        appPrefs,
////        name,
////        audioMediaDao.genreTable,
////        Composer,
////        defaultOrderByItems,
////        audioMediaDao.mediaFileTable::getSongsForGenres
////      )
//    }
//  },
  Folder(6),
//    override suspend fun getNextList(
//      audioMediaDao: AudioMediaDao,
//      appPrefs: AppPrefs,
//      name: String
//    ): AudioIdList {
//      return EMPTY_MEDIA_ID_LIST
////      return nextList(
////        appPrefs,
////        name,
////        audioMediaDao.folderTable,
////        PlayList,
////        defaultOrderByItems,
////        audioMediaDao.mediaFileTable::getSongsForFolders
////      )
//    }
//
////    override fun onCreateOptionsMenu(
////      context: Context,
////      scope: CoroutineScope,
////      menu: Menu,
////      prefs: AppPreferences
////    ) {
////      menu.addSub(R.string.Display).run {
////        val folderItemSongFetcher = prefs.folderItemSongFetcher
////        addItem(R.string.Title, R.id.action_show_title, R.id.menu_group_folder_title_type)
////          .also { it.isChecked = SongTitleFetcher.Title === folderItemSongFetcher }
////          .onMenuItemClick(scope) { item ->
////            item.isChecked = true
////            prefs.folderItemSongFetcher = SongTitleFetcher.Title
////          }
////        addItem(R.string.FileName, R.id.action_show_file_name, R.id.menu_group_folder_title_type)
////          .also { it.isChecked = SongTitleFetcher.FileName === folderItemSongFetcher }
////          .onMenuItemClick(scope) { item ->
////            item.isChecked = true
////            prefs.folderItemSongFetcher = SongTitleFetcher.FileName
////          }
////        setGroupCheckable(R.id.menu_group_folder_title_type, true, true)
////      }
////    }
////
////    override fun getTitleFetcher(prefs: AppPreferences): SongTitleFetcher {
////      return prefs.folderItemSongFetcher
////    }
////
////    override fun preferenceChanged(key: AppPreferences.PreferenceKey): Boolean {
////      return key == AppPreferences.PreferenceKey.FolderSongItemTitleFetcher
////    }
//  },
  PlayList(7),
//    override suspend fun getNextList(
//      audioMediaDao: AudioMediaDao, appPrefs: AppPrefs, name: String
//    ): AudioIdList {
//      return EMPTY_MEDIA_ID_LIST
////      return nextList(
////        appPrefs,
////        name,
////        audioMediaDao.playListStore,
////        Album,
////        defaultOrderByItems,
////        audioMediaDao.mediaFileTable::getSongsForPlaylists
////      )
//    }
//  },
  SmartPlaylist(8),
//    override suspend fun getNextList(
//      audioMediaDao: AudioMediaDao,
//      appPrefs: AppPrefs,
//      name: String
//    ): AudioIdList {
//      return EMPTY_MEDIA_ID_LIST
////      try {
////        val mediaIdList = audioMediaDao.smartPlaylistTable.getPlaylistEndOfListAction(name)
////          .getAudioIdList(audioMediaDao, name)
////        if (mediaIdList.isNotEmpty()) return mediaIdList
////      } catch (e: NoSuchItemException) {
////        LOG.w(e) { +it }
////      }
////      return nextList(
////        appPrefs,
////        name,
////        audioMediaDao.smartPlaylistTable,
////        PlayList,
////        defaultOrderByItems,
////        audioMediaDao.mediaFileTable::getSongsForPlaylists
////      )
//    }
//  },
  External(9)
//    override suspend fun getNextList(
//      audioMediaDao: AudioMediaDao,
//      appPrefs: AppPrefs,
//      name: String
//    ): AudioIdList {
//      return Album.getNextList(audioMediaDao, appPrefs, "")
//    }
//  };

//  fun allowViewSort(): Boolean {
//    return allowViewSort
//  }

//  abstract suspend fun getNextList(
//    audioMediaDao: AudioMediaDao,
//    appPrefs: AppPrefs,
//    name: String
//  ): AudioIdList
//
//  @Suppress("unused")
//  fun supportSectionIndexing(): Boolean {
//    return supportSectionIndexing
//  }

//  protected fun nextList(
//    prefs: AppPreferences,
//    id: Long,
//    table: IdCollection,
//    nextType: SongListType,
//    orderBy: List<OrderByItem>,
//    makeList: (idList: LongList, orderBy: List<OrderByItem>, allowDuplicates: Boolean) -> LongList
//  ): AudioIdList {
//    var nextId = table.getNextId(id)
//    while (nextId > 0) {
//      val idList = makeList(LongLists.singleton(nextId), orderBy, prefs.allowDuplicates)
//      if (!idList.isEmpty()) {
//        return AudioIdList(idList, this, nextId)
//      } else {
//        nextId = table.getNextId(nextId)
//      }
//    }
//    return AudioIdList(LongLists.EMPTY_LIST, nextType, 0)
//  }

//  open fun onCreateOptionsMenu(
//    context: Context,
//    scope: CoroutineScope,
//    menu: Menu,
//    prefs: AppPreferences
//  ) {}
//
//  open fun getTitleFetcher(prefs: AppPreferences): SongTitleFetcher {
//    return SongTitleFetcher.Title
//  }
//
//  open fun preferenceChanged(key: AppPreferences.PreferenceKey): Boolean = false
}

/*
private val albumOrderByList: List<OrderByItem> by lazy {
  OrderByItem.makeList(MediaFileTable.ORDER_BY_DISC, MediaFileTable.ORDER_BY_TRACK)
}

private val artistOrderByList: List<OrderByItem> by lazy {
  OrderByItem.makeList(
    MediaFileTable.ORDER_BY_YEAR,
    MediaFileTable.ORDER_BY_ALBUM,
    MediaFileTable.ORDER_BY_DISC,
    MediaFileTable.ORDER_BY_TRACK
  )
}

private val defaultOrderByList: List<OrderByItem> by lazy {
  OrderByItem.makeList(
    MediaFileTable.ORDER_BY_ARTIST,
    MediaFileTable.ORDER_BY_YEAR,
    MediaFileTable.ORDER_BY_ALBUM,
    MediaFileTable.ORDER_BY_DISC,
    MediaFileTable.ORDER_BY_TRACK
  )
}

enum class SongListType(
  override val id: Int,
  private val allowViewSort: Boolean,
  val defaultOrderByItems: List<OrderBy>,
  private val supportSectionIndexing: Boolean
) : HasConstId {
  All(1, true, allOrderByList, true) {
    override fun getNextList(
      openHelper: DbOpenHelper,
      prefs: AppPreferences,
      _id: Long
    ): AudioIdList {
      @Suppress("RemoveRedundantQualifierName") // NOT REDUNDANT - Idea is currently fubar
      return Album.getNextList(openHelper, prefs, 0)   // get first album list
    }
  },
  Album(2, true, albumOrderByList, false) {
    override fun getNextList(
      openHelper: DbOpenHelper,
      prefs: AppPreferences,
      _id: Long
    ): AudioIdList {
      return nextList(
        prefs,
        _id,
        openHelper.albumTable,
        Artist,
        defaultOrderByItems,
        openHelper.mediaFileTable::getSongsForAlbums
      )
    }
  },
  Artist(3, true, artistOrderByList, true) {
    override fun getNextList(
      openHelper: DbOpenHelper,
      prefs: AppPreferences,
      _id: Long
    ): AudioIdList {
      return nextList(
        prefs,
        _id,
        openHelper.artists,
        Genre,
        defaultOrderByItems,
        openHelper.mediaFileTable::getSongsForArtists
      )
    }
  },
  Composer(4, true, defaultOrderByList, true) {
    override fun getNextList(
      openHelper: DbOpenHelper,
      prefs: AppPreferences,
      _id: Long
    ): AudioIdList {
      return nextList(
        prefs,
        _id,
        openHelper.composerTable,
        Folder,
        defaultOrderByItems,
        openHelper.mediaFileTable::getSongsForComposers
      )
    }
  },
  Genre(5, true, defaultOrderByList, true) {
    override fun getNextList(
      openHelper: DbOpenHelper,
      prefs: AppPreferences,
      _id: Long
    ): AudioIdList {
      return nextList(
        prefs,
        _id,
        openHelper.genreTable,
        Composer,
        defaultOrderByItems,
        openHelper.mediaFileTable::getSongsForGenres
      )
    }
  },
  Folder(6, true, defaultOrderByList, false) {
    override fun getNextList(
      openHelper: DbOpenHelper,
      prefs: AppPreferences,
      _id: Long
    ): AudioIdList {
      return nextList(
        prefs,
        _id,
        openHelper.folderTable,
        PlayList,
        defaultOrderByItems,
        openHelper.mediaFileTable::getSongsForFolders
      )
    }

    override fun onCreateOptionsMenu(
      context: Context,
      scope: CoroutineScope,
      menu: Menu,
      prefs: AppPreferences
    ) {
      menu.addSub(R.string.Display).run {
        val folderItemSongFetcher = prefs.folderItemSongFetcher
        addItem(R.string.Title, R.id.action_show_title, R.id.menu_group_folder_title_type)
          .also { it.isChecked = SongTitleFetcher.Title === folderItemSongFetcher }
          .onMenuItemClick(scope) { item ->
            item.isChecked = true
            prefs.folderItemSongFetcher = SongTitleFetcher.Title
          }
        addItem(R.string.FileName, R.id.action_show_file_name, R.id.menu_group_folder_title_type)
          .also { it.isChecked = SongTitleFetcher.FileName === folderItemSongFetcher }
          .onMenuItemClick(scope) { item ->
            item.isChecked = true
            prefs.folderItemSongFetcher = SongTitleFetcher.FileName
          }
        setGroupCheckable(R.id.menu_group_folder_title_type, true, true)
      }
    }

    override fun getTitleFetcher(prefs: AppPreferences): SongTitleFetcher {
      return prefs.folderItemSongFetcher
    }

    override fun preferenceChanged(key: AppPreferences.PreferenceKey): Boolean {
      return key == AppPreferences.PreferenceKey.FolderSongItemTitleFetcher
    }
  },
  PlayList(7, false, emptyList<OrderByItem>(), false) {
    override fun getNextList(
      openHelper: DbOpenHelper, prefs: AppPreferences, _id: Long
    ): AudioIdList {
      return nextList(
        prefs,
        _id,
        openHelper.playListStore,
        Album,
        defaultOrderByItems,
        openHelper.mediaFileTable::getSongsForPlaylists
      )
    }
  },
  SmartPlaylist(8, false, emptyList<OrderByItem>(), false) {
    override fun getNextList(
      openHelper: DbOpenHelper,
      prefs: AppPreferences,
      _id: Long
    ): AudioIdList {
      try {
        val mediaIdList = openHelper.smartPlaylistTable.getPlaylistEndOfListAction(_id)
          .getAudioIdList(openHelper, _id)
        if (mediaIdList.isNotEmpty()) return mediaIdList
      } catch (e: NoSuchItemException) {
        LOG.w(e) { +it }
      }
      return nextList(
        prefs,
        _id,
        openHelper.smartPlaylistTable,
        PlayList,
        defaultOrderByItems,
        openHelper.mediaFileTable::getSongsForPlaylists
      )
    }
  },
  External(9, false, emptyList<OrderByItem>(), false) {
    override fun getNextList(
      openHelper: DbOpenHelper,
      prefs: AppPreferences,
      _id: Long
    ): AudioIdList {
      @Suppress("RemoveRedundantQualifierName")   // not redundant - Idea's checker is fubar
      return Album.getNextList(openHelper, prefs, 0)
    }
  };

  fun allowViewSort(): Boolean {
    return allowViewSort
  }

  abstract fun getNextList(openHelper: DbOpenHelper, prefs: AppPreferences, _id: Long): AudioIdList

  @Suppress("unused")
  fun supportSectionIndexing(): Boolean {
    return supportSectionIndexing
  }

  protected fun nextList(
    prefs: AppPreferences,
    id: Long,
    table: IdCollection,
    nextType: SongListType,
    orderBy: List<OrderByItem>,
    makeList: (idList: LongList, orderBy: List<OrderByItem>, allowDuplicates: Boolean) -> LongList
  ): AudioIdList {
    var nextId = table.getNextId(id)
    while (nextId > 0) {
      val idList = makeList(LongLists.singleton(nextId), orderBy, prefs.allowDuplicates)
      if (!idList.isEmpty()) {
        return AudioIdList(idList, this, nextId)
      } else {
        nextId = table.getNextId(nextId)
      }
    }
    return AudioIdList(LongLists.EMPTY_LIST, nextType, 0)
  }

  open fun onCreateOptionsMenu(
    context: Context,
    scope: CoroutineScope,
    menu: Menu,
    prefs: AppPreferences
  ) {}

  open fun getTitleFetcher(prefs: AppPreferences): SongTitleFetcher {
    return SongTitleFetcher.Title
  }

  open fun preferenceChanged(key: AppPreferences.PreferenceKey): Boolean = false

  companion object {
    val DEFAULT = All
  }
}
*/

suspend fun SongListType.getNextList(
  audioMediaDao: AudioMediaDao,
  name: String,
  appPrefs: AppPrefs
): AudioIdList {
  return when (this) {
    SongListType.All -> getNextAlbumList(audioMediaDao, name, appPrefs)
    SongListType.Album -> getNextAlbumList(audioMediaDao, name, appPrefs)
    SongListType.Artist -> getNextArtistList(audioMediaDao, name, appPrefs)
    SongListType.Composer -> getNextComposerList(audioMediaDao, name, appPrefs)
    SongListType.Genre -> EMPTY_MEDIA_ID_LIST
    SongListType.Folder -> EMPTY_MEDIA_ID_LIST
    SongListType.PlayList -> EMPTY_MEDIA_ID_LIST
    SongListType.SmartPlaylist -> EMPTY_MEDIA_ID_LIST
    SongListType.External -> EMPTY_MEDIA_ID_LIST
  }
}

private suspend fun getNextAlbumList(
  audioMediaDao: AudioMediaDao,
  name: String,
  appPrefs: AppPrefs
): AudioIdList = when (val result = audioMediaDao.getNextAlbumList(AlbumTitle(name))) {
  is Ok -> result.value
  is Err -> getNextArtistList(audioMediaDao, "", appPrefs)
}

private suspend fun getNextArtistList(
  audioMediaDao: AudioMediaDao,
  name: String,
  appPrefs: AppPrefs
): AudioIdList = when (val result = audioMediaDao.getNextArtistList(ArtistName(name))) {
  is Ok -> result.value
  is Err -> getNextComposerList(audioMediaDao, "", appPrefs)
}

private suspend fun getNextComposerList(
  audioMediaDao: AudioMediaDao,
  name: String,
  appPrefs: AppPrefs
): AudioIdList = when (val result = audioMediaDao.getNextComposerList(ComposerName(name))) {
  is Ok -> result.value
  is Err -> SongListType.Genre.getNextList(audioMediaDao, "", appPrefs)
}
