/*
 * Copyright 2020 eAlva.com
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

package com.ealva.toque.persist

/**
 * A HasConstId instance has an int identifier that must never change. It can be persisted and
 * queried across app instances without fear of it changing. A typical use will be in an enum so it
 * may be persisted without concern for name change or declaration position ([Enum.name] and
 * [Enum.ordinal] respectively)
 */
interface HasConstId {
  /**
   * Don't reuse this ID for other purposes - it is used for lookup (reification). Prefer
   * IDs starting at 1 and incrementing by 1 as this leads to search and space
   * efficiency in other areas
   */
  val id: Int
}
