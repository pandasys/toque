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

package com.ealva.toque.android.service.vlc

import android.content.Context
import android.net.Uri
import android.os.Environment.DIRECTORY_MUSIC
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ealva.toque.common.toMillis
import com.ealva.toque.db.AlbumId
import com.ealva.toque.db.MediaId
import com.ealva.toque.service.vlc.LibVlc
import com.ealva.toque.service.vlc.LibVlcPreferencesSingleton
import com.ealva.toque.service.vlc.LibVlcSingleton
import com.ealva.toque.service.vlc.NullEqPresetSelector
import com.ealva.toque.service.vlc.NullVlcPlayerFactory
import com.ealva.toque.service.vlc.VlcMedia
import com.ealva.toque.test.shared.CoroutineRule
import com.nhaarman.expect.expect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import org.koin.test.KoinTest
import java.io.File
import java.io.InputStream

fun InputStream.toFile(path: String) {
  File(path).outputStream().use { output -> copyTo(output) }
}

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class VlcMediaTest : KoinTest {
  @get:Rule
  var coroutineRule = CoroutineRule()

  @Suppress("DEPRECATION")
  @get:Rule
  var thrown: ExpectedException = ExpectedException.none()

  private lateinit var appCtx: Context

  private val fileName = "REMAIN.mp3"

  @Before
  fun setup() {
    println("setup")
    appCtx = ApplicationProvider.getApplicationContext()
    val dir = appCtx.getExternalFilesDir(DIRECTORY_MUSIC)
    val file = File(dir, fileName)
    javaClass.getResourceAsStream("/Music/3 Miles Out/REMAIN.mp3").use { input ->
      checkNotNull(input).toFile(file.absolutePath)
    }
  }

  @After
  fun tearDown() {
    println("tearDown")
    val dir = appCtx.getExternalFilesDir(DIRECTORY_MUSIC)
    val file = File(dir, fileName)
    file.delete()
  }

  @Test
  fun testMakeAudioMedia() = coroutineRule.runBlockingTest {
    val (prefsSingleton, libVlcSingleton) = getSingletons()
    val libVlc: LibVlc = libVlcSingleton.instance()
    val dir = appCtx.getExternalFilesDir(DIRECTORY_MUSIC)
    val file = File(dir, fileName)
    expect(file.exists()).toBe(true)
    val uri = Uri.fromFile(file)
    val media = libVlc.makeAudioMedia(uri, 0.toMillis(), true, prefsSingleton.instance())
    expect(media.type).toBe(1)
    val vlcMedia = VlcMedia(
      media,
      uri,
      MediaId.INVALID,
      AlbumId.INVALID,
      NullEqPresetSelector,
      NullVlcPlayerFactory,
      coroutineRule.testDispatcher
    )
    expect(vlcMedia.uri).toBe(uri)
  }

  private fun getSingletons(): Pair<LibVlcPreferencesSingleton, LibVlcSingleton> {
    val prefsSingleton = LibVlcPreferencesSingleton(appCtx, coroutineRule.testDispatcher)
    return Pair(
      prefsSingleton,
      LibVlcSingleton(appCtx, prefsSingleton, dispatcher = coroutineRule.testDispatcher)
    )
  }
}
