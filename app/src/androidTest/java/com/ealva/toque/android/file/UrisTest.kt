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

import android.net.Uri
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ealva.toque.file.isContentScheme
import com.ealva.toque.file.isFileScheme
import com.ealva.toque.file.isNetworkScheme
import com.ealva.toque.file.mimeTypeFromExt
import com.ealva.toque.file.schemeIsSupported
import com.nhaarman.expect.Matcher
import com.nhaarman.expect.expect
import com.nhaarman.expect.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.videolan.libvlc.util.Extensions

private val fileUri: Uri = "file:///storage/emulated/0/Pictures/blah.mp3".toUri()
private val smbUri: Uri = "smb://workgroup;user:password@server/share/folder/file.mp3".toUri()
private val sshUri: Uri = "ssh://myid@192.168.1.101:22/song.mp2".toUri()
private val nfsUri: Uri = "nfs://myNFS1/test.mp3".toUri()
private val ftpUri: Uri = "ftp://127.0.0.1:3531/movie.avi".toUri()
private val ftpsUri: Uri = "ftps://127.0.0.1:3531/movie.avi".toUri()
private val contentUri: Uri = "content://media/external/audio/media/957".toUri()

private val allUris = listOf(
  fileUri, smbUri, sshUri, nfsUri, ftpUri, ftpsUri, contentUri
)

private val networkUris = listOf(
  smbUri, sshUri, nfsUri, ftpUri, ftpsUri
)

@RunWith(AndroidJUnit4::class)
class UrisTest {
  @Test
  fun testSchemeSupported() {
    allUris.forEach { uri ->
      expect(uri.schemeIsSupported()).toBe(true)
    }
  }

  @Test
  fun testNetworkScheme() {
    networkUris.forEach { uri ->
      expect(uri.isNetworkScheme()).toBe(true)
    }
  }

  @Test
  fun testFileScheme() {
    expect(fileUri.isFileScheme()).toBe(true)
  }

  @Test
  fun testContentScheme() {
    expect(contentUri.isContentScheme()).toBe(true)
  }

  @Test
  fun testVideoFileExtMimeType() {
    Extensions.VIDEO.filterNot { listOf(".mp2", ".ps").contains(it) }.forEach { ext ->
      expect(ext.mimeTypeFromExt()).toNotBeNullOrEmpty { "$ext no MimeType" }
    }
  }

  @Test
  fun testAudioFileExtMimeType() {
    Extensions.AUDIO.forEach { ext ->
      expect(ext.mimeTypeFromExt()).toNotBeNullOrEmpty { "$ext no MimeType" }
    }
  }

  @Test
  fun testPlaylistFileExtMimeType() {
    Extensions.PLAYLIST
      .filterNot { it == ".b4s" || it == ".wpl" }
      .forEach { ext -> expect(ext.mimeTypeFromExt()).toNotBeNullOrEmpty { "$ext no MimeType" } }
  }
}

fun Matcher<String>.toNotBeNullOrEmpty(message: (() -> Any?)? = null) {
  if (actual.isNullOrEmpty()) {
    fail("""Expected "$actual" not to be null or empty.""", message)
  }
}
