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

package com.ealva.toque.service.vlc

import android.content.Context
import android.os.Environment.DIRECTORY_MUSIC
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.sharedtest.CoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.io.InputStream

fun InputStream.toFile(path: String) {
  File(path).outputStream().use { output -> copyTo(output) }
}

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class VlcMediaTest {
  @get:Rule
  var coroutineRule = CoroutineRule()

  @get:Rule
  val tempFolder: TemporaryFolder = TemporaryFolder()

  private lateinit var appCtx: Context
  private lateinit var appPrefsFile: File
  private lateinit var vlcPrefsFile: File
  private lateinit var dataStoreScope: TestScope
  private lateinit var appPrefsSingleton: AppPrefsSingleton
  private lateinit var vlcPrefsSingleton: LibVlcPrefsSingleton
  private lateinit var vlcSingleton: LibVlcSingleton
  private val fileName = "REMAIN.mp3"

  @Before
  fun setup() {
    println("setup")
    appCtx = ApplicationProvider.getApplicationContext()
    dataStoreScope = TestScope(coroutineRule.testDispatcher + Job())
    appPrefsFile = tempFolder.newFile("app_prefs.preferences_pb")
    vlcPrefsFile = tempFolder.newFile("vlc_prefs.preferences_pb")

    appPrefsSingleton = AppPrefsSingleton(AppPrefs.Companion::make, appPrefsFile, dataStoreScope)
    vlcPrefsSingleton = LibVlcPrefsSingleton(
      LibVlcPrefs.Companion::make,
      vlcPrefsFile,
      dataStoreScope
    )
    vlcSingleton = LibVlcSingleton(
      appCtx,
      vlcPrefsSingleton,
      dispatcher = coroutineRule.testDispatcher
    )
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
  fun testMakeAudioMedia() = //    val libVlc: LibVlc = vlcSingleton.instance()
//    val dir = appCtx.getExternalFilesDir(DIRECTORY_MUSIC)
//    val file = File(dir, fileName)
//    expect(file.exists()).toBe(true)
//    val uri = Uri.fromFile(file)
//    val media = libVlc.makeAudioMedia(uri, Millis.ZERO, true, vlcPrefsSingleton.instance())
//    expect(media.type).toBe(1)
//    val vlcMedia = VlcMedia(
//      media,
//      uri,
//      MediaId.INVALID,
//      AlbumId.INVALID,
//      NullEqPresetSelector,
//      NullAvPlayerFactory,
//      appPrefsSingleton.instance(),
//      coroutineRule.testDispatcher
//    )
//    expect(vlcMedia.uri).toBe(uri)
    runTest {
      //    val libVlc: LibVlc = vlcSingleton.instance()
//    val dir = appCtx.getExternalFilesDir(DIRECTORY_MUSIC)
//    val file = File(dir, fileName)
//    expect(file.exists()).toBe(true)
//    val uri = Uri.fromFile(file)
//    val media = libVlc.makeAudioMedia(uri, Millis.ZERO, true, vlcPrefsSingleton.instance())
//    expect(media.type).toBe(1)
//    val vlcMedia = VlcMedia(
//      media,
//      uri,
//      MediaId.INVALID,
//      AlbumId.INVALID,
//      NullEqPresetSelector,
//      NullAvPlayerFactory,
//      appPrefsSingleton.instance(),
//      coroutineRule.testDispatcher
//    )
//    expect(vlcMedia.uri).toBe(uri)
      //    val libVlc: LibVlc = vlcSingleton.instance()
//    val dir = appCtx.getExternalFilesDir(DIRECTORY_MUSIC)
//    val file = File(dir, fileName)
//    expect(file.exists()).toBe(true)
//    val uri = Uri.fromFile(file)
//    val media = libVlc.makeAudioMedia(uri, Millis.ZERO, true, vlcPrefsSingleton.instance())
//    expect(media.type).toBe(1)
//    val vlcMedia = VlcMedia(
//      media,
//      uri,
//      MediaId.INVALID,
//      AlbumId.INVALID,
//      NullEqPresetSelector,
//      NullAvPlayerFactory,
//      appPrefsSingleton.instance(),
//      coroutineRule.testDispatcher
//    )
//    expect(vlcMedia.uri).toBe(uri)
    }
}
