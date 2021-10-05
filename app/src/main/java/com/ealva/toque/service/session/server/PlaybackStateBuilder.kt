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

@file:Suppress("unused")

package com.ealva.toque.service.session.server

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.StringRes
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Millis
import com.ealva.toque.common.PlaybackRate
import com.ealva.toque.common.fetch
import com.ealva.toque.service.media.PlayState
import com.ealva.toque.service.session.common.PlaybackActions
import com.ealva.toque.service.session.common.PlaybackState

private val LOG by lazyLogger(PlayState::class)

interface PlaybackStateBuilder {
  fun setState(playState: PlayState, position: Millis, rate: PlaybackRate): PlaybackStateBuilder
  fun setIsStreaming(isStreaming: Boolean): PlaybackStateBuilder
  fun setActions(actions: PlaybackActions): PlaybackStateBuilder
  fun setActiveQueueItemId(id: Long): PlaybackStateBuilder
  fun setError(code: PlaybackErrorCode, @StringRes res: Int): PlaybackStateBuilder
  fun setError(
    code: PlaybackErrorCode,
    @StringRes res: Int,
    vararg formatArgs: Any
  ): PlaybackStateBuilder

  fun build(): PlaybackStateCompat

  companion object {
    operator fun invoke(): PlaybackStateBuilder = PlaybackStateBuilderImpl()

    operator fun invoke(state: PlaybackState): PlaybackStateBuilder {
      return PlaybackStateBuilderImpl()
        .setState(state.playState, state.position, state.playbackRate)
        .setActions(state.actions)
        .setActiveQueueItemId(state.itemInstanceId)
    }

    const val IS_STREAMING_KEY = "IsStreaming"
  }
}

private class PlaybackStateBuilderImpl : PlaybackStateBuilder {
  private val extras = Bundle()
  private val builder = PlaybackStateCompat.Builder()

  override fun setState(playState: PlayState, position: Millis, rate: PlaybackRate) = apply {
    builder.setState(playState(), position(), rate())
  }

  override fun setIsStreaming(isStreaming: Boolean) = apply {
    extras.putBoolean(PlaybackStateBuilder.IS_STREAMING_KEY, isStreaming)
  }

  @SuppressLint("WrongConstant")
  override fun setActions(actions: PlaybackActions) = apply {
    builder.setActions(actions.asCompat)
  }

  override fun setActiveQueueItemId(id: Long) = apply {
    builder.setActiveQueueItemId(id)
  }

  override fun setError(code: PlaybackErrorCode, res: Int) = apply {
    builder.setErrorMessage(code(), fetch(res))
  }

  override fun setError(code: PlaybackErrorCode, res: Int, vararg formatArgs: Any) = apply {
    builder.setErrorMessage(code(), fetch(res, *formatArgs))
  }

  override fun build(): PlaybackStateCompat = builder.setExtras(extras).build()
}

val PlaybackStateCompat.isStreaming: Boolean
  get() = extras?.getBoolean(PlaybackStateBuilder.IS_STREAMING_KEY) ?: false

inline fun buildPlaybackState(
  builderAction: PlaybackStateCompat.Builder.() -> Unit
): PlaybackStateCompat = PlaybackStateCompat.Builder().apply(builderAction).build()

enum class PlaybackErrorCode(private val code: Int) {
  Unknown(PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR),
  AppError(PlaybackStateCompat.ERROR_CODE_APP_ERROR),
  NotSupported(PlaybackStateCompat.ERROR_CODE_NOT_SUPPORTED),
  AuthenticationExpired(PlaybackStateCompat.ERROR_CODE_AUTHENTICATION_EXPIRED),
  PremiumAccountRequired(PlaybackStateCompat.ERROR_CODE_PREMIUM_ACCOUNT_REQUIRED),
  ConcurrentStreamLimit(PlaybackStateCompat.ERROR_CODE_CONCURRENT_STREAM_LIMIT),
  ParentalControlRestricted(PlaybackStateCompat.ERROR_CODE_PARENTAL_CONTROL_RESTRICTED),
  NotAvailableInRegion(PlaybackStateCompat.ERROR_CODE_NOT_AVAILABLE_IN_REGION),
  ContentAlreadyPlaying(PlaybackStateCompat.ERROR_CODE_CONTENT_ALREADY_PLAYING),
  SkipLimitReached(PlaybackStateCompat.ERROR_CODE_SKIP_LIMIT_REACHED),
  ActionAborted(PlaybackStateCompat.ERROR_CODE_ACTION_ABORTED),
  EndOfQueue(PlaybackStateCompat.ERROR_CODE_END_OF_QUEUE);

