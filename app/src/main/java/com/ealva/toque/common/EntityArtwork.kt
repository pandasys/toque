/*
 * Copyright 2022 Eric A. Snell
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

package com.ealva.toque.common

import android.net.Uri
import com.ealva.toque.file.elseIfEmpty

interface EntityArtwork {
  val remoteArtwork: Uri
  val localArtwork: Uri

  companion object {
    private data class ArtworkData(
      override val remoteArtwork: Uri,
      override val localArtwork: Uri
    ) : EntityArtwork

    operator fun invoke(remote: Uri, local: Uri): EntityArtwork = ArtworkData(remote, local)
  }
}

inline val EntityArtwork.preferredArt: Uri get() = localArtwork.elseIfEmpty(remoteArtwork)
