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

package com.ealva.toque.service

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.ealvalog.w
import com.ealva.toque.log._i
import com.ealva.toque.service.controller.NullMediaController
import com.ealva.toque.service.controller.ToqueMediaController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val LOG by lazyLogger(MediaPlayerServiceConnectionImpl::class)

interface MediaPlayerServiceConnection {
  /**
   * Starts as [NullMediaController], set to the controller instance on successful bind, and then
   * set to [NullMediaController] if the service becomes unavailable
   */
  val mediaController: StateFlow<ToqueMediaController>

  val isBound: Boolean
  val isNotBound: Boolean

  fun bind(): Boolean
  fun unbind()

  companion object {
    operator fun invoke(context: Context): MediaPlayerServiceConnection =
      MediaPlayerServiceConnectionImpl(context)
  }
}

/**
 * Encapsulates binding to the media player service
 */
private class MediaPlayerServiceConnectionImpl(
  private val context: Context
) : MediaPlayerServiceConnection {
  private var serviceConnection: ServiceConnection? = null
  override var isBound: Boolean = false
  override val isNotBound: Boolean
    get() = !isBound

  override val mediaController = MutableStateFlow<ToqueMediaController>(NullMediaController)

  override fun bind(): Boolean {
    if (!isBound) {
      serviceConnection = bindToService(context, context.javaClass.name)
      isBound = serviceConnection != null
    }
    return isBound
  }

  override fun unbind() {
    if (isBound) {
      isBound = false
      mediaController.value = NullMediaController
      serviceConnection?.let {
        context.unbindService(it)
      }
    }
    serviceConnection = null
  }

  private fun bindToService(
    context: Context,
    binderName: String
  ): ServiceConnection? {
    val connection = makeServiceConnection(binderName)
    isBound = context.bindService(
      MediaPlayerService.makeStartIntent(
        context,
        MediaPlayerService.Action.None,
        null
      ),
      connection,
      Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT
    )

    return if (isBound) connection else null
  }

  private fun makeServiceConnection(binderName: String): ServiceConnection {
    return object : ServiceConnection {
      override fun onServiceConnected(
        name: ComponentName,
        service: IBinder
      ) {
        LOG._i { it("onServiceConnected name:%s %s", name, binderName) }
        val binder = service as MediaPlayerService.MediaServiceBinder
        mediaController.value = binder.controller
      }

      override fun onServiceDisconnected(name: ComponentName) {
        LOG._i { it("onServiceDisconnected name:%s %s", name, binderName) }
        mediaController.value = NullMediaController
      }

      override fun onBindingDied(name: ComponentName) {
        LOG.w { it("onBindingDied name:%s %s", name, binderName) }
      }

      override fun onNullBinding(name: ComponentName) {
        LOG.w { it("onNullBinding name:%s %s", name, binderName) }
      }
    }
  }
}
