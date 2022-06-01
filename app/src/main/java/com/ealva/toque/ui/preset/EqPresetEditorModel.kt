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

import androidx.compose.runtime.Immutable
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Amp
import com.ealva.toque.common.EqBand
import com.ealva.toque.common.EqPresetId
import com.ealva.toque.log._e
import com.ealva.toque.navigation.AllowableNavigation
import com.ealva.toque.service.audio.LocalAudioQueueState
import com.ealva.toque.service.media.EqMode
import com.ealva.toque.service.media.EqPreset
import com.ealva.toque.service.media.EqPreset.BandData
import com.ealva.toque.service.media.EqPresetFactory
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel
import com.ealva.toque.ui.main.MainViewModel
import com.ealva.toque.ui.main.ScreenOrientation
import com.ealva.toque.ui.nav.backIfAllowed
import com.ealva.toque.ui.preset.EqPresetEditorModel.Preset
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.Bundleable
import com.zhuinden.simplestack.ScopedServices
import com.zhuinden.statebundle.StateBundle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val List<EqPreset>.asPresets: List<Preset>
  get() = map { eqPreset -> eqPreset.asPreset }

private val EqPreset.asPreset: Preset
  get() = Preset(id, displayName, isSystemPreset, getAllValues(), eqBands)

@Suppress("unused")
private val LOG by lazyLogger(EqPresetEditorModel::class)

interface EqPresetEditorModel : AllowableNavigation, ScreenOrientation {
  @Immutable
  data class Preset(
    val id: EqPresetId,
    val displayName: String,
    val isSystemPreset: Boolean,
    val bandData: BandData,
    val eqBands: ImmutableList<EqBand>
  ) {
    val isEditable: Boolean
      get() = !isSystemPreset
  }

  @Immutable
  data class State(
    /** True if currently in editing mode */
    val editing: Boolean,
    val currentPreset: Preset,
    val allPresets: List<Preset>,
    val eqMode: EqMode,
  )

  val editorState: StateFlow<State>

  fun toggleEqMode()
  fun presetSelected(preset: Preset)
  fun goBack()
  fun setPreAmp(amp: Amp)
  fun setBand(band: EqBand, amp: Amp)
  fun saveAs()

  companion object {
    operator fun invoke(
      mainViewModel: MainViewModel,
      localAudioModel: LocalAudioQueueViewModel,
      backstack: Backstack,
      eqPresetFactory: EqPresetFactory,
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): EqPresetEditorModel = EqPresetEditorModelImpl(
      mainViewModel = mainViewModel,
      localAudioModel = localAudioModel,
      backstack = backstack,
      eqPresetFactory = eqPresetFactory,
      dispatcher = dispatcher
    )
  }
}

private const val TRY_EMIT_FAILED_MESSAGE = "applyEditsFlow tryEmit failed. Ensure " +
  "MutableSharedFlow is configured with extraBufferCapacity = 1 and onBufferOverflow = " +
  "BufferOverflow.DROP_OLDEST"

private class EqPresetEditorModelImpl(
  private val mainViewModel: MainViewModel,
  private val localAudioModel: LocalAudioQueueViewModel,
  private val backstack: Backstack,
  private val eqPresetFactory: EqPresetFactory,
  dispatcher: CoroutineDispatcher
) : EqPresetEditorModel, ScopedServices.Registered, Bundleable, ScreenOrientation by mainViewModel {
  private val scope = CoroutineScope(SupervisorJob() + dispatcher)
  private val originalOrientation = screenOrientation
  private val audioQueueState = localAudioModel.audioQueueState.value

  /**
   * We are using a flow to apply edits to ensure ordering. Edits may arrive very quickly as the
   * user moves a slider and persisting values is suspending.
   *
   * extraBufferCapacity is 1 and overflow is DROP_OLDEST because we only care about the last edit
   * and we want to use the non-suspending tryEmit.
   */
  private val applyEditsFlow = MutableSharedFlow<BandData>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  override val editorState = MutableStateFlow(
    EqPresetEditorModel.State(
      editing = false,
      currentPreset = audioQueueState.currentPreset.asPreset,
      allPresets = emptyList(),
      eqMode = audioQueueState.eqMode
    )
  )
  private val currentEqPreset: EqPreset
    get() = audioQueueState.currentPreset

  private val currentData: Preset
    get() = currentEqPreset.asPreset

  private var editingValues: BandData? = null
  private var initialValues: BandData? = null

  private val inEditingMode: Boolean
    get() = editingValues != null

  override fun onServiceRegistered() {
    LOG.e { it("onServiceRegistered") }
    localAudioModel.audioQueueState
      .onEach { audioQueueState -> handleAudioQueueState(audioQueueState) }
      .catch { cause -> LOG.e(cause) { it("Error audioQueueState flow") } }
      .launchIn(scope)

    scope.launch {
      eqPresetFactory.allPresets()
        .onEach { list -> editorState.update { state -> state.copy(allPresets = list.asPresets) } }
        .collect()
    }

    applyEditsFlow
      .onEach { presetData -> applyEdit(presetData) }
      .launchIn(scope)
  }

  private suspend fun applyEdit(bandData: BandData) {
    currentEqPreset.setAllValues(bandData)
    editorState.update { state ->
      state.copy(currentPreset = state.currentPreset.copy(bandData = bandData) )
    }
  }

  override fun onServiceUnregistered() {
    scope.cancel()
  }

  override fun goBack() {
    backstack.backIfAllowed()
  }

  override fun setPreAmp(amp: Amp) {
    maybeEstablishInitialValues()
    updateEditingValues { presetData -> presetData.setPreAmp(amp) }
  }

  override fun setBand(band: EqBand, amp: Amp) {
    maybeEstablishInitialValues()
    updateEditingValues { presetData -> presetData.setBand(band, amp) }
  }

  private inline fun updateEditingValues(editBlock: (BandData) -> BandData) {
    editingValues = editingValues?.let { current -> editBlock(current).also { emitEdit(it) } }
  }

  private fun emitEdit(it: BandData) {
    if (!applyEditsFlow.tryEmit(it)) LOG.e { it(TRY_EMIT_FAILED_MESSAGE) }
  }

  override fun saveAs() {
    LOG._e { it("SaveAs") }
  }

  override fun navigateIfAllowed(command: () -> Unit) {
    // If data is unsaved, prompt like below, else execute command
    if (inEditingMode) {
      // show prompt
    } else {
      command()
    }
//    val playlistData = currentPlaylistData
//    if (playlistData.let { it.isValid && it.hasNotBeenEdited }) {
//      command()
//    } else {
//      // Exit and discard all changes? Exit, Cancel, Save (if savable)
//      showExitPrompt(playlistData, command)
//    }
  }

  private fun maybeEstablishInitialValues() {
    if (initialValues == null) {
      currentData.bandData.let { data ->
        initialValues = data
        editingValues = data
      }
    }
  }

  override fun toggleEqMode() {
    localAudioModel.toggleEqMode()
  }

  override fun presetSelected(preset: Preset) {
    localAudioModel.setCurrentPreset(preset.id)
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

  private fun handleAudioQueueState(audioQueueState: LocalAudioQueueState) {
    editorState.update { state ->
      val newEqPreset = audioQueueState.currentPreset
      state.copy(
        currentPreset = newEqPreset.asPreset,
        eqMode = audioQueueState.eqMode
      )
    }
  }
}

