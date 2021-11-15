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

package com.ealva.toque.db

import com.ealva.toque.common.Filter
import com.ealva.toque.common.toFilter

object DaoCommon {
  const val ESC_CHAR = '\\'

  /**
   * Wrap with LIKE operator '%' wildcards and escape any necessary characters within the string
   */
  fun String.wrapAsFilter(): Filter = trim().let { toWrap ->
    if (toWrap.isEmpty()) toWrap else buildString {
      append('%')
      toWrap.forEach { ch ->
        when (ch) {
          '%', '_', ESC_CHAR -> append(ESC_CHAR)
        }
        append(ch)
      }
      append('%')
    }
  }.toFilter()
}
