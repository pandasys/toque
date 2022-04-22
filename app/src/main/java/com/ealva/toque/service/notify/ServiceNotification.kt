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

package com.ealva.toque.service.notify

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * A notification which may be displayed to the user. Instances should be treated as events and
 * not retained in something like a StateFlow.
 */
interface ServiceNotification {
  /**
   * Possible action to be taken when notification is received. Typically, this would be an "undo"
   * of some type.
   */
  interface Action {
    val label: String?
    fun action()
    fun expired()

    companion object {
      val NoAction = object : Action {
        override val label: String = ""
        override fun action() = Unit
        override fun expired() = Unit
      }
    }
  }

  @Suppress("unused")
  enum class NotificationDuration {
    Notify,
    Important,
    WaitForReply
  }

  /** Message to display to the user */
  val msg: String

  /** How long should this info be displayed */
  val duration: NotificationDuration

  /**
   * Action to be performed. If this is [Action.NoAction] or the [Action.label] is null, then
   * [Action.action] will not be invoked.
   */
  val action: Action

  /**
   * The instance of the notification - a counter so message with same data is unique. eg. add 10
   * songs and display a snack, then add 10 more and the snack should display again, though data is
   * exactly the same, but version will be different so notification is not equal to previous.
   */
  val version: Int

  val type: UUID

  companion object {
    private data class NotificationData(
      override val msg: String,
      override val duration: NotificationDuration,
      override val action: Action,
      override val version: Int,
      override val type: UUID
    ) : ServiceNotification

    operator fun invoke(
      msg: String,
      duration: NotificationDuration = NotificationDuration.Notify,
      type: UUID = UUID.randomUUID()
    ): ServiceNotification =
      NotificationData(
        msg,
        duration,
        Action.NoAction,
        nextVersion.getAndIncrement(),
        type
      )

    operator fun invoke(
      msg: String,
      action: Action,
      duration: NotificationDuration = NotificationDuration.Important,
      type: UUID = UUID.randomUUID()
    ): ServiceNotification = NotificationData(
      msg,
      duration,
      action,
      nextVersion.getAndIncrement(),
      type
    )
  }
}

private val nextVersion = AtomicInteger(0)
