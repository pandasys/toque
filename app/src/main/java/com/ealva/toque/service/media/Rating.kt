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

val NO_RATING = Rating(-1)
val RATING_0 = Rating(0)
val RATING_HALF = Rating(10)
val RATING_1 = Rating(20)
val RATING_1_5 = Rating(30)
val RATING_2 = Rating(40)
val RATING_2_5 = Rating(50)
val RATING_3 = Rating(60)
val RATING_3_5 = Rating(70)
val RATING_4 = Rating(80)
val RATING_4_5 = Rating(90)
val RATING_5 = Rating(100)

fun Int.toRating(): Rating {
  return when (this) {
    RATING_0.value -> RATING_0
    RATING_HALF.value -> RATING_HALF
    RATING_1.value -> RATING_1
    RATING_1_5.value -> RATING_1_5
    RATING_2.value -> RATING_2
    RATING_2_5.value -> RATING_2_5
    RATING_3.value -> RATING_3
    RATING_3_5.value -> RATING_3_5
    RATING_4.value -> RATING_4
    RATING_4_5.value -> RATING_4_5
    RATING_5.value -> RATING_5
    else -> when (this) { // fallback from exact values
      in 0..4 -> RATING_0
      in 5..14 -> RATING_HALF
      in 15..24 -> RATING_1
      in 25..34 -> RATING_1_5
      in 35..44 -> RATING_2
      in 45..54 -> RATING_2_5
      in 55..64 -> RATING_3
      in 65..74 -> RATING_3_5
      in 75..84 -> RATING_4
      in 85..94 -> RATING_4_5
      in 95..Int.MAX_VALUE -> RATING_5
      else -> NO_RATING
    }
  }
}

inline class Rating(val value: Int) {

  val isValid: Boolean
    get() = value != NO_RATING.value

  fun toStarRating(): StarRating {
    return when (this) {
      RATING_0 -> STAR_RATING_0
      RATING_HALF -> STAR_RATING_HALF
      RATING_1 -> STAR_RATING_1
      RATING_1_5 -> STAR_RATING_1_5
      RATING_2 -> STAR_RATING_2
      RATING_2_5 -> STAR_RATING_2_5
      RATING_3 -> STAR_RATING_3
      RATING_3_5 -> STAR_RATING_3_5
      RATING_4 -> STAR_RATING_4
      RATING_4_5 -> STAR_RATING_4_5
      RATING_5 -> STAR_RATING_5
      else -> STAR_NO_RATING
    }
  }

  fun hash(): Int {
    return value
  }
}

// Note: currently have to use value to get compiler optimization of resolving to primitives
fun StarRating.toRating(): Rating {
  return when (value) {
    STAR_RATING_0.value -> RATING_0
    STAR_RATING_HALF.value -> RATING_HALF
    STAR_RATING_1.value -> RATING_1
    STAR_RATING_1_5.value -> RATING_1_5
    STAR_RATING_2.value -> RATING_2
    STAR_RATING_2_5.value -> RATING_2_5
    STAR_RATING_3.value -> RATING_3
    STAR_RATING_3_5.value -> RATING_3_5
    STAR_RATING_4.value -> RATING_4
    STAR_RATING_4_5.value -> RATING_4_5
    STAR_RATING_5.value -> RATING_5
    else -> NO_RATING
  }
}

val STAR_NO_RATING = StarRating(-1.0F)
val STAR_RATING_0 = StarRating(0.0F)
val STAR_RATING_HALF = StarRating(0.5F)
val STAR_RATING_1 = StarRating(1.0F)
val STAR_RATING_1_5 = StarRating(1.5F)
val STAR_RATING_2 = StarRating(2.0F)
val STAR_RATING_2_5 = StarRating(2.5F)
val STAR_RATING_3 = StarRating(3.0F)
val STAR_RATING_3_5 = StarRating(3.5F)
val STAR_RATING_4 = StarRating(4.0F)
val STAR_RATING_4_5 = StarRating(4.5F)
val STAR_RATING_5 = StarRating(5.0F)

fun Float.toStarRating(): StarRating {
  return when (this) {
    STAR_RATING_0.value -> STAR_RATING_0
    STAR_RATING_HALF.value -> STAR_RATING_HALF
    STAR_RATING_1.value -> STAR_RATING_1
    STAR_RATING_1_5.value -> STAR_RATING_1_5
    STAR_RATING_2.value -> STAR_RATING_2
    STAR_RATING_2_5.value -> STAR_RATING_2_5
    STAR_RATING_3.value -> STAR_RATING_3
    STAR_RATING_3_5.value -> STAR_RATING_3_5
    STAR_RATING_4.value -> STAR_RATING_4
    STAR_RATING_4_5.value -> STAR_RATING_4_5
    STAR_RATING_5.value -> STAR_RATING_5
    else -> STAR_NO_RATING
  }
}

