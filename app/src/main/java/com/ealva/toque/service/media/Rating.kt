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

@file:Suppress("MagicNumber")

package com.ealva.toque.service.media

import kotlin.math.roundToInt

/*
 All rating names are based on "stars", including half stars, as all ratings are displayed as such.
 Currently ratings are persisted as -1 .. 100, with 0..100 being valid and -1 meaning no rating.
 Each increment of 10 is 1/2 star. Some file tags expect 0..100 while MP3s are 0..256 or "*-"
 characters.
 */

inline class Rating(val value: Int) : Comparable<Rating> {
  override fun compareTo(other: Rating): Int = value.compareTo(other.value)

  val isValid: Boolean
    get() = value != RATING_NONE.value

  companion object {
    val RATING_NONE = Rating(-1)
    val RATING_0 = Rating(0)
    val RATING_0_5 = Rating(10)
    val RATING_1 = Rating(20)
    val RATING_1_5 = Rating(30)
    val RATING_2 = Rating(40)
    val RATING_2_5 = Rating(50)
    val RATING_3 = Rating(60)
    val RATING_3_5 = Rating(70)
    val RATING_4 = Rating(80)
    val RATING_4_5 = Rating(90)
    val RATING_5 = Rating(100)

    val VALID_RANGE = RATING_0..RATING_5
  }
}

fun Rating.coerceToValid() = coerceIn(Rating.VALID_RANGE)

fun Int.toRating(): Rating = when (this) {
  Rating.RATING_0.value -> Rating.RATING_0
  Rating.RATING_0_5.value -> Rating.RATING_0_5
  Rating.RATING_1.value -> Rating.RATING_1
  Rating.RATING_1_5.value -> Rating.RATING_1_5
  Rating.RATING_2.value -> Rating.RATING_2
  Rating.RATING_2_5.value -> Rating.RATING_2_5
  Rating.RATING_3.value -> Rating.RATING_3
  Rating.RATING_3_5.value -> Rating.RATING_3_5
  Rating.RATING_4.value -> Rating.RATING_4
  Rating.RATING_4_5.value -> Rating.RATING_4_5
  Rating.RATING_5.value -> Rating.RATING_5
  else -> when (this) { // fallback from exact values
    in 0..4 -> Rating.RATING_0
    in 5..14 -> Rating.RATING_0_5
    in 15..24 -> Rating.RATING_1
    in 25..34 -> Rating.RATING_1_5
    in 35..44 -> Rating.RATING_2
    in 45..54 -> Rating.RATING_2_5
    in 55..64 -> Rating.RATING_3
    in 65..74 -> Rating.RATING_3_5
    in 75..84 -> Rating.RATING_4
    in 85..94 -> Rating.RATING_4_5
    in 95..Int.MAX_VALUE -> Rating.RATING_5
    else -> Rating.RATING_NONE
  }
}

fun String.toRating(): Rating = toIntOrNull()?.toRating() ?: Rating.RATING_NONE

inline class StarRating(val value: Float) : Comparable<StarRating> {
  override fun compareTo(other: StarRating): Int = value.compareTo(other.value)

  val isValid: Boolean
    get() = value != STAR_NONE.value

  companion object {
    val STAR_NONE = StarRating(-1.0F)
    val STAR_0 = StarRating(0.0F)
    val STAR_0_5 = StarRating(0.5F)
    val STAR_1 = StarRating(1.0F)
    val STAR_1_5 = StarRating(1.5F)
    val STAR_2 = StarRating(2.0F)
    val STAR_2_5 = StarRating(2.5F)
    val STAR_3 = StarRating(3.0F)
    val STAR_3_5 = StarRating(3.5F)
    val STAR_4 = StarRating(4.0F)
    val STAR_4_5 = StarRating(4.5F)
    val STAR_5 = StarRating(5.0F)

    val VALID_RANGE = STAR_0..STAR_5
  }
}

fun StarRating.coerceToValid() = coerceIn(StarRating.VALID_RANGE)