  operator fun invoke(): Int = code
}

fun buildErrorState(
  code: PlaybackErrorCode,
  @StringRes res: Int
): PlaybackStateCompat = buildPlaybackState {
  setState(PlaybackStateCompat.STATE_ERROR, 0, 0F)
  setErrorMessage(code(), fetch(res))
}

val EMPTY_PLAYBACK_STATE: PlaybackStateCompat = PlaybackStateCompat.Builder().build()

/*
 private fun publishState(position: Long? = null) {
     if (!this::mediaSession.isInitialized) return
     if (AndroidDevices.isAndroidTv) handler.removeMessages(END_MEDIASESSION)
     val pscb = PlaybackStateCompat.Builder()
     var actions = PLAYBACK_BASE_ACTIONS
     val hasMedia = playlistManager.hasCurrentMedia()
     var time = position ?: time
     var state = PlayerController.playbackState
     when (state) {
         PlaybackStateCompat.STATE_PLAYING -> actions = actions or
           (PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_STOP)
         PlaybackStateCompat.STATE_PAUSED -> actions = actions or
           (PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_STOP)
         else -> {
             actions = actions or PlaybackStateCompat.ACTION_PLAY
             val media = if (AndroidDevices.isAndroidTv && !AndroidUtil.isOOrLater && hasMedia)
               playlistManager.getCurrentMedia() else null
             if (media != null) { // Hack to show a now paying card on Android TV
                 val length = media.length
                 time = media.time
                 val progress = if (length <= 0L) 0f else time / length.toFloat()
                 if (progress < 0.95f) {
                     state = PlaybackStateCompat.STATE_PAUSED
                     handler.sendEmptyMessageDelayed(END_MEDIASESSION, 900_000L)
                 }
             }
         }
     }
     pscb.setState(state, time, playlistManager.player.getRate())
     pscb.setActiveQueueItemId(playlistManager.currentIndex.toLong())
     val repeatType = playlistManager.repeating
     if (repeatType != PlaybackStateCompat.REPEAT_MODE_NONE || hasNext())
         actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
     if (repeatType != PlaybackStateCompat.REPEAT_MODE_NONE || hasPrevious() || isSeekable)
         actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
     if (isSeekable)
         actions = actions or PlaybackStateCompat.ACTION_FAST_FORWARD or
           PlaybackStateCompat.ACTION_REWIND or PlaybackStateCompat.ACTION_SEEK_TO
     actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM
     if (playlistManager.canShuffle()) actions = actions or
       PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE
     actions = actions or PlaybackStateCompat.ACTION_SET_REPEAT_MODE
     pscb.setActions(actions)
     mediaSession.setRepeatMode(repeatType)
     mediaSession.setShuffleMode(if (isShuffling) PlaybackStateCompat.SHUFFLE_MODE_ALL else
      PlaybackStateCompat.SHUFFLE_MODE_NONE)
     val repeatResId = if (repeatType == PlaybackStateCompat.REPEAT_MODE_ALL)
          R.drawable.ic_auto_repeat_pressed
        else if (repeatType == PlaybackStateCompat.REPEAT_MODE_ONE)
          R.drawable.ic_auto_repeat_one_pressed
        else
          R.drawable.ic_auto_repeat_normal
     if (playlistManager.canShuffle())
         pscb.addCustomAction("shuffle", getString(R.string.shuffle_title),
            if (isShuffling) R.drawable.ic_auto_shuffle_enabled
            else R.drawable.ic_auto_shuffle_disabled
         )
     pscb.addCustomAction("repeat", getString(R.string.repeat_title), repeatResId)
     mediaSession.setExtras(Bundle().apply {
         putBoolean(PLAYBACK_SLOT_RESERVATION_SKIP_TO_NEXT, true)
         putBoolean(PLAYBACK_SLOT_RESERVATION_SKIP_TO_PREV, true)
     })

     val mediaIsActive = state != PlaybackStateCompat.STATE_STOPPED
     val update = mediaSession.isActive != mediaIsActive
     updateMediaQueueSlidingWindow()
     mediaSession.setPlaybackState(pscb.build())
     enabledActions = actions
     mediaSession.isActive = mediaIsActive
     mediaSession.setQueueTitle(getString(R.string.music_now_playing))
     if (update) {
         if (mediaIsActive) sendStartSessionIdIntent()
         else sendStopSessionIdIntent()
     }
 }
 */
