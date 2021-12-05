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

package com.ealva.toque.ui.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvabrainz.common.ComposerName
import com.ealva.ealvabrainz.common.GenreName
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.common.Millis
import com.ealva.toque.common.Title
import com.ealva.toque.common.asDurationString
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.FullAudioInfo
import com.ealva.toque.log._e
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.service.media.EmbeddedArtwork
import com.ealva.toque.service.media.MediaFileTagInfo
import com.ealva.toque.service.media.MediaMetadataParserFactory
import com.ealva.toque.service.media.StarRating
import com.ealva.toque.service.media.asString
import com.ealva.toque.tag.ArtistParserFactory
import com.ealva.toque.ui.settings.AppBarTitle
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding
import com.gowtham.ratingbar.RatingBar
import com.gowtham.ratingbar.RatingBarStyle
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.ScopedServices
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.text.DateFormat
import java.util.Locale

private val LOG by lazyLogger(AudioMediaInfoScreen::class)

@Immutable
@Parcelize
data class AudioMediaInfoScreen(
  private val mediaId: MediaId
) : BaseLibraryItemsScreen(), KoinComponent {
  override fun bindServices(serviceBinder: ServiceBinder) {
    LOG._e { it("bindServices") }
    serviceBinder.add(
      MediaInfoViewModel(
        mediaId = mediaId,
        audioMediaDao = get(),
        mediaDataParserFactory = get(),
        artistParserFactory = get(),
        backstack = serviceBinder.backstack
      )
    )
  }

  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<MediaInfoViewModel>()
    val mediaInfo = viewModel.mediaInfo.collectAsState()

    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding(bottom = false)
    ) {
      TopAppBar(
        title = { AppBarTitle(mediaInfo.value.title.value) },
        backgroundColor = MaterialTheme.colors.surface,
        modifier = Modifier.fillMaxWidth(),
        navigationIcon = {
          IconButton(onClick = { viewModel.goBack() }) {
            Icon(
              painter = rememberImagePainter(data = R.drawable.ic_arrow_left),
              contentDescription = "Back",
              modifier = Modifier.size(26.dp)
            )
          }
        }
      )
      MediaInfo(mediaInfo.value)
    }
  }

}

interface MediaInfoViewModel {

  @Immutable
  data class MediaInfo(
    val title: Title = Title(""),
    val album: AlbumTitle = AlbumTitle(""),
    val albumArtist: ArtistName = ArtistName(""),
    val songArtist: ArtistName = ArtistName(""),
    val track: Int = 0,
    val totalTracks: Int = 0,
    val disc: Int = 0,
    val totalDiscs: Int = 0,
    val duration: Millis = Millis(0L),
    val year: Int = 0,
    val genre: GenreName = GenreName(""),
    val composer: ComposerName = ComposerName(""),
    val rating: StarRating = StarRating.STAR_NONE,
    val tagRating: StarRating = StarRating.STAR_NONE,
    val playCount: Int = 0,
    val lastPlayed: Millis = Millis(0),
    val skippedCount: Int = 0,
    val lastSkipped: Millis = Millis(0),
    val dateAdded: Millis = Millis(0),
    val location: Uri = Uri.EMPTY,
    val audioInfo: String = "",
    val embeddedArtwork: Bitmap? = null,
    val artworkDescription: String = "",
    val comment: String = "",
    val mediaId: MediaId = MediaId.INVALID,
    val albumId: AlbumId = AlbumId.INVALID,
    val albumArtistId: ArtistId = ArtistId.INVALID,
  )

  val mediaInfo: StateFlow<MediaInfo>

  fun goBack()

  companion object {
    operator fun invoke(
      mediaId: MediaId,
      audioMediaDao: AudioMediaDao,
      mediaDataParserFactory: MediaMetadataParserFactory,
      artistParserFactory: ArtistParserFactory,
      backstack: Backstack,
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): MediaInfoViewModel = MediaInfoViewModelImpl(
      mediaId,
      audioMediaDao,
      mediaDataParserFactory,
      artistParserFactory,
      backstack,
      dispatcher
    )
  }
}

