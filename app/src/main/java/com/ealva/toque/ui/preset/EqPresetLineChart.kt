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

package com.ealva.toque.ui.preset

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ealva.toque.common.Amp
import com.ealva.toque.common.EqBand
import com.ealva.toque.common.EqPresetId
import com.ealva.toque.common.Frequency
import com.ealva.toque.service.media.EqPreset
import com.ealva.toque.service.media.EqPreset.BandData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

private val ClosedRange<Amp>.spread: Float
  get() = endInclusive.value - start.value

private const val GRAPH_SMOOTHNESS = 0.1f

private data class CanvasConfig(
  val spread: Float,
  val widthDp: Dp,
  val width: Float,
  val heightDp: Dp,
  val height: Float,
  val paddingLeft: Float,
  val paddingTop: Float,
  val paddingRight: Float,
  val paddingBottom: Float
) {
  val offset = spread / 2
}

@Composable
fun EqPresetLineChart(
  bands: ImmutableList<Amp>,
  width: Dp,
  height: Dp,
  padding: PaddingValues = PaddingValues(),
  color: Color = LocalContentColor.current
) {
  val config = with(LocalDensity.current) {
    CanvasConfig(
      Amp.RANGE.spread,
      width,
      width.toPx(),
      height,
      height.toPx(),
      padding.calculateStartPadding(LayoutDirection.Ltr).toPx(),
      padding.calculateTopPadding().toPx(),
      padding.calculateEndPadding(LayoutDirection.Ltr).toPx(),
      padding.calculateBottomPadding().toPx()
    )
  }

  LineChart(points = bands, color = color, canvasConfig = config)
}

@Composable
private fun LineChart(points: ImmutableList<Amp>, color: Color, canvasConfig: CanvasConfig) {
  Canvas(
    modifier = Modifier
      .width(canvasConfig.widthDp)
      .height(canvasConfig.heightDp)
  ) {
    val yCenter = canvasConfig.height / 2
    drawLine(
      color.copy(alpha = .5F),
      Offset(canvasConfig.paddingLeft, yCenter),
      Offset(canvasConfig.width - canvasConfig.paddingRight, yCenter)
    )
    if (points.isNotEmpty()) drawPath(
      path = createSmoothPath(points, canvasConfig),
      color = color,
      style = Stroke(4F)
    )
  }

}

private fun createSmoothPath(
  points: ImmutableList<Amp>,
  config: CanvasConfig
): Path = Path().apply {
  val maxX = points.indices.last.toFloat()
  moveTo(
    getXPos(0, maxX, config),
    getYPos(points[0].value, config)
  )
  for (i in 1 until points.size - 1) {
    val thisPointX: Float = getXPos(i, maxX, config)
    val thisPointY: Float = getYPos(points[i].value, config)
    val nextPointX: Float = getXPos(i + 1, maxX, config)
    val nextPointY: Float = getYPos(points[points.index(i + 1)].value, config)
    val startDiffX: Float = nextPointX - getXPos(points.index(i - 1), maxX, config)
    val startDiffY: Float = nextPointY - getYPos(points[points.index(i - 1)].value, config)
    val endDiffX: Float = getXPos(points.index(i + 2), maxX, config) - thisPointX
    val endDiffY: Float = getYPos(points[points.index(i + 2)].value, config) - thisPointY
    val firstControlX: Float = thisPointX + GRAPH_SMOOTHNESS * startDiffX
    val firstControlY: Float = thisPointY + GRAPH_SMOOTHNESS * startDiffY
    val secondControlX: Float = nextPointX - GRAPH_SMOOTHNESS * endDiffX
    val secondControlY: Float = nextPointY - GRAPH_SMOOTHNESS * endDiffY
    cubicTo(firstControlX, firstControlY, secondControlX, secondControlY, nextPointX, nextPointY)
  }
}

private fun ImmutableList<Amp>.index(index: Int): Int {
  return index.coerceIn(indices)
}

private fun getYPos(value: Float, config: CanvasConfig): Float {
  val height: Float = config.height - config.paddingTop - config.paddingBottom
  var yPos = (value + config.offset) / config.spread * height
  yPos = height - yPos
  yPos += config.paddingTop
  return yPos
}

private fun getXPos(value: Int, maxValue: Float, config: CanvasConfig): Float {
  var xPos = value.toFloat()
  val width: Float = config.width - config.paddingLeft - config.paddingRight
  xPos = xPos / maxValue * width
  xPos += config.paddingLeft
  return xPos
}

@Preview
@Composable
fun EqPresetLineChartPreview() {
  val preset = EqPresetStub(
    defaultBandValues = arrayOf(
      Amp(3),
      Amp(5),
      Amp(8),
      Amp(3),
      Amp(0),
      Amp(-3),
      Amp(-5),
      Amp(-10),
      Amp(-18),
      Amp(-20),
    )
  )
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black)
  ) {
    EqPresetLineChart(
      bands = preset.getAllValues().bandValues,
      width = 100.dp,
      height = 40.dp,
      padding = PaddingValues(4.dp),
      color = Color.White
    )
  }
}

private class EqPresetStub(
  override var id: EqPresetId = EqPresetId(0),
  private val defaultBandValues: Array<Amp> = Array(10) { Amp.NONE }
) : EqPreset {
  override var name: String = "EqPresetStub"
  override val displayName: String = name
  override var isSystemPreset: Boolean = false
  private val bandFrequencies =
    floatArrayOf(31F, 63F, 125F, 250F, 500F, 1000F, 2000F, 4000F, 8000F, 16000F)
  override val eqBands: ImmutableList<EqBand> = buildList<EqBand> {
    bandFrequencies.forEachIndexed { index, frequency ->
      EqBand(index, Frequency(frequency))
    }
  }.toImmutableList()

  override var preAmp: Amp = Amp.DEFAULT_PREAMP

  override suspend fun setPreAmp(amplitude: Amp) {
    preAmp = amplitude
  }

  private var bandValues: Array<Amp> = defaultBandValues
  override fun getAmp(band: EqBand): Amp = bandValues[band.index]

  override suspend fun setAmp(band: EqBand, amplitude: Amp) {
    bandValues[band.index] = amplitude
  }

  override suspend fun resetAllToDefault() {
    preAmp = Amp.DEFAULT_PREAMP
    bandValues = defaultBandValues
  }

  override fun getAllValues(): BandData = BandData(preAmp, bandValues.toList())

  override suspend fun setAllValues(bandData: BandData) {
    preAmp = bandData.preAmp
    bandValues = bandData.bandValues.toTypedArray()
  }
}
