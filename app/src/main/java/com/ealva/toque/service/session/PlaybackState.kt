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

package com.ealva.toque.service.session

import android.os.Bundle
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.StringRes
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.app.Toque
import com.ealva.toque.common.Millis
import com.ealva.toque.common.PlaybackRate
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.log._e
import com.ealva.toque.service.session.PlaybackActions.Action

private val LOG by lazyLogger(PlaybackState::class)

enum class PlaybackState(val compat: Int) {
  None(PlaybackStateCompat.STATE_NONE),
  Stopped(PlaybackStateCompat.STATE_STOPPED),
  Playing(PlaybackStateCompat.STATE_PLAYING),
  Paused(PlaybackStateCompat.STATE_PAUSED),
  FastForwarding(PlaybackStateCompat.STATE_FAST_FORWARDING),
  Rewinding(PlaybackStateCompat.STATE_REWINDING),
  Buffering(PlaybackStateCompat.STATE_BUFFERING),
  Error(PlaybackStateCompat.STATE_ERROR),
  Connecting(PlaybackStateCompat.STATE_CONNECTING),
  SkippingToPrevious(PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS),
  SkippingToNext(PlaybackStateCompat.STATE_SKIPPING_TO_NEXT),
  SkippingToItem(PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM);
}

interface PlaybackInfo {
  val state: PlaybackState
  val position: Millis
  val activeItemId: Long
  val rate: PlaybackRate
  val repeatMode: RepeatMode
  val hasNext: Boolean
  val hasPrevious: Boolean
  val isSeekable: Boolean
}

class PlaybackInfoData(
  override val state: PlaybackState,
  override val position: Millis,
  override val activeItemId: Long,
  override val rate: PlaybackRate,
  override val repeatMode: RepeatMode,
  override val hasNext: Boolean,
  override val hasPrevious: Boolean,
  override val isSeekable: Boolean
) : PlaybackInfo

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

  val asCompat: Int
    get() = code
}

fun buildErrorState(
  code: PlaybackErrorCode,
  @StringRes res: Int
): PlaybackStateCompat = buildPlaybackState {
  setState(PlaybackStateCompat.STATE_ERROR, 0, 0F)
  setErrorMessage(code.asCompat, Toque.appContext.getString(res))
}

operator fun PlaybackActions.Companion.invoke(info: PlaybackInfo): PlaybackActions {
  return PlaybackActions().set(info)
}

fun PlaybackInfo.toState(): PlaybackStateCompat = buildPlaybackState {
  setState(state.compat, position.value, rate.rate)
  setActions(PlaybackActions(this@toState).asCompat)
  setActiveQueueItemId(activeItemId)
  setExtras(Bundle.EMPTY)
}

fun PlaybackActions.set(info: PlaybackInfo): PlaybackActions = apply {
  when (info.state) {
    PlaybackState.Playing -> addAll(Action.Pause, Action.Stop)
    PlaybackState.Paused -> addAll(Action.Play, Action.Stop)
    else -> add(Action.Play)
  }
  if (info.repeatMode != RepeatMode.One) {
    when {
      info.hasNext -> add(Action.SkipToNext)
      info.hasPrevious && info.isSeekable -> addAll(Action.SkipToPrevious)
    }
  }
  if (info.isSeekable) addAll(Action.FastForward, Action.Rewind, Action.SeekTo, Action.SetRating)
  addAll(Action.SetShuffleMode, Action.SetRepeatMode)
  LOG._e { it("actions=%s", this) }
}

val EMPTY_PLAYBACK_STATE: PlaybackStateCompat = buildPlaybackState {
  setState(PlaybackStateCompat.STATE_NONE, 0, 0f)
}

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