private class MediaInfoViewModelImpl(
  private val mediaId: MediaId,
  private val audioMediaDao: AudioMediaDao,
  mediaDataParserFactory: MediaMetadataParserFactory,
  private val artistParserFactory: ArtistParserFactory,
  private val backstack: Backstack,
  private val dispatcher: CoroutineDispatcher,
) : MediaInfoViewModel, ScopedServices.Activated {
  private lateinit var scope: CoroutineScope
  private val metadataParser = mediaDataParserFactory.make()

  override val mediaInfo = MutableStateFlow(MediaInfoViewModel.MediaInfo())
  override fun goBack() {
    backstack.goBack()
  }

  override fun onServiceActive() {
    scope = CoroutineScope(SupervisorJob() + dispatcher)
    LOG._e { it("onServiceActive") }
    scope.launch {
      when (val result = audioMediaDao.getFullInfo(mediaId)) {
        is Ok -> handleFullAudioInfo(result.value)
        is Err -> LOG.e { it("%s", result.error) }
      }
    }
  }

  private fun handleFullAudioInfo(fullInfo: FullAudioInfo) {
    LOG._e { it("handleFullAudioInfo") }
    scope.launch {
      val uriToParse = if (fullInfo.file == Uri.EMPTY) fullInfo.location else fullInfo.file
      if (uriToParse !== Uri.EMPTY) {
        val artwork = metadataParser.parseMetadata(
          uri = uriToParse,
          artistParser = artistParserFactory.make(),
          ignoreArtwork = false
        ).use { tag: MediaFileTagInfo ->
          LOG._e { it("have tag") }
          mediaInfo.update { info ->
            info.copy(
              tagRating = tag.rating,
              audioInfo = tag.fullDescription,
              comment = tag.comment
            )
          }
          tag.embeddedArtwork
        }
        handleEmbeddedArtwork(artwork)
      }
    }
    mediaInfo.update { info ->
      info.copy(
        title = fullInfo.title,
        album = fullInfo.album,
        albumArtist = fullInfo.albumArtist,
        songArtist = fullInfo.songArtist,
        track = fullInfo.track,
        totalTracks = fullInfo.totalTracks,
        disc = fullInfo.disc,
        totalDiscs = fullInfo.totalDiscs,
        duration = fullInfo.duration,
        year = fullInfo.year,
        genre = fullInfo.genre,
        composer = fullInfo.composer,
        rating = fullInfo.rating,
        playCount = fullInfo.playCount,
        lastPlayed = fullInfo.lastPlayed,
        skippedCount = fullInfo.skippedCount,
        lastSkipped = fullInfo.lastSkipped,
        dateAdded = fullInfo.dateAdded,
        location = if (fullInfo.file === Uri.EMPTY) fullInfo.location else fullInfo.file,
        mediaId = fullInfo.mediaId,
        albumId = fullInfo.albumId,
        albumArtistId = fullInfo.albumArtistId
      )
    }
  }

  private fun handleEmbeddedArtwork(artwork: EmbeddedArtwork) {
    LOG._e { it("artwork=%s", artwork.asString) }
    if (artwork.exists && artwork.isBinary) {
      scope.launch(Dispatchers.IO) {
        try {
          val options = BitmapFactory.Options()
          val bytes = artwork.data.size
          val bitmap = BitmapFactory.decodeByteArray(artwork.data, 0, bytes, options)
          val description = getDescription(
            bytes,
            options.outWidth,
            options.outHeight,
            options.outMimeType,
            artwork.pictureType
          )
          LOG._e { it("description=%s", description) }
          mediaInfo.update { info ->
            info.copy(
              embeddedArtwork = bitmap,
              artworkDescription = description
            )
          }
        } catch (e: Exception) {
          LOG.e(e) { it("Can't decode embedded bitmap") }
        }
      }
    }
  }

  private fun getDescription(
    bytes: Int,
    width: Int,
    height: Int,
    mimeType: String?,
    pictureType: String
  ) = "${width}x$height, $mimeType, $pictureType, $bytes bytes"

  override fun onServiceInactive() {
    scope.cancel()
  }
}

@Composable
private fun MediaInfo(mediaInfo: MediaInfoViewModel.MediaInfo) {
  val labelColor = MaterialTheme.colors.onSurface.copy(ContentAlpha.medium)

  Column(
    modifier = Modifier
      .verticalScroll(rememberScrollState())
      .fillMaxWidth()
      .padding(horizontal = 12.dp)
  ) {
    LabeledTextValue(R.string.AlbumArtist, mediaInfo.albumArtist.value, labelColor)
    LabeledTextValue(R.string.Album, mediaInfo.album.value, labelColor)
    LabeledTextValue(R.string.SongArtist, mediaInfo.songArtist.value, labelColor)
    TrackDiscRow(mediaInfo, labelColor)
    LabeledTextValue(R.string.Genre, mediaInfo.genre.value, labelColor)
    LabeledTextValue(R.string.Composer, mediaInfo.composer.value, labelColor)
    RatingRow(mediaInfo, labelColor)
    CountLastOccurredRow(
      count = mediaInfo.playCount,
      countLabel = R.string.Plays,
      last = mediaInfo.lastPlayed,
      lastLabel = R.string.LastPlayed,
      labelColor = labelColor
    )
    CountLastOccurredRow(
      count = mediaInfo.skippedCount,
      countLabel = R.string.Skips,
      last = mediaInfo.lastSkipped,
      lastLabel = R.string.LastSkipped,
      labelColor = labelColor
    )
    LabeledTextValue(
      labelRes = R.string.DateAdded,
      valueText = mediaInfo.dateAdded.asDateTime(),
      labelColor = labelColor
    )
    LabeledTextValue(
      labelText = mediaInfo.location.path ?: "Audio Info",
      valueText = mediaInfo.audioInfo,
      labelColor = labelColor
    )
    LabeledValue(
      labelRes = R.string.EmbeddedArtwork,
      labelColor = labelColor,
      labelBottomPadding = 4.dp,
      value = {}
    )
  }
}

