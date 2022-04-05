/*
 * Copyright 2022 Eric A. Snell
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

package com.ealva.toque.service.image

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.TypedValue
import android.util.TypedValue.COMPLEX_UNIT_DIP
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.PixelSize
import coil.size.Size
import com.ealva.toque.R
import com.ealva.toque.app.Toque.Companion.appContext
import kotlin.math.roundToInt

private inline val Dp.asPx: Int
  get() = TypedValue
    .applyDimension(COMPLEX_UNIT_DIP, value, appContext.resources.displayMetrics)
    .roundToInt()

private inline val Dp.asSize: Size get() = asPx.let { px -> PixelSize(px, px) }

/**
 * This is the default size of bitmaps loaded for Android metadata. It's lazy as we use the
 * Toque.[appContext] to calculate pixel size, so the calculation shouldn't be done until we need
 * the value the first time.
 *
 * The current max bitmap size in MediaSessionCompat is 320dp
 * ```
 * private static final int MAX_BITMAP_SIZE_IN_DP = 320;
 * ```
 * When we load a bitmap for metadata we'll go ahead and use this size. Typically it will be a
 * reduction in size, but if the album or artist artwork is small, we could get a small resize
 * larger.
 */
val METADATA_MAX_BMP_SIZE by lazy { 320.dp.asSize }

/**
 * Creates a Bitmap of [size] from this Uri if it points to a image and the image is in a format
 * supported by our image loader (currently Coil). [size] defaults to the maximum size allowed
 * in Android metadata but can be set to any size, including [coil.size.OriginalSize]
 */
suspend fun Uri.loadAsBitmap(
  size: Size = METADATA_MAX_BMP_SIZE,
  fallback: Int = R.drawable.ic_big_album,
): Bitmap? = appContext.let { ctx ->
  (ctx.imageLoader.execute(
    ImageRequest.Builder(ctx)
      .data(if (this !== Uri.EMPTY) this else fallback)
      .error(fallback)
      .allowHardware(false)
      .size(size)
      .build()
  ).drawable as? BitmapDrawable)?.bitmap
}
