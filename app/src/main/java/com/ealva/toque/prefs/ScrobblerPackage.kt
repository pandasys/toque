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

package com.ealva.toque.prefs

import androidx.annotation.StringRes
import com.ealva.toque.R
import com.ealva.toque.common.PackageName
import com.ealva.toque.common.toPackageName
import com.ealva.toque.persist.HasConstId
import com.ealva.toque.res.HasDescription

/**
 * Don't change any enum instance [id] as it is persisted in the app preferences.
 */
enum class ScrobblerPackage(
  override val id: Int,
  val packageName: PackageName,
  @StringRes override val stringRes: Int
) : HasConstId, HasDescription {
  None(0, PackageName.NONE, R.string.None),
  LastFm(1, "com.adam.aslfms".toPackageName(), R.string.LastFm),
  SimpleLastFm(2, "com.adam.aslfms".toPackageName(), R.string.SimpleLastFm);
}
