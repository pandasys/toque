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

package com.ealva.toque.tag

import com.ealva.toque.media.MediaFormat
import ealvatag.tag.images.Artwork
import ealvatag.tag.reference.PictureTypes

class ArtworkField(private val artwork: Artwork?) {

  val height: Int
    get() = artwork?.height ?: -1

  val width: Int
    get() = artwork?.width ?: -1

  /**
   * Check if this tag contains a Url. @see #getUrl
   *
   * @return true if artwork exists and is a Url
   */
  @Suppress("MemberVisibilityCanBePrivate")
  val isUrl: Boolean
    get() = artwork?.isLinked == true

  /**
   * Check if this tag contains binary data. @see #getData
   *
   * @return true if artwork exists and is binary data.
   */
  @Suppress("MemberVisibilityCanBePrivate")
  val isBinaryData: Boolean
    get() = artwork?.isLinked != true

  /**
   * Get the artwork Url.
   *
   * @return string form of a Url
   *
   * @throws NullPointerException  if the artwork doesn't exist @see #isUrl
   * @throws IllegalStateException if this artwork is not a Url @see #isUrl
   */
  val url: String
    get() {
      requireNotNull(artwork) { "Check exists()" }
      check(isUrl) { "Artwork is binary data. Check isUrl() before calling this method." }
      return artwork.imageUrl ?: ""
    }

  /**
   * Get the artwork raw data - a byte[]
   *
   * @return artwork raw image data
   *
   * @throws NullPointerException  if the artwork doesn't exist @see #isBinaryData
   * @throws IllegalStateException if this artwork is not image data
   */
  val data: ByteArray
    get() {
      requireNotNull(artwork) { "Check exists()" }
      check(isBinaryData) { "Artwork is an Url. Check isBinaryData() before calling this method" }
      return artwork.binaryData ?: ByteArray(0)
    }

  val mimeType: String
    get() {
      if (isBinaryData) {
        return artwork?.mimeType ?: "Unknown"
      } else if (isUrl) {
        val dotIndex = url.lastIndexOf(".")
        val mimeTypes = MediaFormat.mediaFormatFromExtension(
          if (dotIndex == -1 || dotIndex >= url.length - 1) {
            ""
          } else {
            url.substring(dotIndex + 1)
          }
        )
          .mimeTypes
        return if (mimeTypes.isEmpty()) "image/*" else mimeTypes[0]
      }
      return "Unspecified"
    }

  val pictureDescription: String
    get() {
      var desc = ""
      if (isBinaryData) {
        desc = PictureTypes.getInstanceOf().getValue(artwork!!.pictureType)
      } else if (isUrl) {
        desc = url
      }
      return if (desc.isEmpty()) "Unspecified" else desc
    }

  fun exists(): Boolean {
    return artwork != null
  }
}
