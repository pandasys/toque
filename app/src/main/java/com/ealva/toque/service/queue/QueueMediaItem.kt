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

package com.ealva.toque.service.queue

import com.ealva.toque.persist.HasId
import com.ealva.toque.persist.InstanceId
import com.ealva.toque.persist.PersistentId

interface QueueMediaItem : HasId

object NullQueueMediaItem : QueueMediaItem {
  override val id = object : PersistentId {
    override val value: Long = PersistentId.ID_INVALID
  }
  override val instanceId: InstanceId = InstanceId(id.value)
}
