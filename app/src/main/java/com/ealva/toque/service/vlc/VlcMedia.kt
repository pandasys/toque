/*
 * Copyright 2020 eAlva.com
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

package com.ealva.toque.service.vlc

import android.net.Uri
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.toMillis
import com.ealva.toque.file.isNetworkScheme
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.service.media.Media
import com.ealva.toque.service.media.MediaEvent
import com.ealva.toque.service.media.MediaPlayerEvent
import com.ealva.toque.service.media.MediaState
import com.ealva.toque.service.media.MetadataField
import com.ealva.toque.service.media.ParsedStatus
import com.ealva.toque.service.player.AvPlayerFactory
import com.ealva.toque.service.player.PlayerTransition
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.videolan.libvlc.interfaces.IMedia

private val LOG by lazyLogger(VlcMedia::class)

class VlcMedia(
  val media: IMedia,
  val uri: Uri,
  private val mediaId: MediaId,
  private val albumId: AlbumId,
  private val presetSelector: EqPresetSelector,
  private val vlcPlayerFactory: AvPlayerFactory,
  private val prefs: AppPrefs,
  private val dispatcher: CoroutineDispatcher
) : Media {
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
  private var player: VlcPlayer = NullVlcPlayer

  override val isStream: Boolean = uri.isNetworkScheme()

  override val parsedStatus = MutableStateFlow(
    if (media.isParsed) ParsedStatus.Done else ParsedStatus.NotParsed
  )
  override val state = MutableStateFlow(media.state.toMediaState())
  override val duration = MutableStateFlow(media.duration.toMillis())

  override val mediaEventFlow = MutableSharedFlow<MediaEvent>()
  override val playerEventFlow = MutableSharedFlow<MediaPlayerEvent>()

  init {
    media.setEventListener(makeMediaEventListener())
  }

  private fun emitMediaEvent(event: MediaEvent) {
    scope.launch { mediaEventFlow.emit(event) }
  }

  fun release() {
    scope.cancel()
    player.shutdown()
    media.release()
  }

  override suspend fun prepareAndPlay(onPreparedTransition: PlayerTransition) {
    player = makePlayer(onPreparedTransition)
    scope.launch {
      player.eventFlow.collect { playerEventFlow.emit(it) }
    }
  }

  private suspend fun makePlayer(onPreparedTransition: PlayerTransition): VlcPlayer {
    TODO()
//    return vlcPlayerFactory.make(
//      this,
//      onPreparedTransition,
//      prefs,
//      duration.value,
//      dispatcher
//    )
  }

  private fun makeMediaEventListener(): IMedia.EventListener =
    IMedia.EventListener { event: IMedia.Event ->
      when (event.type) {
        IMedia.Event.MetaChanged ->
          emitMediaEvent(MediaEvent.MetadataUpdate(event.metaId.toMetadataField()))
        IMedia.Event.SubItemAdded -> {
        }
        IMedia.Event.DurationChanged -> duration.value = media.duration.toMillis()
        IMedia.Event.ParsedChanged -> parsedStatus.value = event.parsedStatus.toParsedStatus()
        IMedia.Event.StateChanged -> state.value = media.state.toMediaState()
        IMedia.Event.SubItemTreeAdded -> {
        }
        else -> LOG.e { it("Unrecognized IMediaEvent type %d", event.type) }
      }
    }

  companion object {
    fun parseFlagFromUri(uri: Uri): Int =
      if (uri.isNetworkScheme()) PARSE_NETWORK else PARSE_LOCAL

    /** Parse metadata if the file is local. Doesn't bother with artwork */
    private const val PARSE_LOCAL = IMedia.Parse.ParseLocal

    /** Parse metadata even if over a network connection. Doesn't bother with artwork */
    private const val PARSE_NETWORK = IMedia.Parse.ParseNetwork

//    /** Parse metadata and fetch artwork if the file is local */
//    const val PARSE_WITH_ART_LOCAL = IMedia.Parse.FetchLocal
//
//    /** Parse metadata and fetch artwork even if over a network connection */
//    const val PARSE_WITH_ART_NETWORK = IMedia.Parse.FetchNetwork
  }
}

private fun Int.toMetadataField(): MetadataField = when (this) {
  IMedia.Meta.Title -> MetadataField.Title
  IMedia.Meta.Artist -> MetadataField.Artist
  IMedia.Meta.Genre -> MetadataField.Genre
  IMedia.Meta.Copyright -> MetadataField.Copyright
  IMedia.Meta.Album -> MetadataField.Album
  IMedia.Meta.TrackNumber -> MetadataField.TrackNumber
  IMedia.Meta.Description -> MetadataField.Description
  IMedia.Meta.Rating -> MetadataField.Rating
  IMedia.Meta.Date -> MetadataField.Date
  IMedia.Meta.Setting -> MetadataField.Setting
  IMedia.Meta.URL -> MetadataField.URL
  IMedia.Meta.Language -> MetadataField.Language
  IMedia.Meta.NowPlaying -> MetadataField.NowPlaying
  IMedia.Meta.Publisher -> MetadataField.Publisher
  IMedia.Meta.EncodedBy -> MetadataField.EncodedBy
  IMedia.Meta.ArtworkURL -> MetadataField.ArtworkURL
  IMedia.Meta.TrackID -> MetadataField.TrackID
  IMedia.Meta.TrackTotal -> MetadataField.TrackTotal
  IMedia.Meta.Director -> MetadataField.Director
  IMedia.Meta.Season -> MetadataField.Season
  IMedia.Meta.Episode -> MetadataField.Episode
  IMedia.Meta.ShowName -> MetadataField.ShowName
  IMedia.Meta.Actors -> MetadataField.Actors
  IMedia.Meta.AlbumArtist -> MetadataField.AlbumArtist
  IMedia.Meta.DiscNumber -> MetadataField.DiscNumber
  else -> MetadataField.Unknown
}

private fun Int.toMediaState(): MediaState = when (this) {
  IMedia.State.NothingSpecial -> MediaState.NothingSpecial
  IMedia.State.Opening -> MediaState.Opening
  IMedia.State.Playing -> MediaState.Playing
  IMedia.State.Paused -> MediaState.Paused
  IMedia.State.Stopped -> MediaState.Stopped
  IMedia.State.Ended -> MediaState.Ended
  IMedia.State.Error -> MediaState.Error
  else -> MediaState.Unknown
}

private fun Int.toParsedStatus(): ParsedStatus = when (this) {
  IMedia.ParsedStatus.Skipped -> ParsedStatus.Skipped
  IMedia.ParsedStatus.Failed -> ParsedStatus.Failed
  IMedia.ParsedStatus.Timeout -> ParsedStatus.Timeout
  IMedia.ParsedStatus.Done -> ParsedStatus.Done
  else -> ParsedStatus.Unknown
}
