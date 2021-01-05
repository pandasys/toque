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

package com.ealva.toque.android.content

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Listen for a Broadcast Intent based on [filter] and invoke [handler] with the receiver type
 * [BroadcastReceiver] and parameters [scope][CoroutineScope], [context][Context], and
 * [intent][Intent]. This, [T], must be both a [Context] and [LifecycleOwner], such as
 * a androidx.lifecycle.LifecycleService or androidx.fragment.app.FragmentActivity.
 *
 * A [CoroutineContext], default [EmptyCoroutineContext], and [CoroutineStart], default
 * [CoroutineStart.UNDISPATCHED], may be passed to control the coroutine in which the lambda
 * [handler] runs. By default the coroutine is undispatched unless/until is suspends.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> T.onBroadcast(
  filter: IntentFilter,
  coroutineContext: CoroutineContext = EmptyCoroutineContext,
  coroutineStart: CoroutineStart = CoroutineStart.UNDISPATCHED,
  handler: suspend BroadcastReceiver.(intent: Intent?) -> Unit
) where T : Context, T : LifecycleOwner {
  registerReceiver(
    LifecycleAwareBroadcastReceiver(
      this,
      this,
      coroutineContext,
      coroutineStart,
      handler
    ),
    filter
  )
}

private class LifecycleAwareBroadcastReceiver(
  private val context: Context,
  private val lifecycleOwner: LifecycleOwner,
  private val coroutineContext: CoroutineContext,
  private val coroutineStart: CoroutineStart,
  private val handler: suspend BroadcastReceiver.(intent: Intent?) -> Unit
) : BroadcastReceiver(), DefaultLifecycleObserver {

  init {
    lifecycleOwner.lifecycle.addObserver(this)
  }

  override fun onReceive(context: Context?, intent: Intent?) {
    lifecycleOwner.lifecycleScope.launch(coroutineContext, coroutineStart) {
      handler(intent)
    }
  }

  override fun onDestroy(@Suppress("UNUSED_PARAMETER") lifecycleOwner: LifecycleOwner) {
    context.unregisterReceiver(this)
  }
}
