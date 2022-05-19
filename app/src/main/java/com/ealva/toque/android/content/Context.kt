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

package com.ealva.toque.android.content

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.ealva.toque.BuildConfig

inline fun <reified T : Any> Context.requireSystemService(): T = checkNotNull(getSystemService())

fun Context.canReadStorage() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
  Environment.isExternalStorageManager()
} else {
  ContextCompat.checkSelfPermission(
    this,
    Manifest.permission.READ_EXTERNAL_STORAGE
  ) == PackageManager.PERMISSION_GRANTED
}

fun Context.cannotReadStorage() = !canReadStorage()

fun Context.canWriteStorage() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
  Environment.isExternalStorageManager()
} else ContextCompat.checkSelfPermission(
  this,
  Manifest.permission.WRITE_EXTERNAL_STORAGE
) == PackageManager.PERMISSION_GRANTED

fun Context.cannotWriteStorage() = !canWriteStorage()

@RequiresApi(Build.VERSION_CODES.R)
fun hasAllAccess(context: Context) = !Intent(
  Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
  Uri.parse("package:${BuildConfig.APP_ID}")
).isCallable(context) || Environment.isExternalStorageManager()
