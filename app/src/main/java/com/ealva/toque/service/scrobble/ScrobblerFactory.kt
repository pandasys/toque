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

package com.ealva.toque.service.scrobble

import android.content.Context
import com.ealva.toque.R
import com.ealva.toque.android.content.IntentBroadcaster
import com.ealva.toque.common.PackageName
import com.ealva.toque.common.asPackageName
import com.ealva.toque.prefs.ScrobblerPackage

/**
 * Creates a Scrobbler interface to notify of events a scrobbler needs
 */
interface ScrobblerFactory {

  /**
   * Create a scrobbler for the given package name [selectedScrobbler] or returns [NullScrobbler] if
   * the package name is not found.
   */
  fun make(selectedScrobbler: ScrobblerPackage): Scrobbler

  companion object {
    operator fun invoke(context: Context): ScrobblerFactory =
      ScrobblerFactoryImpl(
        context.getString(R.string.app_name),
        context.packageName.asPackageName,
        IntentBroadcaster(context)
      )
  }
}

private class ScrobblerFactoryImpl(
  private val appName: String,
  private val appPkg: PackageName,
  private val intentBroadcaster: IntentBroadcaster
) : ScrobblerFactory {
  override fun make(selectedScrobbler: ScrobblerPackage): Scrobbler {
    return when (selectedScrobbler) {
      ScrobblerPackage.SimpleLastFm -> SimpleLastFmScrobbler(appName, appPkg, intentBroadcaster)
      ScrobblerPackage.LastFm -> LastFmScrobbler(appPkg, intentBroadcaster)
      else -> NullScrobbler
    }
  }
}
