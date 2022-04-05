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

import android.content.Context
import android.util.TypedValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.common.drawable
import com.ealva.toque.common.fetch
import com.ealva.toque.service.MediaPlayerService
import kotlin.math.roundToInt

@Suppress("unused")
private val LOG by lazyLogger(MediumWidget::class)

private val WHITE_PROVIDER = ColorProvider(Color.White)
private val DIM_WHITE_PROVIDER = ColorProvider(Color.White.copy(alpha = 0.73F))

private val ARTIST_TEXT_STYLE = TextStyle(
  color = DIM_WHITE_PROVIDER,
  fontSize = 14.sp,
  fontWeight = FontWeight.Normal,
  fontStyle = FontStyle.Normal,
  textAlign = TextAlign.Center
)

private val ALBUM_TEXT_STYLE = TextStyle(
  color = WHITE_PROVIDER,
  fontSize = 10.sp,
  fontWeight = FontWeight.Normal,
  fontStyle = FontStyle.Normal,
  textAlign = TextAlign.Center
)

private val TITLE_TEXT_STYLE = TextStyle(
  color = WHITE_PROVIDER,
  fontSize = 16.sp,
  fontWeight = FontWeight.Normal,
  fontStyle = FontStyle.Normal,
  textAlign = TextAlign.Center
)

private val DP_BUTTON_SIZE = 32.dp
val DP_LARGEST_BUTTON_SIZE = 38.dp
val DP_MAX_IMAGE_SIZE = 120.dp
val DP_MIN_IMAGE_SIZE = 60.dp

class MediumWidget(private val state: WidgetState) : GlanceAppWidget() {
  override val sizeMode: SizeMode = SizeMode.Exact

  @Composable
  override fun Content() {
//    LOG._e { it("updating widget %s", state) }
//    if (state === WidgetState.NullWidgetState) return

    val widgetSize: DpSize = LocalSize.current
//    LOG._e { it("widget size:%s", widgetSize) }

    val imageSize = (widgetSize.height - DP_LARGEST_BUTTON_SIZE)
      .coerceIn(DP_MIN_IMAGE_SIZE..DP_MAX_IMAGE_SIZE)

    Column(
      modifier = GlanceModifier
        .fillMaxWidth()
        .background(Color.Black.copy(alpha = 0.33F))
        .clickable(actionRunCallback<StartMain>())
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically
      ) {
        val provider = if (state.iconBitmap != null) ImageProvider(state.iconBitmap) else
          ImageProvider(R.drawable.ic_big_album)
        Image(
          provider = provider,
          contentDescription = fetch(R.string.Artwork),
          modifier = GlanceModifier.size(imageSize)
        )
        Column(
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = state.album.value,
            modifier = GlanceModifier.fillMaxWidth(),
            style = ALBUM_TEXT_STYLE
          )
          Text(
            text = state.title.value,
            modifier = GlanceModifier.fillMaxWidth(),
            style = TITLE_TEXT_STYLE
          )
          Text(
            text = state.artist.value,
            modifier = GlanceModifier.fillMaxWidth(),
            style = ARTIST_TEXT_STYLE
          )
        }
      }
      Row(
        modifier = GlanceModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Image(
          provider = ImageProvider(state.repeatMode.drawable),
          contentDescription = fetch(state.repeatMode.titleRes),
          modifier = GlanceModifier
            .size(DP_BUTTON_SIZE)
            .defaultWeight()
            .padding(horizontal = 4.dp)
            .clickable(actionRunCallback<RepeatAction>())
        )
        Image(
          provider = ImageProvider(R.drawable.ic_previous),
          contentDescription = fetch(R.string.Previous),
          modifier = GlanceModifier
            .size(DP_BUTTON_SIZE)
            .defaultWeight()
            .padding(horizontal = 4.dp)
            .clickable(actionRunCallback<PreviousAction>())
        )
        Image(
          provider = ImageProvider(
            resId = if (state.isPlaying) R.drawable.ic_play_pause_playing_button
            else R.drawable.ic_play_pause_paused_button
          ),
          contentDescription = fetch(R.string.Previous),
          modifier = GlanceModifier
            .size(DP_LARGEST_BUTTON_SIZE)
            .defaultWeight()
            .padding(horizontal = 4.dp)
            .clickable(actionRunCallback<TogglePlayPause>())
        )
        Image(
          provider = ImageProvider(R.drawable.ic_baseline_skip_next_24),
          contentDescription = fetch(R.string.Next),
          modifier = GlanceModifier
            .size(DP_BUTTON_SIZE)
            .defaultWeight()
            .padding(horizontal = 4.dp)
            .clickable(actionRunCallback<NextAction>())
        )
        Image(
          provider = ImageProvider(state.shuffleMode.drawable),
          contentDescription = fetch(state.shuffleMode.titleRes),
          modifier = GlanceModifier
            .size(DP_BUTTON_SIZE)
            .defaultWeight()
            .padding(horizontal = 4.dp)
            .clickable(actionRunCallback<ShuffleAction>())
        )
      }
    }
  }

  private fun Dp.toPx(ctx: Context) = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    value,
    ctx.resources.displayMetrics
  ).roundToInt()
}

class StartMain : ActionCallback {
  override suspend fun onRun(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
    context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName))
  }
}

abstract class MediaActionCallback(private val action: MediaPlayerService.Action) : ActionCallback {
  override suspend fun onRun(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
    MediaPlayerService.startForegroundService(context, action)
  }
}

class RepeatAction : MediaActionCallback(MediaPlayerService.Action.NextRepeat)
class PreviousAction : MediaActionCallback(MediaPlayerService.Action.Previous)
class TogglePlayPause : MediaActionCallback(MediaPlayerService.Action.TogglePlayPause)
class NextAction : MediaActionCallback(MediaPlayerService.Action.Next)
class ShuffleAction : MediaActionCallback(MediaPlayerService.Action.NextShuffle)
