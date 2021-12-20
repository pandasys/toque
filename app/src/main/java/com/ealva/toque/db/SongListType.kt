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

package com.ealva.toque.db

import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.persist.HasConstId

private val LOG by lazyLogger(SongListType::class)

@Suppress("unused")
private val allOrderByList: List<OrderByItem> by lazy {
  listOf(MediaTable.TITLE_ORDER)
}

/**
 * Don't ever change [id] or lists will be lost. New [id]s maybe added. A new [id] can be added and
 * mapped to from an old [id] if that ever becomes necessary.
 */
enum class SongListType(override val id: Int) : HasConstId {
  All(1),
  Album(2),
  Artist(3),
  Composer(4),
  Genre(5),
  PlayList(7),
  External(9);

  companion object {
    /**
     * Items in this list all possibly generate audio lists and are ordered for next/previous
     * selection.
     */
    private val generatingLists = listOf(
      Album,
      Artist,
      Composer,
      Genre,
      PlayList
    )

    fun getRandomType(): SongListType = generatingLists.random()
  }
}
