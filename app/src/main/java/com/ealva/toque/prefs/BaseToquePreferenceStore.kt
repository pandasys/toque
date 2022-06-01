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

@file:Suppress("SameParameterValue")

package com.ealva.toque.prefs

import com.ealva.prefstore.store.BasePreferenceStore
import com.ealva.prefstore.store.PreferenceStore
import com.ealva.prefstore.store.Storage
import com.ealva.prefstore.store.StorePref
import com.ealva.toque.common.Amp
import com.ealva.toque.common.BooleanValue
import com.ealva.toque.common.Millis
import com.ealva.toque.common.Volume
import com.ealva.toque.persist.HasConstId
import com.ealva.toque.persist.reify
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

typealias MillisStorePref = PreferenceStore.Preference<Long, Millis>
typealias VolumeStorePref = PreferenceStore.Preference<Int, Volume>
typealias AmpStorePref = PreferenceStore.Preference<Float, Amp>
typealias DurationPref = PreferenceStore.Preference<Long, Duration>
typealias EnumPref<T> = PreferenceStore.Preference<Int, T>

open class BaseToquePreferenceStore<T : PreferenceStore<T>>(
  storage: Storage
) : BasePreferenceStore<T>(storage) {
  protected fun durationPref(
    default: Duration,
    customName: String? = null,
    sanitize: ((Duration) -> Duration)? = null
  ): DurationPref = asTypePref(
    default = default,
    maker = { millis -> millis.milliseconds },
    serialize = { duration -> duration.inWholeMilliseconds },
    customName = customName,
    sanitize = sanitize
  )

  protected fun millisPref(
    default: Millis,
    customName: String? = null,
    sanitize: ((Millis) -> Millis)? = null
  ): MillisStorePref = asTypePref(default, ::Millis, { it() }, customName, sanitize)

  protected fun <T : BooleanValue> booleanValuePref(
    default: T,
    maker: (Boolean) -> T,
    customName: String? = null,
    sanitize: ((T) -> T)? = null
  ): StorePref<Boolean, T> = asTypePref(default, maker, { it.value }, customName, sanitize)

  protected fun volumePref(
    default: Volume,
    customName: String? = null,
    sanitize: ((Volume) -> Volume)? = null
  ): VolumeStorePref = asTypePref(default, ::Volume, { it() }, customName, sanitize)

  protected fun ampPref(
    default: Amp,
    customName: String? = null,
    sanitize: ((Amp) -> Amp) = { it.coerceIn(Amp.RANGE) }
  ): AmpStorePref = asTypePref(default, ::Amp, { it.value }, customName, sanitize)

  protected inline fun <reified T> enumPref(
    default: T,
    customName: String? = null
  ): EnumPref<T> where T : Enum<T>, T : HasConstId {
    return asTypePref(default, { default::class.reify(it, default) }, { it.id }, customName)
  }
}
