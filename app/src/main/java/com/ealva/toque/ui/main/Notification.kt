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

package com.ealva.toque.ui.main

import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.SnackbarResult
import androidx.compose.runtime.Immutable
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.log._e
import com.ealva.toque.ui.main.Notification.Action.Companion.NoAction
import java.util.concurrent.atomic.AtomicInteger

@Suppress("unused")
private val LOG by lazyLogger(Notification::class)

@Immutable
interface Notification {
  /**
   * Action to be displayed as the action of a snackbar. The [label] is to be displayed to the
   * user. [action] is called when the user selects the action button, else [expired] is called when
   * the snackbar is dismissed
   */
  interface Action {
    val label: String?
    fun action()
    fun expired()

    companion object {
      val NoAction = object : Action {
        override val label: String? = null
        override fun action() = Unit
        override fun expired() = Unit
      }
    }
  }

  val isIndefiniteLength: Boolean

  /**
   * Action to be performed. If this is [Action.NoAction] or the [Action.label] is null, then
   * [Action.action] will not be called, but [Action.expired] will be called. Either [Action.action]
   * or [Action.expired] will always be called.
   */
  val action: Action

  /**
   * Show this Notification as a snackbar in [snackbarHostState]
   */
  suspend fun showSnackbar(snackbarHostState: SnackbarHostState)

  fun dismiss()

  companion object {
    @Immutable
    private data class NotificationData(
      private val msg: String,
      private val duration: SnackbarDuration,
      override val action: Action,
      /** version is used to ensure the NotificationData compares as unique */
      private val version: Int
    ) : Notification {
      private var hasBeenDismissed = false
      override val isIndefiniteLength: Boolean
        get() = duration === SnackbarDuration.Indefinite

      private var hostState: SnackbarHostState? = null

      override suspend fun showSnackbar(snackbarHostState: SnackbarHostState) {
        hostState = snackbarHostState
        when (
          snackbarHostState.showSnackbar(
            message = msg,
            actionLabel = action.label,
            duration = duration
          )
        ) {
          SnackbarResult.ActionPerformed -> {
            LOG._e { it("action performed") }
            hasBeenDismissed = true
            action.action()
          }
          SnackbarResult.Dismissed -> {
            LOG._e { it("action expired") }
            hasBeenDismissed = true
            action.expired()
          }
        }
      }

      override fun dismiss() {
        if (!hasBeenDismissed) {
          hasBeenDismissed = true
          hostState?.currentSnackbarData?.dismiss()
        }
      }
    }

    /**
     * Create a Notification with [msg] and a [duration] that defaults to [SnackbarDuration.Short]
     */
    operator fun invoke(
      msg: String,
      duration: SnackbarDuration = SnackbarDuration.Short
    ): Notification = NotificationData(msg, duration, NoAction, nextVersion.getAndIncrement())

    /**
     * Create a Notification with [msg], [action], and [duration] that defaults to
     * [SnackbarDuration.Long]. Often used to display an undoable operation to the user. If the
     * user "must" respond to the action, use [SnackbarDuration.Indefinite], though this might
     * be a poor user experience - probably prefer a dialog in "must" respond situations.
     */
    operator fun invoke(
      msg: String,
      action: Action,
      duration: SnackbarDuration = SnackbarDuration.Long
    ): Notification = NotificationData(msg, duration, action, nextVersion.getAndIncrement())
  }
}

private val nextVersion = AtomicInteger(0)
