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

package com.ealva.toque.common

import com.nhaarman.expect.expect
import org.junit.Test

class RatingTest {
  @Test
  fun `test int to rating`() {
    expect((-1).toRating()).toBe(Rating.RATING_NONE)
    (0..4).forEach { expect(it.toRating()).toBe(Rating.RATING_0) }
    (5..14).forEach { expect(it.toRating()).toBe(Rating.RATING_0_5) }
    (15..24).forEach { expect(it.toRating()).toBe(Rating.RATING_1) }
    (25..34).forEach { expect(it.toRating()).toBe(Rating.RATING_1_5) }
    (35..44).forEach { expect(it.toRating()).toBe(Rating.RATING_2) }
    (45..54).forEach { expect(it.toRating()).toBe(Rating.RATING_2_5) }
    (55..64).forEach { expect(it.toRating()).toBe(Rating.RATING_3) }
    (65..74).forEach { expect(it.toRating()).toBe(Rating.RATING_3_5) }
    (75..84).forEach { expect(it.toRating()).toBe(Rating.RATING_4) }
    (85..94).forEach { expect(it.toRating()).toBe(Rating.RATING_4_5) }
    (95..101).forEach { expect(it.toRating()).toBe(Rating.RATING_5) }
  }

  @Test
  fun `test rating coerce to valid`() {
    expect(Int.MIN_VALUE.toRating().coerceToValid()).toBe(Rating.RATING_0)
    expect((-1).toRating().coerceToValid()).toBe(Rating.RATING_0)
    expect((101).toRating().coerceToValid()).toBe(Rating.RATING_5)
    expect(Int.MAX_VALUE.toRating().coerceToValid()).toBe(Rating.RATING_5)
  }

  @Test
  fun `test string to rating`() {
    expect((-1).toString().toRating()).toBe(Rating.RATING_NONE)
    expect("blah".toRating()).toBe(Rating.RATING_NONE)
    (0..4).forEach { expect(it.toString().toRating()).toBe(Rating.RATING_0) }
    (5..14).forEach { expect(it.toString().toRating()).toBe(Rating.RATING_0_5) }
    (15..24).forEach { expect(it.toString().toRating()).toBe(Rating.RATING_1) }
    (25..34).forEach { expect(it.toString().toRating()).toBe(Rating.RATING_1_5) }
    (35..44).forEach { expect(it.toString().toRating()).toBe(Rating.RATING_2) }
    (45..54).forEach { expect(it.toString().toRating()).toBe(Rating.RATING_2_5) }
    (55..64).forEach { expect(it.toString().toRating()).toBe(Rating.RATING_3) }
    (65..74).forEach { expect(it.toString().toRating()).toBe(Rating.RATING_3_5) }
    (75..84).forEach { expect(it.toString().toRating()).toBe(Rating.RATING_4) }
    (85..94).forEach { expect(it.toString().toRating()).toBe(Rating.RATING_4_5) }
    (95..101).forEach { expect(it.toString().toRating()).toBe(Rating.RATING_5) }
  }

  @Test
  fun `test float to star rating`() {
    expect((-1F).toStarRating()).toBe(StarRating.STAR_NONE)
    (0..24).forEach { expect((it.toFloat() / 100).toStarRating()).toBe(StarRating.STAR_0) }
    (25..74).forEach { expect((it.toFloat() / 100).toStarRating()).toBe(StarRating.STAR_0_5) }
    (75..124).forEach { expect((it.toFloat() / 100).toStarRating()).toBe(StarRating.STAR_1) }
    (125..174).forEach { expect((it.toFloat() / 100).toStarRating()).toBe(StarRating.STAR_1_5) }
    (175..224).forEach { expect((it.toFloat() / 100).toStarRating()).toBe(StarRating.STAR_2) }
    (225..274).forEach { expect((it.toFloat() / 100).toStarRating()).toBe(StarRating.STAR_2_5) }
    (275..324).forEach { expect((it.toFloat() / 100).toStarRating()).toBe(StarRating.STAR_3) }
    (325..374).forEach { expect((it.toFloat() / 100).toStarRating()).toBe(StarRating.STAR_3_5) }
    (375..424).forEach { expect((it.toFloat() / 100).toStarRating()).toBe(StarRating.STAR_4) }
    (425..474).forEach { expect((it.toFloat() / 100).toStarRating()).toBe(StarRating.STAR_4_5) }
    (475..501).forEach { expect((it.toFloat() / 100).toStarRating()).toBe(StarRating.STAR_5) }
  }

