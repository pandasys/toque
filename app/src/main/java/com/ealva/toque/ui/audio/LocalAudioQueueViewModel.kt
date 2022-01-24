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
import com.ealva.toque.db.PlayListType
import com.ealva.toque.db.PlaylistDao
import com.ealva.toque.db.PlaylistIdNameType
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
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel.PromptResult
import com.ealva.toque.ui.common.DialogPrompt
import com.ealva.toque.ui.library.PlayUpNextPrompt
import com.ealva.toque.ui.main.MainViewModel
import com.ealva.toque.ui.main.Notification
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
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
import kotlinx.coroutines.launch
import java.util.regex.Pattern

private val LOG by lazyLogger(LocalAudioQueueViewModel::class)

interface LocalAudioQueueViewModel {
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
    ): LocalAudioQueueViewModel =
      LocalAudioQueueViewModelImpl(
        mainViewModel,
        appPrefsSingleton,
        playlistDao,
        dispatcher
      )
  }
}

private data class PrefsHolder(val appPrefs: AppPrefs? = null)

class LocalAudioQueueViewModelImpl(
  private val mainViewModel: MainViewModel,
  private val appPrefsSingleton: AppPrefsSingleton,
  private val playlistDao: PlaylistDao,
  private val dispatcher: CoroutineDispatcher
) : LocalAudioQueueViewModel, ScopedServices.Registered {
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
    val idList: CategoryMediaList = if (shuffle.value) mediaList.shuffled() else mediaList

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
    localAudioQueue.value.playNext(mediaList, clear, playNow, Manual)
      .onSuccess { queueSize ->
        val plural = if (appPrefsSingleton.instance().allowDuplicates() || clear()) {
          R.plurals.AddedToUpNextNewSize
        } else {
          R.plurals.AddedToUpNextNewSizeLessDuplicates
        }
        mainViewModel.notify(Notification(fetchPlural(plural, size, size, queueSize.value)))
      }
      .onFailure { cause ->
        LOG.e(cause) { it("Error during playNext") }
        mainViewModel.notify(Notification(fetchPlural(R.plurals.FailedAddingToUpNext, size)))
      }
  }

  override fun addToUpNext(categoryMediaList: CategoryMediaList) {
    scope.launch {
      val quantity = categoryMediaList.size
      mainViewModel.notify(
        localAudioQueue.value.addToUpNext(categoryMediaList)
          .onFailure { cause -> LOG.e(cause) { it("Error during addToUpNext") } }
          .map { queueSize ->
            Notification(
              fetchPlural(R.plurals.AddedToUpNextNewSize, quantity, quantity, queueSize.value)
            )
          }
          .getOrElse { Notification(fetchPlural(R.plurals.FailedAddingToUpNext, quantity)) }
      )
    }
  }

  override fun showPrompt(prompt: DialogPrompt) {
    mainViewModel.prompt(prompt)
  }

  override fun clearPrompt() {
    mainViewModel.clearPrompt()
  }

  override suspend fun addToPlaylist(mediaIdList: MediaIdList): PromptResult {
    val deferred = CompletableDeferred<PromptResult>()
    // get list of user playlists. If Empty return CreatePlaylist
    val playlists: List<PlaylistIdNameType> = playlistDao.getAllOfType(PlayListType.UserCreated)
      .onFailure { cause -> LOG.e(cause) { it("Error getting user created playlists.") } }
      .getOrElse { emptyList() }

    fun onDismiss() {
      clearPrompt()
      deferred.complete(PromptResult.Dismissed)
    }

    fun addToPlaylist(mediaIdList: MediaIdList, playlistIdNameType: PlaylistIdNameType) {
      clearPrompt()
      addAudioToPlaylist(mediaIdList, playlistIdNameType)
      deferred.complete(PromptResult.Executed)
    }

    fun createPlaylist(mediaIdList: MediaIdList, playlistName: PlaylistName) {
      clearPrompt()
      createPlaylistWithAudio(mediaIdList, playlistName)
      deferred.complete(PromptResult.Executed)
    }

    fun showCreatePrompt() {
      scope.launch {
        val allPlaylists: List<PlaylistIdNameType> = playlistDao.getAllOfType()
          .onFailure { cause -> LOG.e(cause) { it("Error getting all playlists") } }
          .getOrElse { emptyList() }

        showPrompt(
          DialogPrompt(
            prompt = {
              CreatePlaylistPrompt(
                suggestedName = getSuggestedNewPlaylistName(allPlaylists),
                checkValidName = { isValidName(allPlaylists, it) },
                dismiss = ::onDismiss,
                createPlaylist = { playlistName -> createPlaylist(mediaIdList, playlistName) }
              )
            }
          )
        )
      }
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

  private fun getSuggestedNewPlaylistName(playlists: List<PlaylistIdNameType>): String =
    getSuggestedName(playlists.asSequence().map { it.name.value })

  private fun isValidName(playlists: List<PlaylistIdNameType>, name: PlaylistName): Boolean =
    name.isValidAndUnique(playlists.asSequence().map { it.name })

  private fun addAudioToPlaylist(mediaIdList: MediaIdList, playlistIdNameType: PlaylistIdNameType) {
    scope.launch {
      playlistDao.addToUserPlaylist(playlistIdNameType.id, mediaIdList)
        .onFailure { ex -> LOG.e(ex) { it("Error adding %s.", playlistIdNameType.name.value) } }
        .onSuccess { count ->
          mainViewModel.notify(makeAddToPlaylistNotification(count, playlistIdNameType))
        }
    }
  }

  private fun makeAddToPlaylistNotification(
    count: Long,
    playlistIdNameType: PlaylistIdNameType
  ): Notification {
    val value = count.toInt()
    return Notification(
      fetchPlural(R.plurals.AddedCountToPlaylist, value, value, playlistIdNameType.name.value)
    )
  }

  private fun createPlaylistWithAudio(mediaIdList: MediaIdList, playlistName: PlaylistName) {
    scope.launch {
      playlistDao.createUserPlaylist(playlistName, mediaIdList)
        .onFailure { cause ->
          LOG.e(cause) { it("Error creating %s", playlistName) }
          emitNotification(Notification(fetch(R.string.ErrorCreatingPlaylist, playlistName.value)))
        }
        .onSuccess {
          emitNotification(Notification(fetch(R.string.CreatedPlaylist, playlistName.value)))
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
