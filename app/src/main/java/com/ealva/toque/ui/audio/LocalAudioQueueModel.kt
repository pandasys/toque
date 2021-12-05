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

package com.ealva.toque.ui.audio

import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.common.PlaylistName
import com.ealva.toque.common.ShuffleMedia
import com.ealva.toque.common.fetch
import com.ealva.toque.common.fetchPlural
import com.ealva.toque.common.isValidAndUnique
import com.ealva.toque.db.CategoryMediaList
import com.ealva.toque.db.PlaylistDao
import com.ealva.toque.db.PlaylistIdName
import com.ealva.toque.log._e
import com.ealva.toque.persist.MediaIdList
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.prefs.PlayUpNextAction
import com.ealva.toque.service.audio.LocalAudioQueue
import com.ealva.toque.service.audio.NullLocalAudioQueue
import com.ealva.toque.service.audio.TransitionType.Manual
import com.ealva.toque.service.queue.ClearQueue
import com.ealva.toque.service.queue.PlayNow
import com.ealva.toque.service.queue.PlayableMediaQueue
import com.ealva.toque.ui.audio.LocalAudioQueueModel.PromptResult
import com.ealva.toque.ui.common.DialogPrompt
import com.ealva.toque.ui.library.PlayUpNextPrompt
import com.ealva.toque.ui.main.MainViewModel
import com.ealva.toque.ui.main.Notification
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.zhuinden.simplestack.ScopedServices
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.regex.Pattern

private val LOG by lazyLogger(LocalAudioQueueModel::class)

interface LocalAudioQueueModel {
  val localAudioQueue: StateFlow<LocalAudioQueue>
  val playUpNextAction: StateFlow<PlayUpNextAction>
  val queueSize: Int

  fun emitNotification(notification: Notification)

  suspend fun play(mediaList: CategoryMediaList): PromptResult
  suspend fun shuffle(mediaList: CategoryMediaList): PromptResult
  fun playNext(mediaList: CategoryMediaList)
  fun addToUpNext(categoryMediaList: CategoryMediaList)

  enum class PromptResult {
    Dismissed,
    Executed;

    inline val wasExecuted: Boolean get() = this == Executed

    @Suppress("unused")
    inline val wasDismissed: Boolean
      get() = this == Dismissed
  }

  suspend fun addToPlaylist(mediaIdList: MediaIdList): PromptResult

  fun showPrompt(prompt: DialogPrompt)
  fun clearPrompt()

  companion object {
    operator fun invoke(
      mainViewModel: MainViewModel,
      appPrefsSingleton: AppPrefsSingleton,
      playlistDao: PlaylistDao,
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): LocalAudioQueueModel =
      LocalAudioQueueModelImpl(
        mainViewModel,
        appPrefsSingleton,
        playlistDao,
        dispatcher
      )
  }
}

private data class PrefsHolder(val appPrefs: AppPrefs? = null)

