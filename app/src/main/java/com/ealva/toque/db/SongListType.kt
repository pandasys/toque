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

private val allOrderByList: List<OrderByItem> by lazy {
  listOf(MediaTable.TITLE_ORDER)
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
    ): MediaIdList {
      @Suppress("RemoveRedundantQualifierName") // NOT REDUNDANT - Idea is currently fubar
      return Album.getNextList(openHelper, prefs, 0)   // get first album list
    }
  },
  Album(2, true, albumOrderByList, false) {
    override fun getNextList(
      openHelper: DbOpenHelper,
      prefs: AppPreferences,
      _id: Long
    ): MediaIdList {
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
    ): MediaIdList {
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
    ): MediaIdList {
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
    ): MediaIdList {
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
    ): MediaIdList {
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
    ): MediaIdList {
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
    ): MediaIdList {
      try {
        val mediaIdList = openHelper.smartPlaylistTable.getPlaylistEndOfListAction(_id)
          .getMediaIdList(openHelper, _id)
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
    ): MediaIdList {
      @Suppress("RemoveRedundantQualifierName")   // not redundant - Idea's checker is fubar
      return Album.getNextList(openHelper, prefs, 0)
    }
  };

  fun allowViewSort(): Boolean {
    return allowViewSort
  }

  abstract fun getNextList(openHelper: DbOpenHelper, prefs: AppPreferences, _id: Long): MediaIdList

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
  ): MediaIdList {
    var nextId = table.getNextId(id)
    while (nextId > 0) {
      val idList = makeList(LongLists.singleton(nextId), orderBy, prefs.allowDuplicates)
      if (!idList.isEmpty()) {
        return MediaIdList(idList, this, nextId)
      } else {
        nextId = table.getNextId(nextId)
      }
    }
    return MediaIdList(LongLists.EMPTY_LIST, nextType, 0)
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
