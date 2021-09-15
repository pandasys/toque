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

package com.ealva.toque.art.glide

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Size
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.load.engine.cache.MemorySizeCalculator
import com.bumptech.glide.module.AppGlideModule
import com.ealva.ealvalog.Logger
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.debug
import com.ealva.toque.log._i
import com.ealva.toque.prefs.AppPrefsSingleton
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

private val LOG by lazyLogger(ToqueGlideModule::class)

private fun MemorySizeCalculator.log(log: Logger, name: String) {
  log._i {
    it(
      "%s memoryCache=%d bitmapPool=%d arrayPool=%d",
      name,
      memoryCacheSize,
      bitmapPoolSize,
      arrayPoolSizeInBytes
    )
  }
}

private const val MAX_SIZE_MULTIPLIER = 0.25F // Glide default is 0.4f
private const val LOW_MEMORY_DEVICE_MAX_SIZE_MULTIPLIER = 0.15F // Glide default is 0.33f

@com.bumptech.glide.annotation.GlideModule
class ToqueGlideModule : AppGlideModule(), KoinComponent {
  private lateinit var context: Context

  override fun isManifestParsingEnabled(): Boolean {
    return false
  }

  override fun applyOptions(context: Context, builder: GlideBuilder) {
    this.context = context
    val prefs: AppPrefsSingleton by inject()

    val calculator = MemorySizeCalculator.Builder(context)
      .setMaxSizeMultiplier(MAX_SIZE_MULTIPLIER)
      .setLowMemoryMaxSizeMultiplier(LOW_MEMORY_DEVICE_MAX_SIZE_MULTIPLIER)
      .build()

    debug {
      MemorySizeCalculator.Builder(context).build().log(LOG, "Unadjusted")
      calculator.log(LOG, "Calculator")
    }

    builder
      .setMemorySizeCalculator(calculator)
//      .setDiskCache(
//        if (prefs.artCacheOnInternal) {
//          LOG._i { it("art cache on internal") }
//          InternalCacheDiskCacheFactory(context, prefs.artDiscCacheSizeInBytes.toLong())
//        } else {
//          LOG._i { it("art cache on external") }
//          ExternalPreferredCacheDiskCacheFactory(context, prefs.artDiscCacheSizeInBytes.toLong())
//        }
//      )
  }

  override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
    registry.prepend(File::class.java, BitmapFactory.Options::class.java, BitmapSizeDecoder())
    registry.register(
      BitmapFactory.Options::class.java,
      Size::class.java,
      OptionsSizeResourceTranscoder()
    )
//    AlbumArtInfoModelLoaderFactory.register(context, registry)
//    SongArtInfoModelLoaderFactory.register(context, registry)
//    ArtistModelLoaderFactory.register(context, registry)
//    SongModelLoaderFactory.register(context, registry)
  }
}
