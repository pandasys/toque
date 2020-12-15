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

package com.ealva.toque.android.file

import android.content.Context
import android.webkit.MimeTypeMap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ealva.toque.media.FileExtensions
import com.ealva.toque.media.MediaFormat
import com.nhaarman.expect.expect
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import org.videolan.libvlc.util.Extensions

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class MediaFormatTest {
  @Suppress("DEPRECATION")
  @get:Rule
  var thrown: ExpectedException = ExpectedException.none()

  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun testNoDupIdsOrExtensions() {
    val idSet = IntAVLTreeSet()
    val extSet = ObjectAVLTreeSet<String>()

    MediaFormat.values().forEach { format ->
      expect(idSet.add(format.id)).toBe(true) { "Id ${format.id} for ${format.name} already used" }
      format.fileExtensions.forEach { ext ->
        expect(extSet.add(ext)).toBe(true) { "Extension $ext for ${format.name} already used" }
      }
    }
  }

  @Test
  fun testAudioFileExtensionsAgainstMimeTypeMap() {
    val allAudio = MediaFormat.values().filter { it.isAudio }
    val map = MimeTypeMap.getSingleton()

    allAudio.forEach { format ->
      format.fileExtensions.forEach { ext ->
        map.getMimeTypeFromExtension(ext)?.let { type ->
          expect(format.mimeTypes).toContain(type) {
            "${format.name} types=${format.mimeTypes} doesn't have $type"
          }
        }
      }
    }

    allAudio.forEach { format ->
      if (format !== MediaFormat.MpegAudio) { // disagree with MimeMap on this one
        format.preferredExtension?.let { ext ->
          map.getMimeTypeFromExtension(ext)?.let { type ->
            expect(format.preferredMimeType).toBe(type) {
              "${format.name} $type is not preferred in ${format.mimeTypes}"
            }
          }
        }
      }
    }
  }

  @Test
  fun testAudioMimeTypesAgainstMimeTypeMap() {
    val allAudio = MediaFormat.values().filter { it.isAudio }
    val map = MimeTypeMap.getSingleton()
    allAudio.forEach { format ->
      map.getExtensionFromMimeType(format.preferredMimeType)?.let { ext ->
        expect(format.preferredExtension).toBe(ext) {
          "${format.name} $ext is not preferred in ${format.fileExtensions}"
        }
      }
    }
  }

  @Test
  fun testHaveAudioMediaFormatForAllExtensions() {
    val allAudio = MediaFormat.values().filter { it.isAudio }
    FileExtensions.audio.forEach { ext ->
      expect(allAudio.find { it.fileExtensions.contains(ext) }).toNotBeNull {
        "No format for extension=$ext"
      }
    }
  }

  @Test
  fun testHaveVideoMediaFormatForAllExtensions() {
    val allVideo = MediaFormat.values().filter { it.isVideo }
    FileExtensions.video.forEach<String> { ext ->
      expect(allVideo.find { it.fileExtensions.contains(ext) }).toNotBeNull {
        "No format for extension=$ext"
      }
    }
  }

  @Test
  fun testVideoFileExtensionsAgainstMimeTypeMap() {
    val allVideo = MediaFormat.values().filter { it.isVideo }
    val map = MimeTypeMap.getSingleton()

    allVideo.forEach { format ->
      format.fileExtensions.forEach { ext ->
        map.getMimeTypeFromExtension(ext)?.let { type ->
          expect(format.mimeTypes).toContain(type) {
            "${format.name} types=${format.mimeTypes} doesn't have $type"
          }
        }
      }
    }

    allVideo.forEach { format ->
      format.preferredExtension?.let { ext ->
        map.getMimeTypeFromExtension(ext)?.let { type ->
          expect(format.preferredMimeType).toBe(type) {
            "${format.name} $type is not preferred in ${format.mimeTypes}"
          }
        }
      }
    }
  }

  @Test
  fun testExpectedExtensionCountsForEachType() {
    expect(FileExtensions.audio.size).toBe(Extensions.AUDIO.size)
    expect(FileExtensions.video.size).toBe(Extensions.VIDEO.size - 2)
    expect(FileExtensions.playlist.size).toBe(Extensions.PLAYLIST.size)
    expect(FileExtensions.subtitles.size).toBe(Extensions.SUBTITLES.size)
  }

  @Test
  fun testFormatAttributes() {
    MediaFormat.DolbyDigitalAc3.let { format ->
      expect(format.hasFileExtension("AC3")).toBe(true)
      expect(format.hasMimeType("audio/AAC")).toBe(true)
      expect(format.isAudio).toBe(true)
      expect(format.isVideo).toBe(false)
      expect(format.isPlaylist).toBe(false)
      expect(format.isMediaOrPlaylist).toBe(true)
    }
    MediaFormat.Matroska.let { format ->
      expect(format.hasFileExtension("mKV")).toBe(true)
      expect(format.hasMimeType("VIDEO/x-matroska")).toBe(true)
      expect(format.isAudio).toBe(false)
      expect(format.isVideo).toBe(true)
      expect(format.isPlaylist).toBe(false)
      expect(format.isMediaOrPlaylist).toBe(true)
    }
    MediaFormat.M3u8.let { format ->
      expect(format.hasFileExtension("M3U8")).toBe(true)
      expect(format.hasMimeType("application/X-mpegurl")).toBe(true)
      expect(format.isAudio).toBe(false)
      expect(format.isVideo).toBe(false)
      expect(format.isPlaylist).toBe(true)
      expect(format.isMediaOrPlaylist).toBe(true)
    }
  }

  @Test
  fun testGetFormatFromMimeType() {
    expect(MediaFormat.mediaFormatFromMimeType("audio/x-realaudio, audio/x-pn-realaudio"))
      .toBe(MediaFormat.RealAudio)
  }
}
