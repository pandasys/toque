/*
 * Copyright 2020 eAlva.com
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
import com.ealva.toque.common.Millis
import com.ealva.toque.media.StarRating

inline fun String.updateOrNull(block: () -> String): String? =
  block().let { newValue -> if (newValue.isNotEmpty() && this != newValue) newValue else null }

inline fun <T : Mbid> T.updateOrNull(block: () -> T?): T? =
  block().let { newValue -> if (isObsolete(newValue)) newValue else null }

inline fun <T : PersistentId> T.updateOrNull(block: () -> T): T? =
  block().let { newValue -> if (id != newValue.id) newValue else null }

fun Long.updateOrNull(block: () -> Long): Long? =
  block().let { newValue -> if (this != newValue) newValue else null }

inline fun Int.updateOrNull(block: () -> Int): Int? =
  block().let { newValue -> if (newValue != this) newValue else null }

inline fun StarRating.updateOrNull(block: () -> StarRating): StarRating? =
  block().let { newValue -> if (this != newValue) newValue else null }

fun Millis.updateOrNull(block: () -> Millis): Millis? =
  block().let { newValue -> if (this != newValue) newValue else null }

inline fun anyNotNull(block: () -> Array<Any?>): Boolean = block().any { it != null }