inline class StarRating(val value: Float) {
  val isValid: Boolean
    get() = value != STAR_NO_RATING.value
}

val MP3_NO_RATING = Mp3Rating(-1)
val MP3_RATING_0 = Mp3Rating(0)
val MP3_RATING_HALF = Mp3Rating(13)
val MP3_RATING_1 = Mp3Rating(1)
val MP3_RATING_1_5 = Mp3Rating(54)
val MP3_RATING_2 = Mp3Rating(64)
val MP3_RATING_2_5 = Mp3Rating(118)
val MP3_RATING_3 = Mp3Rating(128)
val MP3_RATING_3_5 = Mp3Rating(186)
val MP3_RATING_4 = Mp3Rating(196)
val MP3_RATING_4_5 = Mp3Rating(242)
val MP3_RATING_5 = Mp3Rating(255)

fun Int.toMp3Rating(): Mp3Rating {
  return when (this) {
    MP3_RATING_0.value -> MP3_RATING_0
    MP3_RATING_1.value -> MP3_RATING_1
    MP3_RATING_HALF.value -> MP3_RATING_HALF
    MP3_RATING_2.value -> MP3_RATING_2
    MP3_RATING_2_5.value -> MP3_RATING_2_5
    MP3_RATING_3.value -> MP3_RATING_3
    MP3_RATING_3_5.value -> MP3_RATING_3_5
    MP3_RATING_4.value -> MP3_RATING_4
    MP3_RATING_4_5.value -> MP3_RATING_4_5
    MP3_RATING_5.value -> MP3_RATING_5
    else -> when (this) {
      in 124..133, in 142..167 -> MP3_RATING_3
      in 192..218 -> MP3_RATING_4
      in 248..255 -> MP3_RATING_5
      in 60..69, in 91..113 -> MP3_RATING_2
      in 19..28, in 40..49 -> MP3_RATING_1
      in 2..8 -> MP3_RATING_0
      in 168..191 -> MP3_RATING_3_5
      in 219..247 -> MP3_RATING_4_5
      in 114..123, in 134..141 -> MP3_RATING_2_5
      in 50..59, in 70..90, 29 -> MP3_RATING_1_5
      in 9..18, in 30..39 -> MP3_RATING_HALF
      in 256..Int.MAX_VALUE -> MP3_RATING_5
      else -> MP3_NO_RATING
    }
  }
}

fun String.toMp3Rating(): Mp3Rating {
  return when (this) {
    "" -> MP3_RATING_0
    "-" -> MP3_RATING_HALF
    "*" -> MP3_RATING_1
    "*-" -> MP3_RATING_1_5
    "**" -> MP3_RATING_2
    "**-" -> MP3_RATING_2_5
    "***" -> MP3_RATING_3
    "***-" -> MP3_RATING_3_5
    "****" -> MP3_RATING_4
    "****-" -> MP3_RATING_4_5
    "*****" -> MP3_RATING_5
    else -> MP3_NO_RATING
  }
}

/**
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
inline class Mp3Rating(val value: Int) {

  val isValid: Boolean
    get() = value != MP3_NO_RATING.value

  fun toStarRating(): StarRating {
    return when (value) {
      MP3_RATING_0.value -> STAR_RATING_0
      MP3_RATING_HALF.value -> STAR_RATING_HALF
      MP3_RATING_1.value -> STAR_RATING_1
      MP3_RATING_1_5.value -> STAR_RATING_1_5
      MP3_RATING_2.value -> STAR_RATING_2
      MP3_RATING_2_5.value -> STAR_RATING_2_5
      MP3_RATING_3.value -> STAR_RATING_3
      MP3_RATING_3_5.value -> STAR_RATING_3_5
      MP3_RATING_4.value -> STAR_RATING_4
      MP3_RATING_4_5.value -> STAR_RATING_4_5
      MP3_RATING_5.value -> STAR_RATING_5
      else -> STAR_NO_RATING
    }
  }

  fun hash(): Int {
    return value
  }
}

fun StarRating.toMp3Rating(): Mp3Rating {
  return when (value) {
    STAR_RATING_0.value -> MP3_RATING_0
    STAR_RATING_HALF.value -> MP3_RATING_HALF
    STAR_RATING_1.value -> MP3_RATING_1
    STAR_RATING_1_5.value -> MP3_RATING_1_5
    STAR_RATING_2.value -> MP3_RATING_2
    STAR_RATING_2_5.value -> MP3_RATING_2_5
    STAR_RATING_3.value -> MP3_RATING_3
    STAR_RATING_3_5.value -> MP3_RATING_3_5
    STAR_RATING_4.value -> MP3_RATING_4
    STAR_RATING_4_5.value -> MP3_RATING_4_5
    STAR_RATING_5.value -> MP3_RATING_5
    else -> MP3_NO_RATING
  }
}
