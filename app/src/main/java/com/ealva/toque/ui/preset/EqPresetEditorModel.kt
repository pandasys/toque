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

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.LocalContentColor
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.common.Amp
import com.ealva.toque.common.EqBand
import com.ealva.toque.common.EqPresetId
import com.ealva.toque.common.fetch
import com.ealva.toque.log._e
import com.ealva.toque.navigation.AllowableNavigation
import com.ealva.toque.service.audio.LocalAudioQueueState
import com.ealva.toque.service.media.EqMode
import com.ealva.toque.service.media.EqPreset
import com.ealva.toque.service.media.EqPreset.BandData
import com.ealva.toque.service.media.EqPresetFactory
import com.ealva.toque.service.media.EqPresetFactory.EqPresetAssociation
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel
import com.ealva.toque.ui.common.DialogPrompt
import com.ealva.toque.ui.common.PopupMenuItem
import com.ealva.toque.ui.common.ToqueAlertDialog
import com.ealva.toque.ui.common.ToqueDialog
import com.ealva.toque.ui.main.MainViewModel
import com.ealva.toque.ui.main.Notification
import com.ealva.toque.ui.main.ScreenOrientation
import com.ealva.toque.ui.nav.backIfAllowed
import com.ealva.toque.ui.preset.EqPresetEditorModel.Preset
import com.ealva.toque.ui.theme.toqueTypography
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
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
import java.util.regex.Pattern

private val List<EqPreset>.asPresets: List<Preset>
  get() = map { eqPreset -> eqPreset.asPreset }

private val EqPreset.asPreset: Preset
  get() = Preset(id, name, displayName, isSystemPreset, getAllValues(), eqBands)

@Suppress("unused")
private val LOG by lazyLogger(EqPresetEditorModel::class)

interface EqPresetEditorModel : AllowableNavigation, ScreenOrientation {
  @Immutable
  data class Preset(
    val id: EqPresetId,
    val name: String,
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
  ) {
    fun makeMenuItems(viewModel: EqPresetEditorModel): List<PopupMenuItem> = listOf(
      PopupMenuItem(title = fetch(R.string.SaveAs), onClick = { viewModel.saveAs() })
    )
  }

  val editorState: StateFlow<State>

