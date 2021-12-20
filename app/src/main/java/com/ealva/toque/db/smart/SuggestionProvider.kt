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

package com.ealva.toque.db.smart

import com.ealva.welite.db.table.Column

interface SuggestionProvider {
  suspend fun getSuggestions(): List<String>
}

interface SuggestionProviderFactory {
  /**
   * Return a [SuggestionProvider] based on identity equals of [column]. If not found, the
   * [EmptySuggestionProvider] is returned
   */
  fun getProvider(column: Column<*>): SuggestionProvider
}

object EmptySuggestionProvider : SuggestionProvider {
  override suspend fun getSuggestions(): List<String> {
    return emptyList()
  }
}
