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

package com.ealva.toque.prefs

import com.ealva.toque.persist.HasConstId
import com.ealva.toque.persist.MediaIdList

/**
 * Encapsulate what happens when the user selects a media item of a list.
 */
enum class SelectMediaAction(
  override val id: Int,
  private val action: (SelectMediaActionProvider, MediaIdList) -> Unit
) : HasConstId {
  Play(1, SelectMediaActionProvider::playMediaList),
  PlayNext(2, SelectMediaActionProvider::playMediaListNext),
  AddToUpNext(3, SelectMediaActionProvider::addMediaListToUpNext);

  fun performAction(provider: SelectMediaActionProvider, mediaIdList: MediaIdList) {
    action(provider, mediaIdList)
  }
}