class LocalAudioQueueModelImpl(
  private val mainViewModel: MainViewModel,
  private val appPrefsSingleton: AppPrefsSingleton,
  private val playlistDao: PlaylistDao,
  private val dispatcher: CoroutineDispatcher
) : LocalAudioQueueModel, ScopedServices.Registered {
  private lateinit var scope: CoroutineScope

  private val prefsHolder = MutableStateFlow(PrefsHolder())

  override val localAudioQueue = MutableStateFlow<LocalAudioQueue>(NullLocalAudioQueue)
  override val playUpNextAction = MutableStateFlow(PlayUpNextAction.Prompt)
  override var queueSize: Int = 0

  override fun emitNotification(notification: Notification) {
    mainViewModel.notify(notification)
  }

  override suspend fun play(mediaList: CategoryMediaList): PromptResult =
    playOrPrompt(mediaList, ShuffleMedia(false))

  override suspend fun shuffle(mediaList: CategoryMediaList): PromptResult =
    playOrPrompt(mediaList, ShuffleMedia(true))

  private suspend fun playOrPrompt(
    mediaList: CategoryMediaList,
    shuffle: ShuffleMedia
  ): PromptResult {
    val result = CompletableDeferred<PromptResult>()
    val idList = if (shuffle.value) mediaList.shuffled() else mediaList
    fun onDismiss() {
      clearPrompt()
      result.complete(PromptResult.Dismissed)
    }

    fun playNext(
      mediaList: CategoryMediaList,
      clearQueue: ClearQueue,
      result: CompletableDeferred<PromptResult>
    ) {
      clearPrompt()
      try {
        scope.launch {
          doPlayNext(mediaList, clearQueue, PlayNow(true))
          result.complete(PromptResult.Executed)
        }
      } catch (e: Exception) {
        result.completeExceptionally(e)
      }
    }

    if (playUpNextAction.value.shouldPrompt) {
      showPrompt(
        DialogPrompt(
          prompt = {
            PlayUpNextPrompt(
              itemCount = idList.size,
              queueSize = queueSize,
              onDismiss = ::onDismiss,
              onClear = { playNext(idList, ClearQueue(true), result) },
              onDoNotClear = { playNext(idList, ClearQueue(false), result) },
              onCancel = ::onDismiss
            )
          }
        )
      )
    } else {
      playNext(idList, playUpNextAction.value.clearUpNext, result)
      result.complete(PromptResult.Executed)
    }
    return result.await()
  }

  override fun playNext(mediaList: CategoryMediaList) {
    scope.launch { doPlayNext(mediaList, ClearQueue(false), PlayNow(false)) }
  }

  private suspend fun doPlayNext(
    mediaList: CategoryMediaList,
    clear: ClearQueue,
    playNow: PlayNow
  ) {
    val size = mediaList.size
    mainViewModel.notify(
      when (val result = localAudioQueue.value.playNext(mediaList, clear, playNow, Manual)) {
        is Ok -> {
          val plural = if (appPrefsSingleton.instance().allowDuplicates() || clear()) {
            R.plurals.AddedToUpNextNewSize
          } else {
            R.plurals.AddedToUpNextNewSizeLessDuplicates
          }
          Notification(fetchPlural(plural, size, result.value.value))
        }
        is Err -> Notification(fetchPlural(R.plurals.FailedAddingToUpNext, size))
      }
    )
  }

  override fun addToUpNext(categoryMediaList: CategoryMediaList) {
    scope.launch {
      val quantity = categoryMediaList.size
      mainViewModel.notify(
        when (val result = localAudioQueue.value.addToUpNext(categoryMediaList)) {
          is Ok -> Notification(
            fetchPlural(R.plurals.AddedToUpNextNewSize, quantity, result.value.value)
          )
          is Err -> Notification(fetchPlural(R.plurals.FailedAddingToUpNext, quantity))
        }
      )
    }
  }

  override fun showPrompt(prompt: DialogPrompt) {
    mainViewModel.prompt(prompt)
  }

  override fun clearPrompt() {
    showPrompt(DialogPrompt.None)
  }

  override suspend fun addToPlaylist(mediaIdList: MediaIdList): PromptResult {
    val deferred = CompletableDeferred<PromptResult>()
    // get list of user playlists. If Empty return CreatePlaylist
    val playlists: List<PlaylistIdName> =
      when (val result = playlistDao.getUserPlaylistNames()) {
        is Ok -> result.value
        is Err -> {
          LOG.e { it("Error retrieving user playlists. %s", result.error) }
          emptyList()
        }
      }

    fun onDismiss() {
      clearPrompt()
      deferred.complete(PromptResult.Dismissed)
    }

    fun addToPlaylist(mediaIdList: MediaIdList, playlistIdName: PlaylistIdName) {
      clearPrompt()
      addAudioToPlaylist(mediaIdList, playlistIdName)
      deferred.complete(PromptResult.Executed)
    }

    fun createPlaylist(mediaIdList: MediaIdList, playlistName: PlaylistName) {
      clearPrompt()
      createPlaylistWithAudio(mediaIdList, playlistName)
      deferred.complete(PromptResult.Executed)
    }

    fun showCreatePrompt() {
      showPrompt(
        DialogPrompt(
          prompt = {
            CreatePlaylistPrompt(
              suggestedName = getSuggestedNewPlaylistName(playlists),
              checkValidName = { isValidName(playlists, it) },
              dismiss = ::onDismiss,
              createPlaylist = { playlistName -> createPlaylist(mediaIdList, playlistName) }
            )
          }
        )
      )
    }

    if (playlists.isEmpty()) {
      showCreatePrompt()
    } else {
      showPrompt(
        DialogPrompt(
          prompt = {
            SelectPlaylistPrompt(
              playlists = playlists,
              onDismiss = ::onDismiss,
              listSelected = { addToPlaylist(mediaIdList, it) },
              createPlaylist = { showCreatePrompt() }
            )
          }
        )
      )
    }
    return deferred.await()
  }

  private fun getSuggestedNewPlaylistName(playlists: List<PlaylistIdName>): String =
    getSuggestedName(playlists.asSequence().map { it.name.value })

  private fun isValidName(playlists: List<PlaylistIdName>, name: PlaylistName): Boolean =
    name.isValidAndUnique(playlists.asSequence().map { it.name })

  private fun addAudioToPlaylist(mediaIdList: MediaIdList, playlistIdName: PlaylistIdName) {
    scope.launch {
      when (val result = playlistDao.addToUserPlaylist(playlistIdName.id, mediaIdList)) {
        is Ok -> LOG._e { it("Added %d to %s", result.value, playlistIdName.name.value) }
        is Err -> LOG.e { it("Error adding %s. %s", playlistIdName.name.value, result.error) }
      }
    }
  }

  private fun createPlaylistWithAudio(mediaIdList: MediaIdList, playlistName: PlaylistName) {
    scope.launch {
      when (val result = playlistDao.createUserPlaylist(playlistName, mediaIdList)) {
        is Ok -> LOG._e { it("created %s", result.value) }
        is Err -> LOG.e { it("Error creating %s. %s", playlistName, result.error) }
      }
    }
  }

  override fun onServiceRegistered() {
    scope = CoroutineScope(SupervisorJob() + dispatcher)
    scope.launch {
      val appPrefs = appPrefsSingleton.instance()
      prefsHolder.value = PrefsHolder(appPrefs)
      appPrefs.playUpNextAction
        .asFlow()
        .onEach { playUpNextAction.value = it }
        .launchIn(scope)
    }

    mainViewModel.currentQueue
      .onEach { queue -> handleQueueChange(queue) }
      .launchIn(scope)
  }

  private fun handleQueueChange(queue: PlayableMediaQueue<*>) = when (queue) {
    is NullLocalAudioQueue -> queueInactive()
    is LocalAudioQueue -> queueActive(queue)
    else -> queueInactive()
  }

  private fun queueInactive() {
    localAudioQueue.value = NullLocalAudioQueue
  }

  private fun queueActive(queue: LocalAudioQueue) {
    localAudioQueue.value = queue
    queue.queueState
      .onEach { queueSize = it.queue.size }
      .launchIn(scope)

    queue.notification
      .onEach { serviceNotification -> mainViewModel.notify(serviceNotification) }
      .launchIn(scope)
  }

  override fun onServiceUnregistered() {
    scope.cancel()
  }
}

private fun getSuggestedName(
  existingNames: Sequence<String>,
  start: String = fetch(R.string.Playlist),
): String {
  var suffix = 0
  val prefix = if (start.endsWith(' ')) start else "$start "
  existingNames.forEach { name ->
    if (name.startsWith(prefix)) {
      suffix = suffix.coerceAtLeast(prefix.getNumAtEndOfString(name))
    }
  }
  return prefix + (suffix + 1)
}

private fun String.getNumAtEndOfString(input: String): Int {
  val lastIntPattern = Pattern.compile("${this}([0-9]+)$")
  val matcher = lastIntPattern.matcher(input)
  if (matcher.find()) {
    return matcher.group(1)?.let { someNumberStr -> Integer.parseInt(someNumberStr) } ?: -1
  }
  return -1
}
