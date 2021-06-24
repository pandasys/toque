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

package com.ealva.toque.service.queue

import com.ealva.toque.common.Millis
import com.ealva.toque.common.Title
import com.ealva.toque.common.toTitle
import com.ealva.toque.persist.HasId
import com.ealva.toque.persist.PersistentId

interface QueueMediaItem : HasId {
  val isValid: Boolean

  val title: Title

  val duration: Millis

  val position: Millis

  val isPlaying: Boolean
}

object NullQueueMediaItem : QueueMediaItem {
  override val id = object : PersistentId {
    override val value: Long = PersistentId.ID_INVALID
  }

  override val isValid: Boolean = false
  override val title: Title = "NullItem".toTitle()
  override val duration: Millis = Millis.ZERO
  override val position: Millis = Millis.ZERO
  override val isPlaying: Boolean = false
  override val instanceId: Long = -1
}