@Composable
fun RatingRow(mediaInfo: MediaInfoViewModel.MediaInfo, labelColor: Color) {
  Row(
    modifier = Modifier.fillMaxWidth()
  ) {
    LabeledValue(
      modifier = Modifier.weight(.5F),
      labelRes = R.string.Rating,
      labelColor = labelColor,
      labelBottomPadding = 4.dp,
      value = {
        if (mediaInfo.rating.isValid) {
          RatingBar(
            value = mediaInfo.rating.value,
            size = 18.dp,
            padding = 2.dp,
            isIndicator = true,
            activeColor = LocalContentColor.current,
            inactiveColor = LocalContentColor.current,
            ratingBarStyle = RatingBarStyle.HighLighted,
            onValueChange = {},
            onRatingChanged = {},
          )
        }
      },
    )
    LabeledValue(
      modifier = Modifier.weight(.5F),
      labelRes = R.string.TagRating,
      labelColor = labelColor,
      labelBottomPadding = 4.dp,
      value = {
        if (mediaInfo.tagRating.isValid) {
          RatingBar(
            value = mediaInfo.tagRating.value,
            size = 18.dp,
            padding = 2.dp,
            isIndicator = true,
            activeColor = LocalContentColor.current,
            inactiveColor = LocalContentColor.current,
            ratingBarStyle = RatingBarStyle.HighLighted,
            onValueChange = {},
            onRatingChanged = {},
          )
        }
      },
    )
  }
}

@Composable
fun CountLastOccurredRow(
  count: Int,
  @StringRes countLabel: Int,
  last: Millis,
  @StringRes lastLabel: Int,
  labelColor: Color
) {
  Row(
    modifier = Modifier.fillMaxWidth()
  ) {
    LabeledTextValue(
      modifier = Modifier.weight(.25F),
      valueText = count.toString(),
      labelRes = countLabel,
      labelColor = labelColor,
    )
    LabeledTextValue(
      modifier = Modifier.weight(.75F),
      valueText = if (last > 0) last.asDateTime() else "",
      labelRes = lastLabel,
      labelColor = labelColor,
    )
  }
}

@Composable
fun TrackDiscRow(mediaInfo: MediaInfoViewModel.MediaInfo, labelColor: Color) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    LabeledTextValue(R.string.Track, numOfTotal(mediaInfo.track, mediaInfo.totalTracks), labelColor)
    LabeledTextValue(R.string.Disc, numOfTotal(mediaInfo.disc, mediaInfo.totalDiscs), labelColor)
    LabeledTextValue(R.string.Duration, mediaInfo.duration.asDurationString, labelColor)
    LabeledTextValue(R.string.Year, mediaInfo.year.toString(), labelColor)
  }
}

fun numOfTotal(num: Int, totalTracks: Int): String {
  return if (totalTracks > 0) "$num of $totalTracks" else "$num"
}

@Composable
private fun LabeledTextValue(
  @StringRes labelRes: Int,
  valueText: String,
  labelColor: Color,
  modifier: Modifier = Modifier,
  ) {
  LabeledTextValue(
    labelText = stringResource(id = labelRes),
    valueText = valueText,
    labelColor = labelColor,
    modifier = modifier,
  )
}

@Composable
private fun LabeledTextValue(
  labelText: String,
  valueText: String,
  labelColor: Color,
  modifier: Modifier = Modifier,
) {
  LabeledValue(
    modifier = modifier,
    labelText = labelText,
    labelColor = labelColor,
    value = { Text(text = valueText, style = MaterialTheme.typography.body1) },
  )
}

@Composable
private fun LabeledValue(
  modifier: Modifier = Modifier,
  labelRes: Int,
  labelColor: Color,
  labelBottomPadding: Dp = 0.dp,
  value: @Composable () -> Unit,
) {
  Column(
    modifier = modifier.padding(vertical = 4.dp, horizontal = 6.dp)
  ) {
    Text(
      modifier = Modifier.padding(bottom = labelBottomPadding),
      text = stringResource(id = labelRes),
      style = MaterialTheme.typography.caption,
      color = labelColor
    )
    value()
  }
}

@Composable
private fun LabeledValue(
  modifier: Modifier = Modifier,
  labelText: String,
  labelColor: Color,
  labelBottomPadding: Dp = 0.dp,
  value: @Composable () -> Unit,
) {
  Column(
    modifier = modifier.padding(vertical = 4.dp, horizontal = 6.dp)
  ) {
    Text(
      modifier = Modifier.padding(bottom = labelBottomPadding),
      text = labelText,
      style = MaterialTheme.typography.caption,
      color = labelColor
    )
    value()
  }
}

private val DATE_FORMATTER = DateFormat.getDateTimeInstance(
  DateFormat.MEDIUM,
  DateFormat.SHORT,
  Locale.getDefault()
)

fun Millis.asDateTime(): String = DATE_FORMATTER.format(value)