  fun toggleEqMode()
  fun presetSelected(preset: Preset)
  fun goBack()
  fun setPreAmp(amp: Amp)
  fun setBand(band: EqBand, amp: Amp)
  fun saveAs()
  fun assignPreset()

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
) : EqPresetEditorModel, ScopedServices.Registered, ScopedServices.Activated, Bundleable,
  ScreenOrientation by mainViewModel {
  private val scope = CoroutineScope(SupervisorJob() + dispatcher)
  private var audioQueueState = localAudioModel.audioQueueState.value

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

  private suspend fun getCurrentEqPreset(): EqPreset =
    audioQueueState.currentPreset.takeIf { it.isValid } ?: eqPresetFactory.defaultPreset()

  private suspend fun getCurrentData(): Preset = getCurrentEqPreset().asPreset

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

  override fun onServiceActive() = launch {
    localAudioModel.setPresetOverride(getCurrentEqPreset())
  }

  override fun onServiceInactive() {
    localAudioModel.clearPresetOverride()
  }

  override fun onServiceUnregistered() {
    scope.cancel()
  }

  private suspend fun applyEdit(bandData: BandData) {
    getCurrentEqPreset().setAllValues(bandData)
    editorState.update { state ->
      state.copy(currentPreset = state.currentPreset.copy(bandData = bandData))
    }
  }

  override fun goBack() {
    backstack.backIfAllowed()
  }

  override fun setPreAmp(amp: Amp) = launch {
    maybeEstablishInitialValues()
    updateEditingValues { presetData -> presetData.setPreAmp(amp) }
  }

  override fun setBand(band: EqBand, amp: Amp) = launch {
    maybeEstablishInitialValues()
    updateEditingValues { presetData -> presetData.setBand(band, amp) }
  }

  private suspend inline fun updateEditingValues(editBlock: (BandData) -> BandData) {
    editingValues = editingValues?.let { current -> editBlock(current).also { emitEdit(it) } }
  }

  private suspend fun emitEdit(bandData: BandData) {
    if (!applyEditsFlow.tryEmit(bandData)) LOG.e { it(TRY_EMIT_FAILED_MESSAGE) }
    val currentEqPreset = getCurrentEqPreset()
    currentEqPreset.setAllValues(bandData)
    localAudioModel.setPresetOverride(currentEqPreset)
  }

  private fun onDismiss() {
    localAudioModel.clearPrompt()
  }

  override fun saveAs() {
    val presets = editorState.value.allPresets

    fun createEqPreset(name: String) {
      localAudioModel.clearPrompt()
      createPresetWitName(name)
    }

    localAudioModel.showPrompt(
      DialogPrompt(
        prompt = {
          CreateSaveAsPresetName(
            suggestedName = getSuggestedPresetName(presets),
            checkValidName = { isValidName(presets, it) },
            dismiss = ::onDismiss,
            createPreset = { name -> createEqPreset(name) }
          )
        }
      )
    )
  }

  override fun assignPreset() = launch {
    val eqPreset = getCurrentEqPreset()
    LOG._e { it("getAssocs %s %s", eqPreset, eqPreset.id) }
    val currentAssociations = eqPresetFactory.getAssociations(eqPreset)
    val allPossible = EqPresetAssociation.values()
    localAudioModel.showPrompt(
      DialogPrompt(
        prompt = {
          SelectAssociations(
            eqPreset = eqPreset,
            initial = currentAssociations,
            allPossible = allPossible,
            makeAssociation = ::makeAssociations,
            dismiss = ::onDismiss
          )
        }
      )
    )
  }

  private fun makeAssociations(
    eqPreset: EqPreset,
    associations: List<EqPresetAssociation>
  ) = launch {
    onDismiss()
    eqPresetFactory.makeAssociations(
      eqPreset = eqPreset,
      mediaId = audioQueueState.currentMediaId,
      albumId = audioQueueState.currentAlbumId,
      associations = associations
    ).onFailure {
      localAudioModel.emitNotification(
        Notification(fetch(R.string.CouldNotMakeAssocsFor, eqPreset.name), SnackbarDuration.Long)
      )
    }.onSuccess {
      localAudioModel.emitNotification(
        Notification(fetch(R.string.MadeAssocsFor, eqPreset.name), SnackbarDuration.Long)
      )
    }
  }

  private fun createPresetWitName(name: String) = launch {
    eqPresetFactory.makeFrom(getCurrentEqPreset(), name)
      .onFailure { cause ->
        LOG.e(cause) { it("Error creating preset %s", name) }
        localAudioModel.emitNotification(
          Notification(fetch(R.string.CouldNotCreatePreset, name), SnackbarDuration.Long)
        )
      }
      .onSuccess { preset ->
        localAudioModel.emitNotification(
          Notification(fetch(R.string.CreatedPreset, preset.displayName))
        )
        localAudioModel.setPresetOverride(preset)
      }
  }

  private fun getSuggestedPresetName(presets: List<Preset>): String =
    getSuggestedName(presets.asSequence().map { it.name })

  private fun isValidName(presets: List<Preset>, name: String): Boolean =
    name.isValidAndUnique(presets.asSequence().map { it.name })

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

  private suspend fun maybeEstablishInitialValues() {
    if (initialValues == null) {
      getCurrentData().bandData.let { data ->
        initialValues = data
        editingValues = data
      }
    }
  }

  override fun toggleEqMode() {
    localAudioModel.toggleEqMode()
  }

  override fun presetSelected(preset: Preset) = localAudioModel.setPresetOverride(preset.id)

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

  private fun handleAudioQueueState(newAudioQueueState: LocalAudioQueueState) {
    audioQueueState = newAudioQueueState
    launch {
      editorState.update { state ->
        val newEqPreset = getCurrentData()
        state.copy(
          currentPreset = newEqPreset,
          eqMode = audioQueueState.eqMode
        )
      }
    }
  }

  private inline fun launch(crossinline block: suspend () -> Unit) {
    scope.launch { block() }
  }
}

@Composable
private fun SelectAssociations(
  eqPreset: EqPreset,
  initial: List<EqPresetAssociation>,
  allPossible: Array<EqPresetAssociation>,
  makeAssociation: (EqPreset, List<EqPresetAssociation>) -> Unit,
  dismiss: () -> Unit
) {
  LOG._e { it("initial %s", initial) }
  val selected = remember { initial.toMutableStateList() }

  fun selectionChanged(assoc: EqPresetAssociation, isSelected: Boolean) {
    LOG._e { it("selectionChanged %s %s", assoc, isSelected) }
    if (isSelected) selected.add(assoc) else selected.remove(assoc)
    LOG._e { it("selected %s", selected.toList()) }
  }

  LOG._e { it("selected %s", selected.toList()) }
  ToqueDialog(onDismissRequest = dismiss) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.Top,
      horizontalAlignment = Alignment.Start
    ) {
      Text(
        modifier = Modifier.padding(horizontal = 8.dp),
        text = stringResource(id = R.string.AssignPreset),
        color = LocalContentColor.current,
        style = toqueTypography.h6
      )
      Column(
        modifier = Modifier
          .padding(start = 8.dp, top = 8.dp, bottom = 10.dp)
      ) {
        allPossible.forEach { association ->
          AssociationCheckBox(
            eqPresetAssociation = association,
            isSelected = selected.contains(association),
            selectionChanged = ::selectionChanged
          )
        }
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
      ) {
        Button(
          modifier = Modifier.padding(start = 8.dp),
          onClick = dismiss
        ) {
          Text(stringResource(R.string.Cancel))
        }
        Button(
          modifier = Modifier.padding(horizontal = 8.dp),
          onClick = { makeAssociation(eqPreset, selected) }
        ) {
          Text(stringResource(R.string.Assign))
        }
      }
    }
  }
