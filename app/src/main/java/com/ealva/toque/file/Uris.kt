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

@file:Suppress("NOTHING_TO_INLINE")

package com.ealva.toque.file

import android.content.ContentResolver.SCHEME_CONTENT
import android.content.ContentResolver.SCHEME_FILE
import android.net.Uri
import com.ealva.toque.service.media.FileExtensions
import com.ealva.toque.service.media.MediaFormat

private val supportedSet = setOf(
  "http",
  "https",
  SCHEME_FILE,
  "smb",
  "ssh",
  "nfs",
  "ftp",
  "ftps",
  SCHEME_CONTENT
)

private val networkSet = setOf("http", "https", "smb", "ssh", "nfs", "ftp", "ftps")

inline fun String?.toUriOrEmpty(): Uri = if (isNullOrBlank()) Uri.EMPTY else Uri.parse(this)

fun Uri.schemeIsSupported(): Boolean = supportedSet.contains(scheme)

val Uri.isNetworkScheme: Boolean get() = networkSet.contains(scheme)

inline val Uri.isFileScheme: Boolean get() = SCHEME_FILE == scheme

inline val Uri.isContentScheme: Boolean get() = SCHEME_CONTENT == scheme

inline val Uri.isLocalScheme: Boolean get() = isContentScheme || isFileScheme

val Uri.fileExtension: String
  get() = lastPathSegment?.substringAfterLast('.', "") ?: ""

val Uri.mediaFormat: MediaFormat
  get() = MediaFormat.mediaFormatFromExtension(fileExtension)

val Uri.fileExtensionWithDot: String
  get() = lastPathSegment.orEmpty().getFileExtensionWithDot()

private fun String.getFileExtensionWithDot(): String = lastIndexOf(".").let { dotIndex ->
  when (dotIndex) {
    in indices -> substring(dotIndex)
    else -> ""
  }
}

/**
 * Mime Type based on file extension or an empty string if no association found
 */
@Suppress("unused")
val Uri.mimeType: String
  get() = mediaFormat.preferredMimeType ?: ""

fun String.mimeTypeFromExt(): String = substringAfterLast('.').let { ext ->
  MediaFormat.mediaFormatFromExtension(ext).preferredMimeType ?: mimeTypeFromLookup(ext)
}

private fun mimeTypeFromLookup(ext: String): String = when {
  FileExtensions.audio.contains(ext.lowercase()) -> "audio/*"
  FileExtensions.video.contains(ext.lowercase()) -> "video/*"
  else -> ""
}

fun Uri.elseIfEmpty(remote: Uri): Uri = if (this === Uri.EMPTY) remote else this