  @Test
  fun `test star rating coerce to valid`() {
    expect(Float.MIN_VALUE.toStarRating().coerceToValid()).toBe(StarRating.STAR_0)
    expect((-1F).toStarRating().coerceToValid()).toBe(StarRating.STAR_0)
    expect(5.1F.toStarRating().coerceToValid()).toBe(StarRating.STAR_5)
    expect(Float.MAX_VALUE.toStarRating().coerceToValid()).toBe(StarRating.STAR_5)
  }

  @Test
  fun `test rating to star rating`() {
    expect(Rating.RATING_0.toStarRating()).toBe(StarRating.STAR_0)
    expect(Rating.RATING_0_5.toStarRating()).toBe(StarRating.STAR_0_5)
    expect(Rating.RATING_1.toStarRating()).toBe(StarRating.STAR_1)
    expect(Rating.RATING_1_5.toStarRating()).toBe(StarRating.STAR_1_5)
    expect(Rating.RATING_2.toStarRating()).toBe(StarRating.STAR_2)
    expect(Rating.RATING_2_5.toStarRating()).toBe(StarRating.STAR_2_5)
    expect(Rating.RATING_3.toStarRating()).toBe(StarRating.STAR_3)
    expect(Rating.RATING_3_5.toStarRating()).toBe(StarRating.STAR_3_5)
    expect(Rating.RATING_4.toStarRating()).toBe(StarRating.STAR_4)
    expect(Rating.RATING_4_5.toStarRating()).toBe(StarRating.STAR_4_5)
    expect(Rating.RATING_5.toStarRating()).toBe(StarRating.STAR_5)
  }

  @Test
  fun `test star rating to rating`() {
    expect(StarRating.STAR_0.toRating()).toBe(Rating.RATING_0)
    expect(StarRating.STAR_0_5.toRating()).toBe(Rating.RATING_0_5)
    expect(StarRating.STAR_1.toRating()).toBe(Rating.RATING_1)
    expect(StarRating.STAR_1_5.toRating()).toBe(Rating.RATING_1_5)
    expect(StarRating.STAR_2.toRating()).toBe(Rating.RATING_2)
    expect(StarRating.STAR_2_5.toRating()).toBe(Rating.RATING_2_5)
    expect(StarRating.STAR_3.toRating()).toBe(Rating.RATING_3)
    expect(StarRating.STAR_3_5.toRating()).toBe(Rating.RATING_3_5)
    expect(StarRating.STAR_4.toRating()).toBe(Rating.RATING_4)
    expect(StarRating.STAR_4_5.toRating()).toBe(Rating.RATING_4_5)
    expect(StarRating.STAR_5.toRating()).toBe(Rating.RATING_5)
  }

  /**
   * This test also encompasses Int.toMp3Rating() as the string is first tested as if it were
   * a valid Int, then as a star string "*-"
   */
  @Test
  fun `test string to mp3 rating`() {
    expect((-1).toString().toMp3Rating()).toBe(Mp3Rating.MP3_NONE)
    expect(Int.MIN_VALUE.toString().toMp3Rating()).toBe(Mp3Rating.MP3_NONE)
    expect("blah".toMp3Rating()).toBe(Mp3Rating.MP3_NONE)
    (124..133).forEach { expect(it.toString().toMp3Rating()).toBe(Mp3Rating.MP3_3) }
    (142..167).forEach { expect(it.toString().toMp3Rating()).toBe(Mp3Rating.MP3_3) }
    (192..218).forEach { expect(it.toString().toMp3Rating()).toBe(Mp3Rating.MP3_4) }
    (248..256).forEach { expect(it.toString().toMp3Rating()).toBe(Mp3Rating.MP3_5) }
    (60..69).forEach { expect(it.toString().toMp3Rating()).toBe(Mp3Rating.MP3_2) }
    (91..113).forEach { expect(it.toString().toMp3Rating()).toBe(Mp3Rating.MP3_2) }
    (19..28).forEach { expect(it.toString().toMp3Rating()).toBe(Mp3Rating.MP3_1) }
    (40..49).forEach { expect(it.toString().toMp3Rating()).toBe(Mp3Rating.MP3_1) }
    (2..8).forEach { expect(it.toString().toMp3Rating()).toBe(Mp3Rating.MP3_0) }
    expect("0".toMp3Rating()).toBe(Mp3Rating.MP3_0)
    (168..191).forEach { expect(it.toString().toMp3Rating()).toBe(Mp3Rating.MP3_3_5) }
    (219..247).forEach { expect(it.toString().toMp3Rating()).toBe(Mp3Rating.MP3_4_5) }
    (114..123).forEach { expect(it.toString().toMp3Rating()).toBe(Mp3Rating.MP3_2_5) }
    (134..141).forEach { expect(it.toString().toMp3Rating()).toBe(Mp3Rating.MP3_2_5) }
    (50..59).forEach { expect(it.toString().toMp3Rating()).toBe(Mp3Rating.MP3_1_5) }
    (70..90).forEach { expect(it.toString().toMp3Rating()).toBe(Mp3Rating.MP3_1_5) }
    expect("29".toMp3Rating()).toBe(Mp3Rating.MP3_1_5)
    (9..18).forEach { expect(it.toString().toMp3Rating()).toBe(Mp3Rating.MP3_0_5) }
    (30..39).forEach { expect(it.toString().toMp3Rating()).toBe(Mp3Rating.MP3_0_5) }

    expect("".toMp3Rating()).toBe(Mp3Rating.MP3_0)
    expect("-".toMp3Rating()).toBe(Mp3Rating.MP3_0_5)
    expect("*".toMp3Rating()).toBe(Mp3Rating.MP3_1)
    expect("*-".toMp3Rating()).toBe(Mp3Rating.MP3_1_5)
    expect("**".toMp3Rating()).toBe(Mp3Rating.MP3_2)
    expect("**-".toMp3Rating()).toBe(Mp3Rating.MP3_2_5)
    expect("***".toMp3Rating()).toBe(Mp3Rating.MP3_3)
    expect("***-".toMp3Rating()).toBe(Mp3Rating.MP3_3_5)
    expect("****".toMp3Rating()).toBe(Mp3Rating.MP3_4)
    expect("****-".toMp3Rating()).toBe(Mp3Rating.MP3_4_5)
    expect("*****".toMp3Rating()).toBe(Mp3Rating.MP3_5)
  }

