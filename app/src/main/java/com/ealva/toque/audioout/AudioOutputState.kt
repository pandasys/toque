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

package com.ealva.toque.audioout

import android.bluetooth.BluetoothA2dp
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.lifecycle.LifecycleOwner
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.android.content.onBroadcast
import com.ealva.toque.audioout.AudioOutputState.Event.BluetoothConnected
import com.ealva.toque.audioout.AudioOutputState.Event.BluetoothDisconnected
import com.ealva.toque.audioout.AudioOutputState.Event.HeadsetConnected
import com.ealva.toque.audioout.AudioOutputState.Event.HeadsetDisconnected
import com.ealva.toque.log._e
import com.ealva.toque.log._i
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val LOG by lazyLogger(AudioOutputState::class)

/**
 * Call this function from Service.onCreate(), the Service must also be a LifecycleOwner, and
 * broadcasts will be appropriately forwarded and the AudioOutputState flows will emit
 */
fun <T> T.handleAudioOutputStateBroadcasts(
  audioOutputState: AudioOutputState
) where T : Context, T : LifecycleOwner {
  onBroadcast(audioOutputState.intentFilter) { intent ->
    intent?.let { audioOutputState.handleBroadcastIntent(it, isInitialStickyBroadcast) }
  }
}

/**
 * Represents current headset state as known by the app. To use this class, somewhere in
 * Service.onCreate() call [handleAudioOutputStateBroadcasts], passing an instance of
 * AudioOutputState:
 * ```kotlin
 * handleAudioOutputStateBroadcasts(audioOutputState)
 * ```
 *
 * The ```onBroadcast``` mentioned above is a lifecycle aware broadcast listener, currently
 * found in Broadcast.kt
 */
interface AudioOutputState {
  /**
   * This state indicates current audio routing. Note that a wired device and bluetooth may be
   * connected at the same time, but the wired device has precedence. When the output route changes
   * the current EQ preset may need to be changed.
   */
  val output: StateFlow<AudioOutputRoute>

  enum class Event {
    /** primordial */
    None,
    /** A bluetooth headset or speaker device connected */
    BluetoothConnected,
    /** A bluetooth headset or speaker device disconnected */
    BluetoothDisconnected,
    /** A wired headset or speaker was connected */
    HeadsetConnected,
    /** A wired headset or speaker was disconnected */
    HeadsetDisconnected
  }

  /**
   * Listen for events when an action might be taken on connect/disconnect. For example, I like my
   * media player to automatically start playing musing when I plug in my headphone jack in the car
   * so I don't have to unlock my phone. Could do that same for bluetooth. Might also want a slight
   * delay and/or volume ramp up.
   */
  val stateChange: StateFlow<Event>

  /**
   * This IntentFilter be to used to receive the broadcast. See Broadcast.onBroadcast()
   */
  val intentFilter: IntentFilter

  /**
   * This is provided because bluetooth may be connected at the same time, though this takes
   * precedence
   */
  val headsetIsConnected: Boolean

  /**
   * This is provided because bluetooth may be connected at the same time, though this takes
   * precedence
   */
  val bluetoothIsConnected: Boolean

  fun handleBroadcastIntent(
    intent: Intent,
    isInitialStickyBroadcast: Boolean
  )

  companion object {
    operator fun invoke(audioManager: AudioManager): AudioOutputState {
      return AudioOutputStateImpl(audioManager)
    }
  }
}

@Suppress("DEPRECATION")
val AudioManager.bluetoothIsOn: Boolean
  get() {
    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      isBluetoothA2dpOn
    } else {
      getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
    }
  }

@Suppress("DEPRECATION")
val AudioManager.wiredHeadsetConnected: Boolean
  get() {
    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) isWiredHeadsetOn
    else getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
      when (it.type) {
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> true
        else -> false
      }
    }
  }

/**
 * Wrapper that keeps audio output state - bluetooth, headphone etc
 */
private class AudioOutputStateImpl(audioManager: AudioManager) : AudioOutputState {
  // Wired headset and bluetooth could be connected at the same time. Wired headset takes precedence
  private var _headsetConnected: Boolean = audioManager.wiredHeadsetConnected
  private var _bluetoothConnected: Boolean = audioManager.bluetoothIsOn

  override val output = MutableStateFlow(getOutputRoute())
  override val stateChange = MutableStateFlow(AudioOutputState.Event.None)

  override val intentFilter: IntentFilter
    get() = IntentFilter(AudioManager.ACTION_HEADSET_PLUG).apply {
      addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
    }

  override fun handleBroadcastIntent(
    intent: Intent,
    isInitialStickyBroadcast: Boolean
  ) {
    if (!isInitialStickyBroadcast) {
      when (intent.action) {
        BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
          when (intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, BluetoothA2dp.STATE_DISCONNECTED)) {
            BluetoothA2dp.STATE_CONNECTED -> {
              LOG._e { it("Bluetooth connected") }
              _bluetoothConnected = true
              emitEvent(BluetoothConnected)
            }
            //BluetoothA2dp.STATE_CONNECTING -> {
            //}
            BluetoothA2dp.STATE_DISCONNECTED -> {
              // possible to receive if connecting failed without having connection
              if (bluetoothIsConnected) {
                _bluetoothConnected = false
                emitEvent(BluetoothDisconnected)
              }
            }
          }
        }
        AudioManager.ACTION_HEADSET_PLUG -> {
          val plugged = intent.getIntExtra("state", 0) > 0
          if (headsetIsConnected != plugged) { // if state different from what we know
            _headsetConnected = plugged
            if (plugged) emitEvent(HeadsetConnected) else emitEvent(HeadsetDisconnected)
          }
        }
      }
    }
  }

  private fun emitEvent(event: AudioOutputState.Event) {
    LOG._i { it("AudioOutput state changed: %s", event) }
    stateChange.value = event

    output.value = getOutputRoute()
  }

  private fun getOutputRoute() = when {
    _headsetConnected -> AudioOutputRoute.HeadphoneJack
    _bluetoothConnected -> AudioOutputRoute.Bluetooth
    else -> AudioOutputRoute.Speaker
  }

  override val headsetIsConnected: Boolean
    get() = _headsetConnected

  override val bluetoothIsConnected: Boolean
    get() = _bluetoothConnected

  override fun toString(): String =
    "$PREFIX${_bluetoothConnected.connectStr()} headset=${_headsetConnected.connectStr()})"
}

private fun Boolean.connectStr() = if (this) "Connected" else "Unconnected"
private const val PREFIX = "AudioOutputStateImp(bluetooth="
