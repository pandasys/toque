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

package com.ealva.toque.android.telcom

import android.Manifest.permission.READ_PHONE_STATE
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.app.Toque.Companion.appContext
import com.ealva.toque.ui.main.RequestPermissionActivity
import com.ealva.toque.ui.main.RequestPermissionActivity.Companion.READ_PHONE_STATE_DATA

private val LOG by lazyLogger("TelecomManager")

/**
 * If we have permission, check if the phone is idle. If we don't have permission, we may
 * [requestPermission], false is returned as we don't want to interrupt a call - so assume
 * not idle.
 */
fun TelecomManager.isIdle(requestPermission: Boolean): Boolean = try {
  if (haveReadPhoneStatePermission) !isInCall else false.also {
    if (requestPermission) RequestPermissionActivity.start(READ_PHONE_STATE_DATA)
  }
} catch (e: SecurityException) {
  LOG.e(e) { it("Error getting phone idle state") }
  false
}

private val haveReadPhoneStatePermission: Boolean
  get() = ContextCompat.checkSelfPermission(appContext, READ_PHONE_STATE) == PERMISSION_GRANTED
