/*
 * Copyright (c) 2016-2019. eAlva.com - All Rights Reserved
 *
 * Unauthorized copying of this file, in whole or in part, via any medium, is strictly prohibited
 * Proprietary and confidential
 *
 */

package com.ealva.toque.art.glide

import android.graphics.BitmapFactory
import android.util.Size
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.resource.SimpleResource
import com.bumptech.glide.load.resource.transcode.ResourceTranscoder

/**
 * Transcoder converting from [BitmapFactory.Options], obtained by [BitmapSizeDecoder], to a [Size]
 * object.
 */
class OptionsSizeResourceTranscoder : ResourceTranscoder<BitmapFactory.Options, Size> {
  override fun transcode(
    resource: Resource<BitmapFactory.Options>,
    options: Options
  ): Resource<Size> = SimpleResource(resource.get().outSize)
}

val BitmapFactory.Options.outSize: Size
  get() = Size(outWidth, outHeight)
