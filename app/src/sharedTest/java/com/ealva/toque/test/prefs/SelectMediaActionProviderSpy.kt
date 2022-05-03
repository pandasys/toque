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

package com.ealva.toque.test.prefs

import com.ealva.toque.persist.MediaIdList
import com.ealva.toque.prefs.SelectMediaActionProvider

@Suppress("PropertyName")
class SelectMediaActionProviderSpy : SelectMediaActionProvider {
  var _playSongList: MediaIdList? = null
  var _playSongListCalled = 0
  override fun playMediaList(mediaIdList: MediaIdList) {
    _playSongList = mediaIdList
    _playSongListCalled++
  }

  var _playListNext: MediaIdList? = null
  var _playListNextCalled = 0
  override fun playMediaListNext(mediaIdList: MediaIdList) {
    _playListNext = mediaIdList
    _playListNextCalled++
  }

  var _addListToUpNext: MediaIdList? = null
  var _addListToUpNextCalled = 0
  override fun addMediaListToUpNext(mediaIdList: MediaIdList) {
    _addListToUpNext = mediaIdList
    _addListToUpNextCalled++
  }
}
