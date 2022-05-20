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
import androidx.compose.foundation.Image
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
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvabrainz.common.ComposerName
import com.ealva.ealvabrainz.common.GenreName
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.common.Millis
import com.ealva.toque.common.Rating
import com.ealva.toque.common.StarRating
import com.ealva.toque.common.Title
import com.ealva.toque.common.asHourMinutesSeconds
import com.ealva.toque.common.fetch
import com.ealva.toque.common.toStarRating
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.FullAudioInfo
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.service.media.EmbeddedArtwork
import com.ealva.toque.service.media.MediaFileTagInfo
import com.ealva.toque.service.media.MediaMetadataParserFactory
import com.ealva.toque.tag.ArtistParserFactory
import com.ealva.toque.ui.common.LocalScreenConfig
import com.ealva.toque.ui.main.MainViewModel
import com.ealva.toque.ui.main.Notification
import com.ealva.toque.ui.nav.backIfAllowed
import com.ealva.toque.ui.settings.AppBarTitle
import com.ealva.toque.ui.theme.toqueColors
import com.ealva.toque.ui.theme.toqueTypography
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding
import com.gowtham.ratingbar.RatingBar
import com.gowtham.ratingbar.RatingBarConfig
import com.gowtham.ratingbar.RatingBarStyle
import com.gowtham.ratingbar.StepSize
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.ScopedServices
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import com.zhuinden.simplestackextensions.servicesktx.lookup
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.text.DateFormat
import java.util.Locale
import kotlin.time.Duration

private val LOG by lazyLogger(AudioMediaInfoScreen::class)

