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

package com.ealva.toque.service.session.client

//
//interface SessionControl {
//  /**
//   * Request that the player prepare for playback. This can decrease the time it takes to start
//   * playback when a play command is received. Preparation is not required. You can call {@link
//   * #play} without calling this method beforehand.
//   */
//  fun prepare()
//
//  /**
//   * Request that the player prepare playback for a specific media id. This can decrease the time
//   it
//   * takes to start playback when a play command is received. Preparation is not required. You can
//   * call [playFromMediaId] without calling this method beforehand.
//   *
//   * @param mediaId The id of the requested media.
//   * @param extras Optional extras that can include extra information about the media item to be
//   * prepared.
//   */
//  fun prepareFromMediaId(mediaId: String, extras: Bundle = Bundle.EMPTY)
//
//  /**
//   * Request that the player prepare playback for a specific search query. This can decrease the
//   * time it takes to start playback when a play command is received. An empty or null query
//   should
//   * be treated as a request to prepare any music. Preparation is not required. You can call
//   * [playFromSearch] without calling this method beforehand.
//   *
//   * @param query The search query.
//   * @param extras Optional extras that can include extra information about the query.
//   */
//  fun prepareFromSearch(query: String, extras: Bundle = Bundle.EMPTY)
//
//  /**
//   * Request that the player prepare playback for a specific [Uri]. This can decrease the time it
//   * takes to start playback when a play command is received. Preparation is not required. You can
//   * call [playFromUri] without calling this method beforehand.
//   *
//   * @param uri The URI of the requested media.
//   * @param extras Optional extras that can include extra information about the media item to be
//   * prepared.
//   */
//  fun prepareFromUri(uri: Uri, extras: Bundle = Bundle.EMPTY)
//
//  /** Request that the player start its playback at its current position. */
//  fun play()
//
//  /**
//   * Request that the player start playback for a specific [Uri].
//   *
//   * @param mediaId The ID of the requested media.
//   * @param extras Optional extras that can include extra information about the media item to be
//   * played.
//   */
//  fun playFromMediaId(mediaId: String, extras: Bundle = Bundle.EMPTY)
//
//  /**
//   * Request that the player start playback for a specific search query. An empty or null query
//   * should be treated as a request to play any music.
//   *
//   * @param query The search query.
//   * @param extras Optional extras that can include extra information about the query.
//   */
//  fun playFromSearch(query: String, extras: Bundle = Bundle.EMPTY)
//
//  /**
//   * Request that the player start playback for a specific [Uri].
//   *
//   * @param uri  The URI of the requested media.
//   * @param extras Optional extras that can include extra information about the media item to be
//   * played.
//   */
//  fun playFromUri(uri: Uri, extras: Bundle = Bundle.EMPTY)
//
//  /**
//   * Plays an item with a specific id in the play queue. If you specify an id that is not in the
//   * play queue, the behavior is undefined.
//   */
//  fun skipToQueueItem(id: Long)
//
//  /**
//   * Request that the player pause its playback and stay at its current position.
//   */
//  fun pause()
//
//  /**
//   * Request that the player stop its playback; it may clear its state in whatever way is
//   * appropriate.
//   */
//  fun stop()
//
//  /** Moves to a new location in the media stream. */
//  fun seekTo(pos: Millis)
//
//  /**
//   * Starts fast forwarding. If playback is already fast forwarding this may increase the rate.
//   */
//  fun fastForward()
//
//  /** Skips to the next item. */
//  fun skipToNext()
//
//  /** Starts rewinding. If playback is already rewinding this may increase the rate. */
//  fun rewind()
//
//  /** Skips to the previous item. */
//  fun skipToPrevious()
//
//  /** Rates the current content. This will cause the rating to be set for the current item. */
//  fun setRating(rating: StarRating)
//
//  /** Enables/disables captioning for this session. */
//  fun setCaptioningEnabled(enabled: Boolean)
//
//  /** Sets the repeat mode for this session. */
//  fun setRepeatMode(mode: RepeatMode)
//
//  /** Sets the shuffle mode for this session. */
//  fun setShuffleMode(mode: ShuffleMode)
//
////  /**
////   * Sends the id and args from a custom action for the [MediaSessionCompat] to perform.
////   *
////   * @see .sendCustomAction
////   * @see MediaSessionCompat.ACTION_FLAG_AS_INAPPROPRIATE
////   * @see MediaSessionCompat.ACTION_SKIP_AD
////   * @see MediaSessionCompat.ACTION_FOLLOW
////   * @see MediaSessionCompat.ACTION_UNFOLLOW
////   * @param action The action identifier of the [PlaybackStateCompat.CustomAction] as specified
// by
////   * the [MediaSessionCompat].
////   * @param args Optional arguments to supply to the [MediaSessionCompat] for this
// custom action.
////   */
////  fun sendCustomAction(action: String?, args: Bundle?)
//
//  companion object {
//    operator fun invoke(controls: MediaControllerCompat.TransportControls): SessionControl =
//      SessionControlImpl(controls)
//  }
//}
//
//private class SessionControlImpl(
//  private val controls: MediaControllerCompat.TransportControls
//) : SessionControl {
//  override fun prepare() = controls.prepare()
//  override fun prepareFromMediaId(mediaId: String, extras: Bundle) =
//    controls.prepareFromMediaId(mediaId, extras)
//
//  override fun prepareFromSearch(query: String, extras: Bundle) =
//    controls.prepareFromSearch(query, extras)
//
//  override fun prepareFromUri(uri: Uri, extras: Bundle) = controls.prepareFromUri(uri, extras)
//  override fun play() = controls.play()
//  override fun playFromMediaId(mediaId: String, extras: Bundle) =
//    controls.playFromMediaId(mediaId, extras)
//
//  override fun playFromSearch(query: String, extras: Bundle) =
//    controls.playFromSearch(query, extras)
//
//  override fun playFromUri(uri: Uri, extras: Bundle) = controls.playFromUri(uri, extras)
//  override fun skipToQueueItem(id: Long) = controls.skipToQueueItem(id)
//  override fun pause() = controls.pause()
//  override fun stop() = controls.stop()
//  override fun seekTo(pos: Millis) = controls.seekTo(pos())
//  override fun fastForward() = controls.fastForward()
//  override fun skipToNext() = controls.skipToNext()
//  override fun rewind() = controls.rewind()
//  override fun skipToPrevious() = controls.skipToPrevious()
//  override fun setRating(rating: StarRating) = controls.setRating(rating.toRatingCompat())
//  override fun setCaptioningEnabled(enabled: Boolean) = controls.setCaptioningEnabled(enabled)
//  override fun setRepeatMode(mode: RepeatMode) = controls.setRepeatMode(mode.asCompat)
//  override fun setShuffleMode(mode: ShuffleMode) = controls.setShuffleMode(mode.asCompat)
//}
//
//object NullSessionControl : SessionControl {
//  override fun prepare() = Unit
//  override fun prepareFromMediaId(mediaId: String, extras: Bundle) = Unit
//  override fun prepareFromSearch(query: String, extras: Bundle) = Unit
//  override fun prepareFromUri(uri: Uri, extras: Bundle) = Unit
//  override fun play() = Unit
//  override fun playFromMediaId(mediaId: String, extras: Bundle) = Unit
//  override fun playFromSearch(query: String, extras: Bundle) = Unit
//  override fun playFromUri(uri: Uri, extras: Bundle) = Unit
//  override fun skipToQueueItem(id: Long) = Unit
//  override fun pause() = Unit
//  override fun stop() = Unit
//  override fun seekTo(pos: Millis) = Unit
//  override fun fastForward() = Unit
//  override fun skipToNext() = Unit
//  override fun rewind() = Unit
//  override fun skipToPrevious() = Unit
//  override fun setRating(rating: StarRating) = Unit
//  override fun setCaptioningEnabled(enabled: Boolean) = Unit
//  override fun setRepeatMode(mode: RepeatMode) = Unit
//  override fun setShuffleMode(mode: ShuffleMode) = Unit
//}
