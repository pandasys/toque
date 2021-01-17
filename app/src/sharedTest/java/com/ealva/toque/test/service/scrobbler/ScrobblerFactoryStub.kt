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

@file:Suppress("PropertyName")

package com.ealva.toque.test.service.scrobbler

import com.ealva.toque.prefs.ScrobblerPackage
import com.ealva.toque.service.scrobble.NullScrobbler
import com.ealva.toque.service.scrobble.Scrobbler
import com.ealva.toque.service.scrobble.ScrobblerFactory

class ScrobblerFactoryStub : ScrobblerFactory {
  var _makeCalled = 0
  val _makeItems: MutableList<ScrobblerPackage> = mutableListOf()
  var _makeReturns: MutableList<Scrobbler> = mutableListOf(NullScrobbler)
  override fun make(selectedScrobbler: ScrobblerPackage): Scrobbler =
    if (_makeReturns.size == 1) _makeReturns[0] else _makeReturns[_makeCalled].also {
      _makeCalled++
      _makeItems.add(selectedScrobbler)
    }
}
