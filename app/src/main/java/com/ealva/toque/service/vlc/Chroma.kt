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

package com.ealva.toque.service.vlc

import com.ealva.toque.persist.HasConstId

enum class Chroma(override val id: Int, private val value: String) : HasConstId {
  I420(1, "I420"),
  I411(2, "I411"),
  I422(3, "I422"),
  YUYV(4, "YUYV"),
  UYVY(5, "UYVY"),
  RV16(6, "RV16"),
  RV24(7, "RV24"),
  RV32(8, "RV32"),
  I42N(9, "I42N"),
  I41N(10, "I41N"),
  GRAW(11, "GRAW");

  override fun toString(): String = value

  companion object {
    val DEFAULT = RV16
  }
}
