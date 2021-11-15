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

import androidx.datastore.preferences.core.Preferences
import com.ealva.prefstore.store.PreferenceStore
import com.ealva.prefstore.store.StorePref
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KProperty

@Suppress("PropertyName", "MemberVisibilityCanBePrivate", "unused")
class PrefStub<S : Any, A : Any>(value: A? = null) : StorePref<S, A> {
  var _keys = mutableListOf<Preferences.Key<S>>()
  var _keyCalled: Int = 0
  override val key: Preferences.Key<S>
    get() {
      _keyCalled++
      return _keys.removeAt(0)
    }

  var _value: A? = value
  var _invokeCalled: Int = 0
  override fun invoke(): A {
    _invokeCalled++
    return requireNotNull(_value)
  }

  var _set: A? = null
  var _setCalled: Int = 0
  override suspend fun set(value: A) {
    _setCalled++
    _set = value
    _value = value
  }

  var _default: A? = null
  override val default: A
    get() = requireNotNull(_default)

  var storedToActual_stored: S? = null
  var storedToActual_actuals = mutableListOf<A>()
  var storedToActualCalled: Int = 0
  override fun storedToActual(stored: S?): A {
    storedToActualCalled++
    storedToActual_stored = stored
    return storedToActual_actuals.removeAt(0)
  }

  var actualToStored_storeds = mutableListOf<S>()
  var actualToStored_actual: A? = null
  var actualToStoredCalled: Int = 0
  override fun actualToStored(actual: A): S {
    actualToStoredCalled++
    actualToStored_actual = actual
    return actualToStored_storeds.removeAt(0)
  }

  var _santize: ((A) -> A)? = null
  override val sanitize: ((A) -> A)?
    get() = _santize

  var _asFlow: Flow<A>? = null
  override fun asFlow(): Flow<A> {
    return requireNotNull(_asFlow)
  }

  override fun getValue(
    thisRef: PreferenceStore<*>,
    property: KProperty<*>
  ): PreferenceStore.Preference<S, A> {
    TODO("Not yet implemented")
  }
}
