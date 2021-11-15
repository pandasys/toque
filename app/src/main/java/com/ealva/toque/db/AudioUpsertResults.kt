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

import com.ealva.toque.persist.MediaId
import com.ealva.toque.persist.isValid

interface AudioUpsertResults {
  /** Emit event that media was created */
  fun mediaCreated(mediaId: MediaId)
  /** Emit event that media was updated */
  fun mediaUpdated(mediaId: MediaId)
  /**
   * Used by media related DAOs to always emit if Media was created or updated. For example, if
   * an AlbumDao creates or updates an Album during upsert, it should always emit.
   */
  fun alwaysEmit(emit: () -> Unit)
  /**
   * Used by media related DAOs to emit if Media was created. For example, if Album data is neither
   * created nor updated during a media upsert, it would not emit based on that criteria. However,
   * if the Album's data is unchanged and a media item is created, the AlbumDao should emit an
   * "updated" event since media was added to the album. The album data itself didn't change, but
   * the fact that it contains a new piece of media is an update.
   */
  fun emitIfMediaCreated(emit: () -> Unit)
  /**
   * Called when the transaction is committed. If media was created or deleted, this function emits
   * an [AudioDaoEvent] and will also cause other emits depending on media create or update. See
   * [alwaysEmit] and [emitIfMediaCreated] for details.
   */
  fun onCommit()

  companion object {
    operator fun invoke(audioEventEmitter: (AudioDaoEvent) -> Unit): AudioUpsertResults =
      AudioUpsertResultsImpl(audioEventEmitter)
  }
}

private class AudioUpsertResultsImpl(
  private val audioEventEmitter: (AudioDaoEvent) -> Unit
) : AudioUpsertResults {
  private var createdMedia: MediaId = MediaId.INVALID
  override fun mediaCreated(mediaId: MediaId) {
    createdMedia = mediaId
  }

  private var updatedMedia: MediaId = MediaId.INVALID
  override fun mediaUpdated(mediaId: MediaId) {
    updatedMedia = mediaId
  }

  private val alwaysEmit: MutableList<() -> Unit> = mutableListOf()
  override fun alwaysEmit(emit: () -> Unit) {
    alwaysEmit += emit
  }

  private val emitIfCreated: MutableList<() -> Unit> = mutableListOf()
  override fun emitIfMediaCreated(emit: () -> Unit) {
    emitIfCreated += emit
  }

  override fun onCommit() {
    if (createdMedia.isValid) {
      audioEventEmitter(AudioDaoEvent.MediaCreated(createdMedia))
      alwaysEmit.forEach { it() }
      emitIfCreated.forEach { it() }
    } else if (updatedMedia.isValid) {
      audioEventEmitter(AudioDaoEvent.MediaUpdated(createdMedia))
      alwaysEmit.forEach { it() }
    }
  }
}