fun Float.toStarRating(): StarRating {
  return when (this) {
    StarRating.STAR_0.value -> StarRating.STAR_0
    StarRating.STAR_0_5.value -> StarRating.STAR_0_5
    StarRating.STAR_1.value -> StarRating.STAR_1
    StarRating.STAR_1_5.value -> StarRating.STAR_1_5
    StarRating.STAR_2.value -> StarRating.STAR_2
    StarRating.STAR_2_5.value -> StarRating.STAR_2_5
    StarRating.STAR_3.value -> StarRating.STAR_3
    StarRating.STAR_3_5.value -> StarRating.STAR_3_5
    StarRating.STAR_4.value -> StarRating.STAR_4
    StarRating.STAR_4_5.value -> StarRating.STAR_4_5
    StarRating.STAR_5.value -> StarRating.STAR_5
    else -> when ((this * 100).roundToInt()) { // fallback from exact values
      in 0..24 -> StarRating.STAR_0 // .5 = 50
      in 25..74 -> StarRating.STAR_0_5
      in 75..124 -> StarRating.STAR_1
      in 125..174 -> StarRating.STAR_1_5
      in 175..224 -> StarRating.STAR_2
      in 225..274 -> StarRating.STAR_2_5
      in 275..324 -> StarRating.STAR_3
      in 325..374 -> StarRating.STAR_3_5
      in 375..424 -> StarRating.STAR_4
      in 425..474 -> StarRating.STAR_4_5
      in 475..Int.MAX_VALUE -> StarRating.STAR_5
      else -> StarRating.STAR_NONE
    }
  }
}

fun Rating.toStarRating(): StarRating {
  return when (this) {
    Rating.RATING_0 -> StarRating.STAR_0
    Rating.RATING_0_5 -> StarRating.STAR_0_5
    Rating.RATING_1 -> StarRating.STAR_1
    Rating.RATING_1_5 -> StarRating.STAR_1_5
    Rating.RATING_2 -> StarRating.STAR_2
    Rating.RATING_2_5 -> StarRating.STAR_2_5
    Rating.RATING_3 -> StarRating.STAR_3
    Rating.RATING_3_5 -> StarRating.STAR_3_5
    Rating.RATING_4 -> StarRating.STAR_4
    Rating.RATING_4_5 -> StarRating.STAR_4_5
    Rating.RATING_5 -> StarRating.STAR_5
    else -> StarRating.STAR_NONE
  }
}

// Note: currently have to use value to get compiler optimization of resolving to primitives
fun StarRating.toRating(): Rating {
  return when (value) {
    StarRating.STAR_0.value -> Rating.RATING_0
    StarRating.STAR_0_5.value -> Rating.RATING_0_5
    StarRating.STAR_1.value -> Rating.RATING_1
    StarRating.STAR_1_5.value -> Rating.RATING_1_5
    StarRating.STAR_2.value -> Rating.RATING_2
    StarRating.STAR_2_5.value -> Rating.RATING_2_5
    StarRating.STAR_3.value -> Rating.RATING_3
    StarRating.STAR_3_5.value -> Rating.RATING_3_5
    StarRating.STAR_4.value -> Rating.RATING_4
    StarRating.STAR_4_5.value -> Rating.RATING_4_5
    StarRating.STAR_5.value -> Rating.RATING_5
    else -> Rating.RATING_NONE
  }
}

/**
 * Taken from a Media Monkey forum a long time ago in a galaxy far, far away...
 *
 * From Media Monkey: somewhat interoperable with other players (Windows, WinAmp...). Other
 * players will convert half stars to whole stars as they don't support the concept of 1/2 of a star
 *
 * rating stars  mp3  (reads)
 *   -1   -1.0   none  none
 *    0    0.0   0    (0, 2-8) -bomb
 *   10    0.5   13   (9-18, 30-39)
 *   20    1.0   1    (1, 19-28, 40-49)
 *   30    1.5   54   (29, 50-59, 70-90)
 *   40    2.0   64   (60-69, 91-113)
 *   50    2.5   118  (114-123, 134-141)
 *   60    3.0   128  (124-133, 142-167)
 *   70    3.5   186  (168-191)
 *   80    4.0   196  (192-218)
 *   90    4.5   242  (219-247)
 *  100    5.0   255  (248-255)
 *
 * Old school uses '*' and '-' chars to denote star and half star respectively
 * "" = 0
 * "*" = 1
 * "**-" = 2.5
 * "*****" = 5
 * etc...
 */
inline class Mp3Rating(val value: Int) : Comparable<Mp3Rating> {
  override fun compareTo(other: Mp3Rating): Int = value.compareTo(other.value)

  val isValid: Boolean
    get() = value != MP3_NONE.value

  companion object {
    val MP3_NONE = Mp3Rating(-1)
    val MP3_0 = Mp3Rating(0)
    val MP3_0_5 = Mp3Rating(13)
    val MP3_1 = Mp3Rating(1)
    val MP3_1_5 = Mp3Rating(54)
    val MP3_2 = Mp3Rating(64)
    val MP3_2_5 = Mp3Rating(118)
    val MP3_3 = Mp3Rating(128)
    val MP3_3_5 = Mp3Rating(186)
    val MP3_4 = Mp3Rating(196)
    val MP3_4_5 = Mp3Rating(242)
    val MP3_5 = Mp3Rating(255)

    val VALID_RANGE = MP3_0..MP3_5
  }
}

fun Mp3Rating.coerceToValid() = coerceIn(Mp3Rating.VALID_RANGE)

