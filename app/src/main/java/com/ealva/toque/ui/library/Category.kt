/*
 * Copyright 2022 Eric A. Snell
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

package com.ealva.toque.ui.library

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * A [Category] represents a high-level grouping of some type. Example types would be songs, albums,
 * artists, genres, composers, playlists, etc. At a minimum a category should have a default [icon]
 * and [title].
 *
 * A collection of particular type is not necessarily a Category. For example, a PlayList
 * is not a Category, in and of itself, but a particular PlayList could be elevated to a Category.
 * An example would be "Most Played", which would be a SmartPlayList elevated to a Category as it's
 * considered of "top level" importance.
 *
 * For search purposes, there is a finite list of "Search Categories". That list is currently:
 * * All Songs
 * * Albums
 * * Artists
 * * Album Artists
 * * Genres
 * * Composers
 * * Playlists
 */
interface Category {
  @get:DrawableRes val icon: Int
  @get:StringRes val title: Int
}
