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

package com.ealva.toque.db.smart

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import com.ealva.toque.common.Rating
import com.ealva.toque.common.StarRating
import com.ealva.toque.common.toRating
import com.ealva.toque.common.toStarRating
import kotlinx.parcelize.Parcelize

@Immutable
@Parcelize
data class MatcherData(val text: String, val first: Long, val second: Long) : Parcelable {
  companion object {
    val EMPTY = MatcherData("", 0, 0)

    operator fun invoke(first: StarRating) = MatcherData(
      "",
      first.toRating().value.toLong(),
      0
    )

    operator fun invoke(first: StarRating, second: StarRating) = MatcherData(
      "",
      first.toRating().value.toLong(),
      second.toRating().value.toLong()
    )

    operator fun invoke(units: TheLast.Units, theLast: TheLast) = MatcherData(
      "",
      units.value.toLong(),
      theLast.id.toLong()
    )
  }
}

val MatcherData.firstAsRating: StarRating
  get() = Rating(first.toInt()).toStarRating()
val MatcherData.secondAsRating: StarRating
  get() = Rating(second.toInt()).toStarRating()