fun Int.toMp3Rating(): Mp3Rating {
  return when (this) {
    Mp3Rating.MP3_0.value -> Mp3Rating.MP3_0
    Mp3Rating.MP3_1.value -> Mp3Rating.MP3_1
    Mp3Rating.MP3_0_5.value -> Mp3Rating.MP3_0_5
    Mp3Rating.MP3_2.value -> Mp3Rating.MP3_2
    Mp3Rating.MP3_2_5.value -> Mp3Rating.MP3_2_5
    Mp3Rating.MP3_3.value -> Mp3Rating.MP3_3
    Mp3Rating.MP3_3_5.value -> Mp3Rating.MP3_3_5
    Mp3Rating.MP3_4.value -> Mp3Rating.MP3_4
    Mp3Rating.MP3_4_5.value -> Mp3Rating.MP3_4_5
    Mp3Rating.MP3_5.value -> Mp3Rating.MP3_5
    else -> when (this) { // fallback from exact values
      in 124..133, in 142..167 -> Mp3Rating.MP3_3
      in 192..218 -> Mp3Rating.MP3_4
      in 248..255 -> Mp3Rating.MP3_5
      in 60..69, in 91..113 -> Mp3Rating.MP3_2
      in 19..28, in 40..49 -> Mp3Rating.MP3_1
      in 2..8, 0 -> Mp3Rating.MP3_0
      in 168..191 -> Mp3Rating.MP3_3_5
      in 219..247 -> Mp3Rating.MP3_4_5
      in 114..123, in 134..141 -> Mp3Rating.MP3_2_5
      in 50..59, in 70..90, 29 -> Mp3Rating.MP3_1_5
      in 9..18, in 30..39 -> Mp3Rating.MP3_0_5
      in 256..Int.MAX_VALUE -> Mp3Rating.MP3_5
      else -> Mp3Rating.MP3_NONE
    }
  }
}

/** Mp3 rating stored as a string in a file tag could be a number or old style "*" */
fun String.toMp3Rating(): Mp3Rating {
  return toIntOrNull()?.toMp3Rating() ?: when (this) {
    "" -> Mp3Rating.MP3_0
    "-" -> Mp3Rating.MP3_0_5
    "*" -> Mp3Rating.MP3_1
    "*-" -> Mp3Rating.MP3_1_5
    "**" -> Mp3Rating.MP3_2
    "**-" -> Mp3Rating.MP3_2_5
    "***" -> Mp3Rating.MP3_3
    "***-" -> Mp3Rating.MP3_3_5
    "****" -> Mp3Rating.MP3_4
    "****-" -> Mp3Rating.MP3_4_5
    "*****" -> Mp3Rating.MP3_5
    else -> Mp3Rating.MP3_NONE
  }
}

fun Mp3Rating.toStarRating(): StarRating {
  return when (value) {
    Mp3Rating.MP3_0.value -> StarRating.STAR_0
    Mp3Rating.MP3_0_5.value -> StarRating.STAR_0_5
    Mp3Rating.MP3_1.value -> StarRating.STAR_1
    Mp3Rating.MP3_1_5.value -> StarRating.STAR_1_5
    Mp3Rating.MP3_2.value -> StarRating.STAR_2
    Mp3Rating.MP3_2_5.value -> StarRating.STAR_2_5
    Mp3Rating.MP3_3.value -> StarRating.STAR_3
    Mp3Rating.MP3_3_5.value -> StarRating.STAR_3_5
    Mp3Rating.MP3_4.value -> StarRating.STAR_4
    Mp3Rating.MP3_4_5.value -> StarRating.STAR_4_5
    Mp3Rating.MP3_5.value -> StarRating.STAR_5
    else -> StarRating.STAR_NONE
  }
}

fun StarRating.toMp3Rating(): Mp3Rating {
  return when (value) {
    StarRating.STAR_0.value -> Mp3Rating.MP3_0
    StarRating.STAR_0_5.value -> Mp3Rating.MP3_0_5
    StarRating.STAR_1.value -> Mp3Rating.MP3_1
    StarRating.STAR_1_5.value -> Mp3Rating.MP3_1_5
    StarRating.STAR_2.value -> Mp3Rating.MP3_2
    StarRating.STAR_2_5.value -> Mp3Rating.MP3_2_5
    StarRating.STAR_3.value -> Mp3Rating.MP3_3
    StarRating.STAR_3_5.value -> Mp3Rating.MP3_3_5
    StarRating.STAR_4.value -> Mp3Rating.MP3_4
    StarRating.STAR_4_5.value -> Mp3Rating.MP3_4_5
    StarRating.STAR_5.value -> Mp3Rating.MP3_5
    else -> Mp3Rating.MP3_NONE
  }
}