@Immutable
@Parcelize
data class AudioMediaInfoScreen(
  private val mediaId: MediaId,
  private val title: Title,
  private val albumTitle: AlbumTitle,
  private val albumArtist: ArtistName,
  private val rating: Rating,
  private val durationMillis: Millis,
) : ComposeKey(), LibraryItemsScreen, KoinComponent {
  @IgnoredOnParcel
  private val duration: Duration = durationMillis.toDuration()

  override fun bindServices(serviceBinder: ServiceBinder) {
    serviceBinder.add(
      MediaInfoViewModel(
        mediaId = mediaId,
        title = title,
        albumTitle = albumTitle,
        albumArtist = albumArtist,
        rating = rating,
        duration = duration,
        mainViewModel = serviceBinder.lookup(),
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
        backgroundColor = toqueColors.surface,
        modifier = Modifier.fillMaxWidth(),
        navigationIcon = {
          IconButton(onClick = { viewModel.goBack() }) {
            Icon(
              painter = painterResource(id = R.drawable.ic_navigate_before),
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
    val duration: Duration = Duration.ZERO,
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
    val albumArt: Uri = Uri.EMPTY,
    val localAlbumArt: Uri = Uri.EMPTY
  )

  val mediaInfo: StateFlow<MediaInfo>

  fun goBack()

  companion object {
    operator fun invoke(
      mediaId: MediaId,
      title: Title,
      albumTitle: AlbumTitle,
      albumArtist: ArtistName,
      rating: Rating,
      duration: Duration,
      mainViewModel: MainViewModel,
      audioMediaDao: AudioMediaDao,
      mediaDataParserFactory: MediaMetadataParserFactory,
      artistParserFactory: ArtistParserFactory,
      backstack: Backstack,
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): MediaInfoViewModel = MediaInfoViewModelImpl(
      mediaId,
      title,
      albumTitle,
      albumArtist,
      rating,
      duration,
      mainViewModel,
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
  title: Title,
  albumTitle: AlbumTitle,
  albumArtist: ArtistName,
  rating: Rating,
  duration: Duration,
  private val mainViewModel: MainViewModel,
  private val audioMediaDao: AudioMediaDao,
  mediaDataParserFactory: MediaMetadataParserFactory,
  private val artistParserFactory: ArtistParserFactory,
  private val backstack: Backstack,
  private val dispatcher: CoroutineDispatcher,
) : MediaInfoViewModel, ScopedServices.Activated {
  private lateinit var scope: CoroutineScope
  private val metadataParser = mediaDataParserFactory.make()

  override val mediaInfo = MutableStateFlow(
    MediaInfoViewModel.MediaInfo(
      title = title,
      album = albumTitle,
      albumArtist = albumArtist,
      songArtist = albumArtist,  // often the same, will update if incorrect
      rating = rating.toStarRating(),
      tagRating = rating.toStarRating(), // usually the same, will update if incorrect
      duration = duration
    )
  )

  override fun goBack() {
    backstack.backIfAllowed()
  }

  override fun onServiceActive() {
    scope = CoroutineScope(SupervisorJob() + dispatcher)
    scope.launch {
      audioMediaDao.getFullInfo(mediaId)
        .onFailure { cause ->
          LOG.e(cause) { it("Error getting media info for $mediaId") }
          mainViewModel.notify(Notification(fetch(R.string.ErrorReadingMediaInfo)))
        }
        .onSuccess { fullAudioInfo -> handleFullAudioInfo(fullAudioInfo) }
    }
  }

  private fun handleFullAudioInfo(fullInfo: FullAudioInfo) {
    scope.launch {
      val uriToParse = if (fullInfo.file == Uri.EMPTY) fullInfo.location else fullInfo.file
      if (uriToParse !== Uri.EMPTY) {
        val artwork = metadataParser.parseMetadata(
          uri = uriToParse,
          artistParser = artistParserFactory.make(),
          ignoreArtwork = false
        ).use { tag: MediaFileTagInfo ->
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
        albumArtistId = fullInfo.albumArtistId,
        albumArt = fullInfo.albumArt,
        localAlbumArt = fullInfo.localAlbumArt
      )
    }
  }

  private fun handleEmbeddedArtwork(artwork: EmbeddedArtwork) {
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
  val labelColor = toqueColors.onSurface.copy(ContentAlpha.medium)
  val config = LocalScreenConfig.current

  Column(
    modifier = Modifier
      .verticalScroll(rememberScrollState())
      .fillMaxWidth()
      .padding(horizontal = 12.dp)
      .padding(bottom = config.getListBottomContentPadding(isExpanded = true))
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
      value = { MediaAndDescription(mediaInfo.embeddedArtwork, mediaInfo.artworkDescription) }
    )
    LabeledTextValue(
      labelRes = R.string.Comment,
      valueText = mediaInfo.comment,
      labelColor = labelColor
    )
    IdRow(mediaInfo.mediaId, mediaInfo.albumId, mediaInfo.albumArtistId, labelColor)
    LabeledTextValue(
      labelRes = R.string.AlbumArt,
      valueText = mediaInfo.albumArt.toString(),
      labelColor = labelColor
    )
    LabeledTextValue(
      labelRes = R.string.LocalAlbumArt,
      valueText = mediaInfo.localAlbumArt.toString(),
      labelColor = labelColor
    )
  }
}

@Composable
fun IdRow(mediaId: MediaId, albumId: AlbumId, albumArtistId: ArtistId, labelColor: Color) {
  Row(modifier = Modifier.fillMaxWidth()) {
    val weight = .25f
    LabeledTextValue(
      modifier = Modifier.weight(weight),
      labelRes = R.string.MediaID,
      valueText = mediaId.value.toString(),
      labelColor = labelColor
    )
    LabeledTextValue(
      modifier = Modifier.weight(weight),
      labelRes = R.string.AlbumID,
      valueText = albumId.value.toString(),
      labelColor = labelColor
    )
    LabeledTextValue(
      modifier = Modifier.weight(weight),
      labelRes = R.string.ArtistID,
      valueText = albumArtistId.value.toString(),
      labelColor = labelColor
    )
  }
}

@Composable
private fun MediaAndDescription(embeddedArtwork: Bitmap?, artworkDescription: String) {
  if (embeddedArtwork != null) {
    Column {
      Image(
        modifier = Modifier.size(100.dp),
        painter = rememberAsyncImagePainter(
          model = ImageRequest.Builder(LocalContext.current)
            .data(embeddedArtwork)
            .build(),
          contentScale = ContentScale.Fit
        ),
        contentDescription = stringResource(id = R.string.EmbeddedArtwork)
      )
      Text(text = artworkDescription, style = toqueTypography.body1)
    }
  } else {
    Text(text = stringResource(id = R.string.None), style = toqueTypography.body1)
  }
}

@Composable
private fun RatingRow(mediaInfo: MediaInfoViewModel.MediaInfo, labelColor: Color) {
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
            config = RatingBarConfig()
              .size(18.dp)
              .padding(2.dp)
              .isIndicator(true)
              .activeColor(LocalContentColor.current)
              .inactiveColor(LocalContentColor.current)
              .stepSize(StepSize.HALF)
              .style(RatingBarStyle.HighLighted),
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
            config = RatingBarConfig()
              .size(18.dp)
              .padding(2.dp)
              .isIndicator(true)
              .activeColor(LocalContentColor.current)
              .inactiveColor(LocalContentColor.current)
              .stepSize(StepSize.HALF)
              .style(RatingBarStyle.HighLighted),
            onValueChange = {},
            onRatingChanged = {},
          )
        }
      },
    )
  }
}

@Composable
private fun CountLastOccurredRow(
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
private fun TrackDiscRow(mediaInfo: MediaInfoViewModel.MediaInfo, labelColor: Color) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    LabeledTextValue(R.string.Track, numOfTotal(mediaInfo.track, mediaInfo.totalTracks), labelColor)
    LabeledTextValue(R.string.Disc, numOfTotal(mediaInfo.disc, mediaInfo.totalDiscs), labelColor)
    LabeledTextValue(R.string.Duration, mediaInfo.duration.asHourMinutesSeconds, labelColor)
    LabeledTextValue(R.string.Year, mediaInfo.year.toString(), labelColor)
  }
}

private fun numOfTotal(num: Int, totalTracks: Int): String {
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
    value = { Text(text = valueText, style = toqueTypography.body1) },
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
      style = toqueTypography.caption,
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
      style = toqueTypography.caption,
      color = labelColor
    )
    value()
  }
}

private fun dateFormatter() = DateFormat.getDateTimeInstance(
  DateFormat.MEDIUM,
  DateFormat.SHORT,
  Locale.getDefault()
)

private fun Millis.asDateTime(): String = dateFormatter().format(value)