  @Test
  fun `test mp3 rating coerce to valid`() {
    expect(Int.MIN_VALUE.toMp3Rating().coerceToValid()).toBe(Mp3Rating.MP3_0)
    expect((-1).toMp3Rating().coerceToValid()).toBe(Mp3Rating.MP3_0)
    expect(256.toMp3Rating().coerceToValid()).toBe(Mp3Rating.MP3_5)
    expect(Int.MAX_VALUE.toMp3Rating().coerceToValid()).toBe(Mp3Rating.MP3_5)
  }

  @Test
  fun `test mp3 rating to star rating`() {
    expect(Mp3Rating.MP3_0.toStarRating()).toBe(StarRating.STAR_0)
    expect(Mp3Rating.MP3_0_5.toStarRating()).toBe(StarRating.STAR_0_5)
    expect(Mp3Rating.MP3_1.toStarRating()).toBe(StarRating.STAR_1)
    expect(Mp3Rating.MP3_1_5.toStarRating()).toBe(StarRating.STAR_1_5)
    expect(Mp3Rating.MP3_2.toStarRating()).toBe(StarRating.STAR_2)
    expect(Mp3Rating.MP3_2_5.toStarRating()).toBe(StarRating.STAR_2_5)
    expect(Mp3Rating.MP3_3.toStarRating()).toBe(StarRating.STAR_3)
    expect(Mp3Rating.MP3_3_5.toStarRating()).toBe(StarRating.STAR_3_5)
    expect(Mp3Rating.MP3_4.toStarRating()).toBe(StarRating.STAR_4)
    expect(Mp3Rating.MP3_4_5.toStarRating()).toBe(StarRating.STAR_4_5)
    expect(Mp3Rating.MP3_5.toStarRating()).toBe(StarRating.STAR_5)
  }

  @Test
  fun `test star rating to mp3 rating`() {
    expect(StarRating.STAR_0.toMp3Rating()).toBe(Mp3Rating.MP3_0)
    expect(StarRating.STAR_0_5.toMp3Rating()).toBe(Mp3Rating.MP3_0_5)
    expect(StarRating.STAR_1.toMp3Rating()).toBe(Mp3Rating.MP3_1)
    expect(StarRating.STAR_1_5.toMp3Rating()).toBe(Mp3Rating.MP3_1_5)
    expect(StarRating.STAR_2.toMp3Rating()).toBe(Mp3Rating.MP3_2)
    expect(StarRating.STAR_2_5.toMp3Rating()).toBe(Mp3Rating.MP3_2_5)
    expect(StarRating.STAR_3.toMp3Rating()).toBe(Mp3Rating.MP3_3)
    expect(StarRating.STAR_3_5.toMp3Rating()).toBe(Mp3Rating.MP3_3_5)
    expect(StarRating.STAR_4.toMp3Rating()).toBe(Mp3Rating.MP3_4)
    expect(StarRating.STAR_4_5.toMp3Rating()).toBe(Mp3Rating.MP3_4_5)
    expect(StarRating.STAR_5.toMp3Rating()).toBe(Mp3Rating.MP3_5)
  }
}
