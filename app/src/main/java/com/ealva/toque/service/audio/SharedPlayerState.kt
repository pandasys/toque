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

package com.ealva.toque.service.audio

import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.audioout.AudioOutputModule
import com.ealva.toque.audioout.AudioOutputRoute
import com.ealva.toque.common.EqPresetId
import com.ealva.toque.common.PlaybackRate
import com.ealva.toque.db.EqPresetIdName
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.prefs.DuckAction
import com.ealva.toque.service.media.EqMode
import com.ealva.toque.service.media.EqPreset
import com.ealva.toque.service.media.EqPresetFactory
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val LOG by lazyLogger(SharedPlayerState::class)

/**
 * The class contains player state which is shared between player instances, such as PlaybackRate,
 * EqPreset, ducked state, ... really anything which must be maintained when transitioning
 * between players. Both "set" and "get" may be done at any level of the containment hierarchy,
 * from queue down to the player itself. When a new player is instantiated it will use the info
 * here to set it's state and should also react to state changes.
 *
 * Whether or not any of this info needs to be persisted outside the scope of this class.
 */
interface SharedPlayerState {
  /**
   * Current ducked state. Can be read by anyone but should only be set by the owner (typically
   * queue level)
   */
  var duckedState: DuckAction

  /** Is the current action ducked */
  val ducked: Boolean
    get() = duckedState === DuckAction.Duck

  /** Is the current action paused */
  val paused: Boolean
    get() = duckedState === DuckAction.Pause

  val playbackRate: StateFlow<PlaybackRate>

  /**
   * Is the equalizer on or off. When this value changes the preferred preset for a piece of media
   * may change.
   */
  val eqMode: StateFlow<EqMode>

  /**
   * Indicate where audio is being routed. When this value changes the preferred preset for a piece
   * of media may change
   */
  val outputRoute: StateFlow<AudioOutputRoute>

  /**
   * EqPreset equals is identity. So when editing preset and setting that preset, ensure the preset
   * is a copy so that this flow will emit.
   */
  val currentPreset: StateFlow<EqPreset>

  /** User requested OpenSL ES or AudioTrack */
  val outputModule: StateFlow<AudioOutputModule>

  /**
   * Set the preferred preset based on [mediaId] or [albumId] or AudioOutputRoute. If a preferred is
   * not found based on those criteria, a default is returned. If [eqMode] is [EqMode.Off] a preset
   * representing "none" is emitted. Collect [currentPreset] to watch EQ preset changes
   */
  fun setPreferred(mediaId: MediaId, albumId: AlbumId)

  /**
   * Set the [currentPreset] to the EqPreset with [id]. If [eqMode] is off, or If [id] is not found,
   * the current preset is not updated.
   */
  fun setCurrent(id: EqPresetId)

  /**
   * This is used when a user preset is being edited. In this case, ensure each [preset[] is a copy
   * so the [currentPreset] will emit a new value. Also, the eq editor should probably suppress
   * changes by detecting any change and setting it back to it's copy. This can happen when
   * media changes during playback.
   */
  fun setCurrent(preset: EqPreset)

  /** Get all preset id/name pairs, typically to display to the user */
  suspend fun getAll(): List<EqPresetIdName>

  companion object {
    /**
     * Instances will use [scope] for structured concurrency (instance live as long as their
     * container, typically a queue of playable items). [factory] is used to retrieve instances
     * of EqPresets based on [eqMode] and other criteria
     *
     * [eqMode] and [outputRoute] are used to determine preferred presets, see [setPreferred], but
     * are contained elsewhere, so are passed as params here. The are also used to indicate to
     * clients that the preferred preset may be different, so are also offered via this interface.
     *
     * [dispatcher] defaults to [Dispatchers.IO] instead of [Dispatchers.Default] thinking this may
     * avoid a context switch ([factory] will do some DB IO to get a preference) and/or interfere
     * with transitions which currently execute on [Dispatchers.Default]
     */
    operator fun invoke(
      scope: CoroutineScope,
      factory: EqPresetFactory,
      eqMode: StateFlow<EqMode>,
      outputRoute: StateFlow<AudioOutputRoute>,
      playbackRate: StateFlow<PlaybackRate>,
      outputModule: StateFlow<AudioOutputModule>,
      dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): SharedPlayerState =
      SharedPlayerStateImpl(
        scope,
        factory,
        eqMode,
        outputRoute,
        playbackRate,
        outputModule,
        dispatcher
      )
  }
}

private class SharedPlayerStateImpl(
  private val scope: CoroutineScope,
  private val factory: EqPresetFactory,
  override val eqMode: StateFlow<EqMode>,
  override val outputRoute: StateFlow<AudioOutputRoute>,
  override val playbackRate: StateFlow<PlaybackRate>,
  override val outputModule: StateFlow<AudioOutputModule>,
  private val dispatcher: CoroutineDispatcher
) : SharedPlayerState {
  private val nonePreset by lazy { factory.nonePreset }
  override val currentPreset by lazy { MutableStateFlow(factory.nonePreset) }
  override var duckedState: DuckAction = DuckAction.None

  override fun setPreferred(
    mediaId: MediaId,
    albumId: AlbumId
  ) {
    if (eqMode.value.isOff()) {
      currentPreset.update { nonePreset }
    } else {
      scope.launch(dispatcher) {
        currentPreset.update {
          factory.getPreferred(mediaId, albumId, outputRoute.value)
            .onFailure { cause -> LOG.e(cause) { it("Error getting preferred preset") } }
            .getOrElse { nonePreset }
        }
      }
    }
  }

  override fun setCurrent(id: EqPresetId) {
    if (eqMode.value.isOn()) {
      scope.launch(dispatcher) {
        factory.getPreset(id)
          .onFailure { cause -> LOG.e(cause) { it("Error setting current preset %s", id) } }
          .onSuccess { preset -> setCurrent(preset) }
      }
    }
  }

  override fun setCurrent(preset: EqPreset) {
    currentPreset.update { preset }
  }

  override suspend fun getAll(): List<EqPresetIdName> {
    return factory.getAllPresets()
  }
}

object NullSharedPlayerState : SharedPlayerState {
  override var duckedState: DuckAction = DuckAction.None
  override val playbackRate = MutableStateFlow(PlaybackRate.NORMAL)
  override val eqMode = MutableStateFlow(EqMode.Off)
  override val outputRoute = MutableStateFlow(AudioOutputRoute.Speaker)
  override val currentPreset = MutableStateFlow(EqPreset.NONE)
  override val outputModule = MutableStateFlow(AudioOutputModule.AudioTrack)
  override fun setPreferred(mediaId: MediaId, albumId: AlbumId) = Unit
  override fun setCurrent(id: EqPresetId) = Unit
  override fun setCurrent(preset: EqPreset) = Unit
  override suspend fun getAll(): List<EqPresetIdName> = emptyList()
}
