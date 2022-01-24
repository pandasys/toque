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

package com.ealva.toque.net

import com.ealva.toque.app.Toque
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File

private const val TWENTY_MEG = 20L * 1024L * 1024L

object NetworkDefaults {
  private val cacheDir: File
    get() = File(Toque.appContext.cacheDir, "OkHttpCache")

  val okHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder().apply {
      cache(Cache(cacheDir, TWENTY_MEG))
    }.build()
  }
}
