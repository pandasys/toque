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

package com.ealva.toque.audio

import android.bluetooth.BluetoothA2dp
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/**
 * Represents current headset state as known by the app. To use this class somewhere in
 * Service.onCreate():
 * ```kotlin
 * onBroadcast(audioOutputState.intentFilter) { intent ->
 *   if (intent != null) {
 *     audioOutputState.handleBroadcastIntent(
 *       intent,
 *       isInitialStickyBroadcast,
 *       ::handleHeadphoneConnect,
 *       ::handleBluetoothConnecting,
 *       ::handleHeadphoneDisconnect
 *     )
 *   }
 * }
 * ```
 */
interface AudioOutputState {
  val output: AudioOutputRoute

  /**
   * This IntentFilter be to used to receive the broadcast. See Broadcast.onBroadcast()
   */
  val intentFilter: IntentFilter

  val headsetIsConnected: Boolean

  val bluetoothIsConnected: Boolean

  fun handleBroadcastIntent(
    intent: Intent,
    isInitialStickyBroadcast: Boolean,
    handleHeadphoneConnect: (isBluetooth: Boolean) -> Unit,
    handleBluetoothConnecting: () -> Unit,
    handleHeadphoneDisconnect: () -> Unit
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
  private var _headsetConnected: Boolean = audioManager.wiredHeadsetConnected
  private var _bluetoothConnected: Boolean = audioManager.bluetoothIsOn

  override val intentFilter: IntentFilter
    get() = IntentFilter(AudioManager.ACTION_HEADSET_PLUG).apply {
      addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
    }

  override fun handleBroadcastIntent(
    intent: Intent,
    isInitialStickyBroadcast: Boolean,
    handleHeadphoneConnect: (isBluetooth: Boolean) -> Unit,
    handleBluetoothConnecting: () -> Unit,
    handleHeadphoneDisconnect: () -> Unit
  ) {
    if (!isInitialStickyBroadcast) {
      when (intent.action) {
        BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
          when (intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, BluetoothA2dp.STATE_DISCONNECTED)) {
            BluetoothA2dp.STATE_CONNECTED -> {
              _bluetoothConnected = true
              handleHeadphoneConnect(true)
            }
            BluetoothA2dp.STATE_CONNECTING -> {
              handleBluetoothConnecting()
            }
            BluetoothA2dp.STATE_DISCONNECTED -> {
              // possible to receive if connecting failed without having connection
              if (bluetoothIsConnected) {
                _bluetoothConnected = false
                handleHeadphoneDisconnect()
              }
            }
          }
        }
        AudioManager.ACTION_HEADSET_PLUG -> {
          val plugged = intent.getIntExtra("state", 0) > 0
          if (headsetIsConnected != plugged) { // if state different from what we know
            _headsetConnected = plugged
            if (plugged) handleHeadphoneConnect(false) else handleHeadphoneDisconnect()
          }
        }
      }
    }
  }

  override val headsetIsConnected: Boolean
    get() = _headsetConnected

  override val bluetoothIsConnected: Boolean
    get() = _bluetoothConnected

  // Wired headset and bluetooth could be connected at the same time. Wired headset takes precedence
  override val output: AudioOutputRoute
    get() {
      if (_headsetConnected) return AudioOutputRoute.HeadphoneJack
      if (_bluetoothConnected) return AudioOutputRoute.Bluetooth
      return AudioOutputRoute.Speaker
    }

  override fun toString(): String =
    "$PREFIX${_bluetoothConnected.connectStr()} headset=${_headsetConnected.connectStr()})"
}

private fun Boolean.connectStr() = if (this) "Connected" else "Unconnected"
private const val PREFIX = "AudioOutputStateImp(bluetooth="
