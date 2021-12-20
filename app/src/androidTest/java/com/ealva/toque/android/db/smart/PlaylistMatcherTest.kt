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

package com.ealva.toque.android.db.smart

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ealva.toque.db.MediaTable
import com.ealva.toque.db.PlayListType
import com.ealva.toque.db.smart.MatcherData
import com.ealva.toque.db.smart.PlaylistMatcher
import com.nhaarman.expect.expect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class PlaylistMatcherTest {
  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun testSmartPlaylistIs() {
    val matcher = PlaylistMatcher.Is
    val clause = matcher.makeWhereClause(
      MediaTable.id,
      MatcherData("TheView", PlayListType.Rules.id.toLong(), 100)
    )
    expect(clause.toString()).toBe(""""TheView"."MediaId" IS NOT NULL""")

    val joinTemplate =
      matcher.makeJoinTemplate(MatcherData("TheView", PlayListType.Rules.id.toLong(), 100))

    expect(joinTemplate?.joinTo(MediaTable)?.toString())
      .toBe(""""Media" LEFT JOIN "TheView" ON "Media"."MediaId" = "TheView"."MediaId"""")
  }

  @Test
  fun testSmartPlaylistIsNot() {
    val matcher = PlaylistMatcher.IsNot
    val clause = matcher.makeWhereClause(
      MediaTable.id,
      MatcherData("TheView", PlayListType.Rules.id.toLong(), 100)
    )
    expect(clause.toString()).toBe(""""TheView"."MediaId" IS NULL""")

    val joinTemplate =
      matcher.makeJoinTemplate(MatcherData("TheView", PlayListType.Rules.id.toLong(), 100))
    expect(joinTemplate?.joinTo(MediaTable)?.toString())
      .toBe(""""Media" LEFT JOIN "TheView" ON "Media"."MediaId" = "TheView"."MediaId"""")
  }

  @Test
  fun testPlaylistIs() {
    val matcher = PlaylistMatcher.Is
    val clause = matcher.makeWhereClause(
      MediaTable.id,
      MatcherData("", PlayListType.UserCreated.id.toLong(), 100)
    )
    expect(clause.toString())
      .toBe(""""Media"."MediaId" IN (SELECT "PlayListMedia"."PlayListMedia_MediaId"""" +
        """ FROM "PlayListMedia" WHERE "PlayListMedia"."PlayListMedia_PlayListId" = 100)""")
  }

  @Test
  fun testPlaylistIsNot() {
    val matcher = PlaylistMatcher.IsNot
    val clause = matcher.makeWhereClause(
      MediaTable.id,
      MatcherData("", PlayListType.UserCreated.id.toLong(), 100)
    )
    expect(clause.toString())
      .toBe(""""Media"."MediaId" NOT IN (SELECT "PlayListMedia"."PlayListMedia_MediaId"""" +
        """ FROM "PlayListMedia" WHERE "PlayListMedia"."PlayListMedia_PlayListId" = 100)""")
  }
}