//  ToqueAlertDialog(
//    onDismissRequest = dismiss,
//    title = {
//      Text(
//        text = stringResource(id = R.string.AssignPreset),
//        color = LocalContentColor.current,
//        style = toqueTypography.h6
//      )
//    },
//    text = {
//      Column {
//        Spacer(modifier = Modifier.height(8.dp))
//        allPossible.forEach { association ->
//          AssociationCheckBox(
//            eqPresetAssociation = association,
//            isSelected = selected.contains(association),
//            selectionChanged = ::selectionChanged
//          )
//        }
//      }
//    },
//    buttons = {
//      Row(
//        modifier = Modifier
//          .padding(start = 18.dp, end = 18.dp, bottom = 18.dp)
//          .fillMaxWidth(),
//        horizontalArrangement = Arrangement.End
//      ) {
//        Button(
//          modifier = Modifier.padding(start = 8.dp),
//          onClick = dismiss
//        ) {
//          Text(stringResource(R.string.Cancel))
//        }
//        Button(
//          modifier = Modifier.padding(horizontal = 8.dp),
//          onClick = { makeAssociation(eqPreset, selected) }
//        ) {
//          Text(stringResource(R.string.Assign))
//        }
//      }
//    },
//  )
}

@Composable
private fun AssociationCheckBox(
  eqPresetAssociation: EqPresetAssociation,
  isSelected: Boolean,
  selectionChanged: (assoc: EqPresetAssociation, isSelected: Boolean) -> Unit
) {
  // share an interaction source so  both checkbox and text field show touch animation
  val interactionSource = remember { MutableInteractionSource() }

  Row(
    horizontalArrangement = Arrangement.Start,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Checkbox(
      checked = isSelected,
      onCheckedChange = { selected -> selectionChanged(eqPresetAssociation, selected) },
      interactionSource = interactionSource
    )
    Text(
      modifier = Modifier
        .padding(end = 6.dp)
        .weight(1F)
        .clickable(
          interactionSource = interactionSource,
          indication = LocalIndication.current,
          onClick = { selectionChanged(eqPresetAssociation, !isSelected) },
        ),
      text = eqPresetAssociation.toString(),
      textAlign = TextAlign.Start,
      maxLines = 1,
      color = LocalContentColor.current,
      style = toqueTypography.body1,
    )
  }
}

@Composable
private fun CreateSaveAsPresetName(
  suggestedName: String,
  checkValidName: (String) -> Boolean,
  dismiss: () -> Unit,
  createPreset: (String) -> Unit
) {
  var name by remember { mutableStateOf(suggestedName) }
  var nameIsValid by remember { mutableStateOf(true) }

  fun nameValidity(newName: String) {
    nameIsValid = checkValidName(newName)
    name = newName
  }

  ToqueAlertDialog(
    onDismissRequest = dismiss,
    buttons = {
      Row(
        modifier = Modifier
          .padding(start = 18.dp, end = 18.dp, bottom = 18.dp)
          .fillMaxWidth(),
        horizontalArrangement = Arrangement.End
      ) {
        Button(
          modifier = Modifier.padding(start = 8.dp),
          onClick = dismiss
        ) {
          Text(stringResource(R.string.Cancel))
        }
        Button(
          modifier = Modifier.padding(horizontal = 8.dp),
          enabled = nameIsValid,
          onClick = { createPreset(name) }
        ) {
          Text(stringResource(R.string.OK))
        }
      }
    },
    text = {
      OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = name,
        onValueChange = { nameValidity(it) },
        label = { Text(text = stringResource(id = R.string.SavePresetAs)) },
        singleLine = true
      )
    }
  )

}

private fun getSuggestedName(
  existingNames: Sequence<String>,
  start: String = fetch(R.string.Preset),
): String {
  var suffix = 0
  val prefix = if (start.endsWith(' ')) start else "$start "
  existingNames.forEach { name ->
    if (name.startsWith(prefix)) {
      suffix = suffix.coerceAtLeast(prefix.getNumAtEndOfString(name))
    }
  }
  return prefix + (suffix + 1)
}


private fun String.getNumAtEndOfString(input: String): Int {
  val lastIntPattern = Pattern.compile("${this}([0-9]+)$")
  val matcher = lastIntPattern.matcher(input)
  if (matcher.find()) {
    return matcher.group(1)?.let { someNumberStr -> Integer.parseInt(someNumberStr) } ?: -1
  }
  return -1
}

private fun String.isValidAndUnique(list: Sequence<String>): Boolean =
  isNotBlank() && list.none { element -> element.equalsIgnoreCase(this) }

private fun String.equalsIgnoreCase(other: String): Boolean = equals(other, true)
