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

import com.ealva.ealvabrainz.brainz.data.Mbid
import com.ealva.ealvabrainz.brainz.data.isObsolete

/**
 * Return this if it [isNotBlank] blank and != to the [current], else null
 */
fun String.takeIfSupersedes(current: String): String? = takeIf { it.isNotBlank() && it != current }

/**
 * Return this Mbid is valid and != to [oldValue], else null
 */
fun <T : Mbid> T.takeIfSupersedes(oldValue: T): T? =
  takeIf { new -> oldValue.isObsolete(new) }

/**
 * Return this if > 0 and != [oldValue]
 */
fun Int.takeIfSupersedes(oldValue: Int): Int? = takeIf { it > 0 && it != oldValue }

/**
 * Returns true if any of the values are not null
 */
fun anyNotNull(vararg values: Any?): Boolean = values.any { it != null }

