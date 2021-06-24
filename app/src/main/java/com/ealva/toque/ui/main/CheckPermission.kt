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

package com.ealva.toque.ui.main

import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

interface CheckPermission {
  fun checkSelfPermission(permission: String): Boolean
  suspend fun requestPermission(permission: String): Boolean

  companion object {
    operator fun invoke(activity: ComponentActivity): CheckPermission =
      CheckPermissionImpl(activity)
  }
}

class CheckPermissionImpl(activity: ComponentActivity) : DefaultLifecycleObserver, CheckPermission {
  private val appCtx = activity.applicationContext
  private val registry: ActivityResultRegistry = activity.activityResultRegistry
  private val key = "RequestPermissionObserver"
  private lateinit var getPermission: ActivityResultLauncher<String>
  private var continuation: Continuation<Boolean>? = null

  init {
    activity.lifecycle.addObserver(this)
  }

  override fun onCreate(owner: LifecycleOwner) {
    getPermission = registry.register(
      key,
      owner,
      ActivityResultContracts.RequestPermission()
    ) { granted ->
      continuation?.resume(granted)
    }
  }

  override fun checkSelfPermission(
    permission: String
  ): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    appCtx.checkSelfPermission(permission) == PERMISSION_GRANTED
  } else true

  override suspend fun requestPermission(permission: String): Boolean = suspendCoroutine {
    continuation = it
    getPermission.launch(permission)
  }
}
