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

package com.ealva.toque.service.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.ealva.toque.service.MediaPlayerService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MediumWidgetReceiver : GlanceAppWidgetReceiver(), KoinComponent {
  private val widgetUpdater: WidgetUpdater by inject()

  override val glanceAppWidget: MediumWidget
    get() = MediumWidget(widgetUpdater.widgetState)

  /**
   * If we don't have a bitmap it's pretty safe to assume the service hasn't been started. We need
   * to start the service, notifying it that widgets need updating. This would typically happen when
   * the device boots.
   */
  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray
  ) {
    super.onUpdate(context, appWidgetManager, appWidgetIds)
    if (widgetUpdater.widgetState.iconBitmap == null) {
      MediaPlayerService.startForWidgetsUpdate(context, appWidgetIds)
    }
  }
}
