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

package com.ealva.toque.service

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Transformations
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.log._i
import com.ealva.toque.service.controller.NullMediaController
import com.ealva.toque.service.controller.ToqueMediaController

private val LOG by lazyLogger(MediaPlayerServiceConnectionImpl::class)

interface MediaPlayerServiceConnection {
  /**
   * Observe for ToqueMediaController instance. Starts as [NullMediaController],
   * set to the controller instance on successful bind, and then set to [NullMediaController]
   * if the service becomes unavailable
   */
  val mediaController: LiveData<ToqueMediaController>

  fun bind(): Boolean
  fun unbind()

  companion object {
    operator fun invoke(context: Context): MediaPlayerServiceConnection =
      MediaPlayerServiceConnectionImpl(context)
  }
}

/**
 * Encapsulates binding to the media player service
 *
 * Created by eric on 7/31/16.
 */
private class MediaPlayerServiceConnectionImpl(
  private val context: Context
) : MediaPlayerServiceConnection {
  private var serviceConnection: ServiceConnection? = null
  private var isBound: Boolean = false
  private var toqueMediaController: ToqueMediaController = NullMediaController

  override val mediaController: MediatorLiveData<ToqueMediaController> =
    MediatorLiveData<ToqueMediaController>().apply { value = NullMediaController }

  override fun bind(): Boolean {
    serviceConnection = bindToService(context, context.javaClass.name)
    isBound = serviceConnection != null
    return isBound
  }

  override fun unbind() {
    if (isBound) {
      isBound = false
      toqueMediaController = NullMediaController
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
        context.applicationContext,
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
      private lateinit var controllerSource: LiveData<ToqueMediaController>

      override fun onServiceConnected(
        name: ComponentName,
        service: IBinder
      ) {
        LOG._i { it("-->onServiceConnected %s", binderName) }
        val binder = service as MediaPlayerService.MediaServiceBinder
        toqueMediaController = binder.controller

        controllerSource = Transformations.map(toqueMediaController.isActive) { active ->
          if (active == true) toqueMediaController else NullMediaController
        }

        mediaController.addSource(controllerSource) { controller ->
          mediaController.value = controller
        }
        LOG._i { it("<--onServiceConnected %s", binderName) }
      }

      override fun onServiceDisconnected(name: ComponentName) {
        LOG._i { it("-->onServiceDisconnected %s", binderName) }
        mediaController.removeSource(controllerSource)
        toqueMediaController = NullMediaController
        mediaController.value = NullMediaController
        LOG._i { it("<--onServiceDisconnected %s", binderName) }
      }
    }
  }
}
