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

package com.ealva.toque.file

import android.content.ContentResolver.SCHEME_CONTENT
import android.content.ContentResolver.SCHEME_FILE
import android.net.Uri
import com.ealva.toque.media.FileExtensions
import com.ealva.toque.media.MediaFormat
import java.util.Locale

private val supportedSet = setOf(SCHEME_FILE, "smb", "ssh", "nfs", "ftp", "ftps", SCHEME_CONTENT)
private val networkSet = setOf("smb", "ssh", "nfs", "ftp", "ftps")

fun Uri.schemeIsSupported(): Boolean = supportedSet.contains(scheme)

fun Uri.isNetworkScheme(): Boolean = networkSet.contains(scheme)

fun Uri.isFileScheme(): Boolean = SCHEME_FILE == scheme

fun Uri.isContentScheme(): Boolean = SCHEME_CONTENT == scheme

val Uri.fileExtension: String
  get() = lastPathSegment?.substringAfterLast('.') ?: ""

val Uri.mediaFormat: MediaFormat
  get() = MediaFormat.mediaFormatFromExtension(fileExtension)

/**
 * Mime Type based on file extension or an empty string if no association found
 */
val Uri.mimeType: String
  get() = mediaFormat.preferredMimeType ?: ""

fun String.mimeTypeFromExt(): String = substringAfterLast('.').let { ext ->
  MediaFormat.mediaFormatFromExtension(ext).preferredMimeType ?: mimeTypeFromLookup(ext)
}

private fun mimeTypeFromLookup(ext: String): String = when {
  FileExtensions.audio.contains(ext.toLowerCase(Locale.ROOT)) -> "audio/*"
  FileExtensions.video.contains(ext.toLowerCase(Locale.ROOT)) -> "video/*"
  else -> ""
}
