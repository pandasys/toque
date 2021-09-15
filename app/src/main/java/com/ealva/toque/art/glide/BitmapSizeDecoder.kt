package com.ealva.toque.art.glide

import android.graphics.BitmapFactory
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.resource.SimpleResource
import java.io.File

/**
 * Decoder to get the size of an image from a file. Bitmap will not actually be allocated.
 */
class BitmapSizeDecoder : ResourceDecoder<File, BitmapFactory.Options> {
  override fun handles(file: File, options: Options): Boolean {
    return true
  }

  /**
   * Get the bitmap size as if it were fully decoded but the options specify to not actually
   * create a bitmap. Superclass return is nullable but we'll tighten the contract to non-nullable.
   */
  override fun decode(
    file: File,
    width: Int,
    height: Int,
    options: Options
  ): Resource<BitmapFactory.Options> = SimpleResource(
    BitmapFactory.Options().apply {
      inJustDecodeBounds = true
      BitmapFactory.decodeFile(file.absolutePath, this)
    }
  )
}
