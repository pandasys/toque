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

import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Amp
import com.ealva.toque.common.EqPresetId
import com.ealva.toque.log._e
import com.ealva.toque.service.media.EqPreset
import com.ealva.toque.service.media.EqPresetFactory
import com.ealva.toque.service.media.PreAmpAndBands
import com.ealva.toque.ui.nav.backIfAllowed
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.Bundleable
import com.zhuinden.simplestack.ScopedServices
import com.zhuinden.statebundle.StateBundle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Suppress("unused")
private val LOG by lazyLogger(EqPresetEditorModel::class)

interface EqPresetEditorModel {

  fun goBack()

  data class State(
    val currentPreset: EqPreset,
    val allPresets: List<EqPreset>
  )

  val editorState: StateFlow<State>

  companion object {
    operator fun invoke(
      backstack: Backstack,
      eqPresetFactory: EqPresetFactory,
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): EqPresetEditorModel = EqPresetEditorModelImpl(
      backstack = backstack,
      eqPresetFactory = eqPresetFactory,
      dispatcher = dispatcher
    )
  }
}

private val EMPTY_STATE = EqPresetEditorModel.State(
  currentPreset = EqPresetDummy(
    defaultBandValues = arrayOf(
      Amp(0),
      Amp(0),
      Amp(0),
      Amp(0),
      Amp(0),
      Amp(0),
      Amp(0),
      Amp(0),
      Amp(-2),
      Amp(-4),
    )
  ),
  allPresets = emptyList()
)

private class EqPresetEditorModelImpl(
  private val backstack: Backstack,
  private val eqPresetFactory: EqPresetFactory,
  dispatcher: CoroutineDispatcher
) : EqPresetEditorModel, ScopedServices.Registered, ScopedServices.Activated, Bundleable {
  private val scope = CoroutineScope(SupervisorJob() + dispatcher)
  override fun goBack() {
    backstack.backIfAllowed()
  }

  override val editorState = MutableStateFlow(EMPTY_STATE)

  override fun onServiceRegistered() {
    LOG.e { it("onServiceRegistered") }
  }

  override fun onServiceUnregistered() {
    LOG.e { it("onServiceUnregistered") }
  }

  override fun onServiceActive() {
    LOG.e { it("onServiceActive") }
  }

  override fun onServiceInactive() {
    LOG.e { it("onServiceInactive") }
  }

  override fun toBundle(): StateBundle = StateBundle().apply {
    LOG._e { it("toBundle") }
  }

  override fun fromBundle(bundle: StateBundle?) {
    LOG._e { it("fromBundle") }
    bundle?.let { restoreFromBundle(bundle) }
  }

  private fun restoreFromBundle(bundle: StateBundle) {
    LOG._e { it("restoreFromBundle") }
  }
}

private class EqPresetDummy(
  override var id: EqPresetId = EqPresetId(0),
  private val defaultBandValues: Array<Amp> = Array(10) { Amp.NONE }
) : EqPreset {
  override val isNullPreset: Boolean = false
  override var name: String = "Speaker"
  override var isSystemPreset: Boolean = false
  override val displayName: String = if (isSystemPreset) "*$name" else name
  override val bandCount: Int = 10
  override val bandIndices: IntRange = 0 until bandCount

  private val bandFrequencies =
    floatArrayOf(31F, 63F, 125F, 250F, 500F, 1000F, 2000F, 4000F, 8000F, 16000F)

  override fun getBandFrequency(index: Int): Float = bandFrequencies[index]
  override fun get(index: Int): Float = bandValues[index].value

  override var preAmp: Amp = Amp.DEFAULT_PREAMP
  override suspend fun setPreAmp(amplitude: Amp) {
    preAmp = amplitude
  }

  private var bandValues: Array<Amp> = defaultBandValues
  override fun getAmp(index: Int): Amp = bandValues[index]

  override suspend fun setAmp(index: Int, amplitude: Amp) {
    bandValues[index] = amplitude
  }

  override suspend fun resetAllToDefault() {
    preAmp = Amp.DEFAULT_PREAMP
    bandValues = defaultBandValues
  }

  override fun getAllValues(): PreAmpAndBands {
    return PreAmpAndBands(preAmp, bandValues)
  }

  override suspend fun setAllValues(preAmpAndBands: PreAmpAndBands) {
    preAmp = preAmpAndBands.preAmp
    bandValues = preAmpAndBands.bands
  }

  override fun clone(): EqPreset = this
}
