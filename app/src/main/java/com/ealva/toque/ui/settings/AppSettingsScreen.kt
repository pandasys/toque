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

package com.ealva.toque.ui.settings

import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import com.ealva.comppref.pref.CallbackSettingItem
import com.ealva.comppref.pref.DefaultSettingMakers
import com.ealva.comppref.pref.ListSettingItem
import com.ealva.comppref.pref.SettingItem
import com.ealva.comppref.pref.SettingsScreen
import com.ealva.comppref.pref.SliderSettingItem
import com.ealva.comppref.pref.SwitchSettingItem
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.prefstore.store.PreferenceStore
import com.ealva.prefstore.store.PreferenceStoreSingleton
import com.ealva.toque.R
import com.ealva.toque.audioout.AudioOutputModule
import com.ealva.toque.common.Millis
import com.ealva.toque.common.MillisRange
import com.ealva.toque.common.Volume
import com.ealva.toque.common.VolumeRange
import com.ealva.toque.common.fetch
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefs.Companion.DUCK_VOLUME_RANGE
import com.ealva.toque.prefs.AppPrefs.Companion.IGNORE_FILES_RANGE
import com.ealva.toque.prefs.AppPrefs.Companion.MEDIA_FADE_RANGE
import com.ealva.toque.prefs.AppPrefs.Companion.PLAY_PAUSE_FADE_RANGE
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.prefs.DuckAction
import com.ealva.toque.prefs.EndOfQueueAction
import com.ealva.toque.prefs.PlayUpNextAction
import com.ealva.toque.prefs.ScrobblerPackage
import com.ealva.toque.service.vlc.LibVlcPrefs
import com.ealva.toque.service.vlc.LibVlcPrefsSingleton
import com.ealva.toque.service.vlc.ReplayGainMode
import com.ealva.toque.ui.settings.SettingScreenKeys.AdvancedSettings
import com.ealva.toque.ui.settings.SettingScreenKeys.ArtworkSettings
import com.ealva.toque.ui.settings.SettingScreenKeys.AudioSettings
import com.ealva.toque.ui.settings.SettingScreenKeys.FadeAudioSettings
import com.ealva.toque.ui.settings.SettingScreenKeys.LibrarySettings
import com.ealva.toque.ui.settings.SettingScreenKeys.ListsLookAndFeel
import com.ealva.toque.ui.settings.SettingScreenKeys.LookAndFeel
import com.ealva.toque.ui.settings.SettingScreenKeys.MediaScannerSettings
import com.ealva.toque.ui.settings.SettingScreenKeys.NowPlayingLookAndFeel
import com.ealva.toque.ui.settings.SettingScreenKeys.PrimarySettings
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding
import com.google.accompanist.pager.ExperimentalPagerApi
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestackcomposeintegration.core.LocalBackstack
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val LOG by lazyLogger(AppSettingsScreen::class)

@Immutable
@Parcelize
data class SettingScreenKey(
  @StringRes val title: Int,
  @StringRes val subtitle: Int = 0
) : Parcelable

object SettingScreenKeys {
  val PrimarySettings = SettingScreenKey(R.string.Settings)
  val LookAndFeel = SettingScreenKey(R.string.LookandFeel, R.string.Settings)
  val ListsLookAndFeel = SettingScreenKey(R.string.Lists, R.string.LAndFSettings)
  val NowPlayingLookAndFeel = SettingScreenKey(R.string.NowPlaying, R.string.LAndFSettings)
  val LibrarySettings = SettingScreenKey(R.string.Library, R.string.Settings)
  val MediaScannerSettings = SettingScreenKey(R.string.MediaScanner, R.string.LibrarySettings)
  //val MediaFileTag = SettingScreenKey("Media File Tag", "Media Scanner - Library - Settings")
  val ArtworkSettings = SettingScreenKey(R.string.Artwork, R.string.Settings)
  val AudioSettings = SettingScreenKey(R.string.Audio, R.string.Settings)
  val FadeAudioSettings = SettingScreenKey(R.string.Fade, R.string.AudioSettings)
  //val SocialSettings = SettingScreenKey(R.string.Social, R.string.Settings)
  val AdvancedSettings = SettingScreenKey(R.string.Advanced, R.string.Settings)
}

@Immutable
@Parcelize
data class AppSettingsScreen(private val key: SettingScreenKey) : ComposeKey() {

  @OptIn(ExperimentalPagerApi::class)
  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val appPrefsSingleton = rememberService<AppPrefsSingleton>("AppPrefs")
    val libVlcPrefsSingleton = rememberService<LibVlcPrefsSingleton>("LibVlcPrefs")

    val backstack = LocalBackstack.current
    val goBack: () -> Unit = { backstack.goBack() }
    if (key == AdvancedSettings) {
      ToqueSettingsScreen(
        title = stringResource(id = key.title),
        subtitle = if (key.subtitle > 0) stringResource(id = key.subtitle) else null,
        prefsSingleton = libVlcPrefsSingleton,
        makeSettings = { prefs -> makeItems(key, prefs) },
        back = goBack
      )
    } else {
      ToqueSettingsScreen(
        title = stringResource(id = key.title),
        subtitle = if (key.subtitle > 0) stringResource(id = key.subtitle) else null,
        prefsSingleton = appPrefsSingleton,
        makeSettings = { prefs -> makeItems(key, backstack, prefs) },
        back = goBack
      )
    }
  }

  private fun makeItems(
    key: SettingScreenKey,
    backstack: Backstack,
    prefs: AppPrefs
  ): List<SettingItem> =
    when (key) {
      PrimarySettings -> makePrimaryItems(backstack)
      LookAndFeel -> makeLookAndFeelItems(backstack)
      ListsLookAndFeel -> makeListsLookAndFeelItems(prefs)
      NowPlayingLookAndFeel -> makeNowPlayingItems()
      LibrarySettings -> makeLibraryItems(backstack, prefs)
      MediaScannerSettings -> makeMediaScannerItems(prefs)
      //MediaFileTag -> makeMediaFileTagItems(prefs)
      ArtworkSettings -> makeArtworkItems()
      AudioSettings -> makeAudioSettingsItems(backstack, prefs)
      FadeAudioSettings -> makeFadeAudioSettings(prefs)
      //SocialSettings -> makeSocialSettings(prefs)
      else -> {
        LOG.e { it("Unrecognized Setting Screen key %s", key) }
        emptyList()
      }
    }

  private fun makeItems(
    key: SettingScreenKey,
    prefs: LibVlcPrefs
  ): List<SettingItem> =
    when (key) {
      AdvancedSettings -> makeAdvancedSettings(prefs)
      else -> {
        LOG.e { it("Unrecognized Setting Screen key %s", key) }
        emptyList()
      }
    }

  private fun makePrimaryItems(backstack: Backstack) = listOf(
    CallbackSettingItem(
      title = fetch(R.string.LookandFeel),
      summary = fetch(R.string.UserInterfaceOptions),
      iconDrawable = R.drawable.ic_eye,
      onClick = { backstack.goTo(AppSettingsScreen(LookAndFeel)) }
    ),
    CallbackSettingItem(
      title = fetch(R.string.Library),
      summary = fetch(R.string.LibraryAndFolderOptions),
      iconDrawable = R.drawable.ic_library_music,
      onClick = { backstack.goTo(AppSettingsScreen(LibrarySettings)) }
    ),
    CallbackSettingItem(
      title = fetch(R.string.Artwork),
      summary = fetch(R.string.AlbumArtworkOptions),
      iconDrawable = R.drawable.ic_photo_library,
      onClick = { backstack.goTo(AppSettingsScreen(ArtworkSettings)) }
    ),
    CallbackSettingItem(
      title = fetch(R.string.Audio),
      summary = fetch(R.string.AudioHeadsetOptions),
      iconDrawable = R.drawable.ic_headphones,
      onClick = { backstack.goTo(AppSettingsScreen(AudioSettings)) }
    ),
    //CallbackSettingItem(
    //  title = fetch(R.string.Social),
    //  summary = fetch(R.string.ScrobbleEtc),
    //  iconDrawable = R.drawable.ic_groups,
    //  onClick = { backstack.goTo(AppSettingsScreen(SocialSettings)) }
    //),
    CallbackSettingItem(
      title = fetch(R.string.Advanced),
      summary = fetch(R.string.AdvancedSettingsAnd),
      iconDrawable = R.drawable.ic_wrench_outline,
      onClick = { backstack.goTo(AppSettingsScreen(AdvancedSettings)) }
    ),
  )

  private fun makeFadeAudioSettings(prefs: AppPrefs) = listOf(
    SwitchSettingItem(
      preference = prefs.autoAdvanceFade,
      title = fetch(R.string.AutoAdvanceCrossFade),
      summary = fetch(R.string.CrossFadeSound),
      offSummary = fetch(R.string.DoNotCrossFade),
      singleLineTitle = true,
    ),
    SliderSettingItem(
      preference = prefs.autoAdvanceFadeLength,
      title = fetch(R.string.AutoAdvanceCrossFadeLength),
      enabled = prefs.autoAdvanceFade(),
      singleLineTitle = true,
      summary = fetch(R.string.TotalCrossFadeDuration),
      steps = 0,
      valueRepresentation = { floatValue -> "${floatValue.toMillisInSeconds().value} ms" },
      valueRange = MEDIA_FADE_RANGE.toFloatRange(),
      floatToType = { value -> value.toMillisInSeconds() },
      typeToFloat = millisToFloat
    ),
    SwitchSettingItem(
      preference = prefs.manualChangeFade,
      title = fetch(R.string.ManualChangeCrossFade),
      summary = fetch(R.string.CrossFadeSound),
      offSummary = fetch(R.string.DoNotCrossFade),
      singleLineTitle = true,
    ),
    SliderSettingItem(
      preference = prefs.manualChangeFadeLength,
      title = fetch(R.string.ManualChangeCrossFadeLength),
      enabled = prefs.manualChangeFade(),
      singleLineTitle = true,
      summary = fetch(R.string.TotalCrossFadeDuration),
      steps = 0,
      valueRepresentation = { floatValue -> "${floatValue.toMillisInSeconds().value} ms" },
      valueRange = MEDIA_FADE_RANGE.toFloatRange(),
      floatToType = { value -> value.toMillisInSeconds() },
      typeToFloat = millisToFloat
    ),
    SwitchSettingItem(
      preference = prefs.playPauseFade,
      title = fetch(R.string.PlayPauseFade),
      summary = fetch(R.string.FadeSound),
      offSummary = fetch(R.string.DoNotFade),
      singleLineTitle = true,
    ),
    SliderSettingItem(
      preference = prefs.playPauseFadeLength,
      title = fetch(R.string.PlayPauseFadeLength),
      enabled = prefs.playPauseFade(),
      singleLineTitle = true,
      summary = fetch(R.string.FadeInOutDuration),
      steps = 0,
      valueRepresentation = { floatValue -> "${floatValue.toMillisInSeconds().value} ms" },
      valueRange = PLAY_PAUSE_FADE_RANGE.toFloatRange(),
      floatToType = { value -> value.toMillisInSeconds() },
      typeToFloat = millisToFloat
    ),
  )

  private fun makeAudioSettingsItems(backstack: Backstack, prefs: AppPrefs) = listOf(
    CallbackSettingItem(
      title = fetch(R.string.Fade),
      summary = fetch(R.string.FadeAndCrossfade),
      iconDrawable = R.drawable.ic_hearing,
      onClick = { backstack.goTo(AppSettingsScreen(FadeAudioSettings)) }
    ),
    SwitchSettingItem(
      preference = prefs.playOnWiredConnection,
      title = fetch(R.string.PlayOnWiredConnection),
      summary = fetch(R.string.StartPlayerOnWiredconnection),
      offSummary = fetch(R.string.DoNotStartOnConnection),
      singleLineTitle = true,
    ),
    SwitchSettingItem(
      preference = prefs.playOnBluetoothConnection,
      title = fetch(R.string.PlayOnBluetoothConnection),
      summary = fetch(R.string.StartPlayerOnBluetoothConnection),
      offSummary = fetch(R.string.DoNotStartOnConnection),
      singleLineTitle = true,
    ),
    ListSettingItem(
      preference = prefs.duckAction,
      title = fetch(R.string.AudioFocusTransientLoss),
      singleLineTitle = true,
      enabled = true,
      dialogItems = mapOf(
        fetch(R.string.Duck) to DuckAction.Duck,
        fetch(R.string.Pause) to DuckAction.Pause,
        fetch(R.string.DoNothing) to DuckAction.None
      )
    ),
    SliderSettingItem(
      preference = prefs.duckVolume,
      title = fetch(R.string.DuckVolume),
      enabled = prefs.duckAction() === DuckAction.Duck,
      singleLineTitle = true,
      summary = fetch(R.string.VolumeDuringTransientLoss),
      steps = 0,
      valueRepresentation = { floatValue -> floatValue.toVolume().value.toString() },
      valueRange = DUCK_VOLUME_RANGE.toFloatRange(),
      floatToType = { value -> value.toVolume() },
      typeToFloat = volumeToFloat
    ),
  )

  private fun makeLookAndFeelItems(
    backstack: Backstack
  ): List<SettingItem> = listOf(
    CallbackSettingItem(
      title = fetch(R.string.Lists),
      summary = fetch(R.string.ListItemActionsOptions),
      iconDrawable = R.drawable.ic_list,
      onClick = { backstack.goTo(AppSettingsScreen(ListsLookAndFeel)) }
    ),
    CallbackSettingItem(
      title = fetch(R.string.NowPlaying),
      summary = fetch(R.string.NowPlayingScreenOptions),
      iconDrawable = R.drawable.ic_presentation_play,
      onClick = { backstack.goTo(AppSettingsScreen(NowPlayingLookAndFeel)) }
    ),
  )

  private fun makeListsLookAndFeelItems(prefs: AppPrefs): List<SettingItem> = listOf(
    ListSettingItem(
      preference = prefs.playUpNextAction,
      title = fetch(R.string.UpNextActionOnPlay),
      singleLineTitle = true,
      enabled = true,
      dialogItems = mapOf(
        PlayUpNextAction.ClearUpNext.titleValuePair,
        PlayUpNextAction.PlayNext.titleValuePair,
        PlayUpNextAction.Prompt.titleValuePair
      )
    ),
    SwitchSettingItem(
      preference = prefs.goToNowPlaying,
      title = fetch(R.string.GoToNowPlaying),
      summary = fetch(R.string.GoToNowPlayingAfterSelection),
      offSummary = fetch(R.string.StayInListAfterSelection),
      singleLineTitle = true,
    ),
    SwitchSettingItem(
      preference = prefs.allowDuplicates,
      title = fetch(R.string.AllowDuplicates),
      summary = fetch(R.string.AllowDuplicatesInUpNext),
      offSummary = fetch(R.string.DuplicatesNotAllowedInQueue),
      singleLineTitle = true,
    ),
  )

  private fun makeNowPlayingItems(): List<SettingItem> = listOf(

  )

  private fun makeLibraryItems(backstack: Backstack, prefs: AppPrefs): List<SettingItem> = listOf(
    CallbackSettingItem(
      title = fetch(R.string.MediaScanner),
      summary = fetch(R.string.LibraryScannerSettings),
      iconDrawable = R.drawable.ic_refresh,
      onClick = { backstack.goTo(AppSettingsScreen(MediaScannerSettings)) }
    ),
    ListSettingItem(
      preference = prefs.endOfQueueAction,
      title = fetch(R.string.EndOfQueueAction),
      singleLineTitle = true,
      enabled = true,
      dialogItems = mapOf(
        EndOfQueueAction.PlayNextList.titleValuePair,
        EndOfQueueAction.ShuffleNextList.titleValuePair,
        EndOfQueueAction.Stop.titleValuePair
      )
    ),
    SliderSettingItem(
      preference = prefs.markPlayedPercentage,
      title = "Played Threshold",
      enabled = true,
      singleLineTitle = true,
      summary = "Played count incremented after",
      steps = 100,
      valueRepresentation = { value -> "${(value * 100).roundToInt().coerceIn(0..100)}%" },
      valueRange = 0F..1.0F,
      floatToType = { value -> value.toDouble() },
      typeToFloat = { it.toFloat() }
    ),
    ListSettingItem(
      preference = prefs.scrobbler,
      title = fetch(R.string.SelectScrobbler),
      singleLineTitle = true,
      enabled = true,
      dialogItems = mapOf(
        ScrobblerPackage.None.titleValuePair,
        ScrobblerPackage.LastFm.titleValuePair,
        ScrobblerPackage.SimpleLastFm.titleValuePair
      )
    ),
  )

  private fun makeMediaScannerItems(
    prefs: AppPrefs
  ): List<SettingItem> = listOf(
    SwitchSettingItem(
      preference = prefs.ignoreSmallFiles,
      title = "Ignore Small Files",
      summary = "Ignore small files, possibly ringtones, etc.",
      offSummary = "Include all files in scan",
      singleLineTitle = true,
    ),
    SliderSettingItem(
      preference = prefs.ignoreThreshold,
      title = "Ignore Files Smaller Than",
      enabled = prefs.ignoreSmallFiles(),
      singleLineTitle = true,
      summary = "Smallest files to scan",
      steps = 0,
      valueRepresentation = { floatValue -> floatValue.roundToSeconds().toString() },
      valueRange = IGNORE_FILES_RANGE.toFloatRange(),
      floatToType = { value -> Millis(value.roundToLong()) },
      typeToFloat = millisToFloat
    ),
    //CallbackSettingItem(
    //  title = "Media File Tag",
    //  summary = "Scanner tag field options",
    //  iconDrawable = R.drawable.ic_refresh,
    //  onClick = { backstack.goTo(AppSettingsScreen(MediaFileTag)) }
    //)
    SwitchSettingItem(
      prefs.readTagRating,
      title = "Read Tag Rating",
      summary = "Read rating field in song file",
      offSummary = "Ignore rating in song file",
      singleLineTitle = true
    )
  )

  //private fun makeMediaFileTagItems(prefs: AppPrefs): List<SettingItem> = listOf(
  //)

  private fun makeArtworkItems(): List<SettingItem> = listOf(

  )

  //private fun makeSocialSettings(prefs: AppPrefs): List<SettingItem> = listOf(
  //)

  private fun makeAdvancedSettings(prefs: LibVlcPrefs): List<SettingItem> = listOf(
    ListSettingItem(
      preference = prefs.audioOutputModule,
      title = "Audio Output",
      singleLineTitle = true,
      enabled = true,
      dialogItems = mapOf(
        AudioOutputModule.AudioTrack.titleViewPair,
        AudioOutputModule.OpenSlEs.titleViewPair
      )
    ),
    ListSettingItem(
      preference = prefs.replayGainMode,
      title = "Replay Gain Mode",
      singleLineTitle = true,
      enabled = true,
      dialogItems = mapOf(
        ReplayGainMode.None.titleValuePair,
        ReplayGainMode.Album.titleValuePair,
        ReplayGainMode.Track.titleValuePair
      )
    ),
  )
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun <T : PreferenceStore<T>> ToqueSettingsScreen(
  title: String,
  subtitle: String? = null,
  prefsSingleton: PreferenceStoreSingleton<T>,
  makeSettings: (T) -> List<SettingItem>,
  back: () -> Unit
) {
  val scope = rememberCoroutineScope()
  val settingsState: MutableState<List<SettingItem>> = remember {
    mutableStateOf(listOf())
  }
  LaunchedEffect(settingsState) {
    scope.launch {
      prefsSingleton
        .asFlow()
        .collect { prefs -> settingsState.value = makeSettings(prefs) }
    }
  }
  Column(
    modifier = Modifier
      .navigationBarsPadding()
      .statusBarsPadding()
      .padding(bottom = 92.dp)
  ) {
    TopAppBar(
      title = { if (subtitle == null) AppBarTitle(title) else TitleAndSubtitle(title, subtitle) },
      backgroundColor = Color.Transparent,
      modifier = Modifier.fillMaxWidth(),
      navigationIcon = {
        IconButton(onClick = back) {
          Image(
            painter = rememberImagePainter(data = R.drawable.ic_arrow_left),
            contentDescription = "Toggle Equalizer",
            modifier = Modifier.size(26.dp)
          )
        }
      }
    )
    SettingsScreen(items = settingsState.value, makers = SettingMaker())
  }
}

@Composable
private fun AppBarTitle(title: String) {
  Text(
    text = title,
    textAlign = TextAlign.Start,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis
  )
}

@Composable
private fun TitleAndSubtitle(title: String, subtitle: String) {
  Column {
    Text(
      text = title,
      textAlign = TextAlign.Start,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
    Text(
      text = subtitle,
      style = MaterialTheme.typography.subtitle2,
      textAlign = TextAlign.Start,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

private fun Float.roundToSeconds(): Long = div(1000).roundToLong()
private val millisToFloat: (Millis) -> Float = { it.value.toFloat() }
private fun Float.toMillisInSeconds() = Millis((this / 100).roundToLong() * 100)

@JvmName("millisToFloatRange")
private fun MillisRange.toFloatRange(): ClosedFloatingPointRange<Float> =
  start.value.toFloat()..endInclusive.value.toFloat()

private val volumeToFloat: (Volume) -> Float = { it.value.toFloat() }
private fun Float.toVolume() =
  Volume(roundToInt()).coerceIn(DUCK_VOLUME_RANGE)

@JvmName("volumeToFloatRange")
private fun VolumeRange.toFloatRange(): ClosedFloatingPointRange<Float> =
  start.value.toFloat()..endInclusive.value.toFloat()

private class SettingMaker : DefaultSettingMakers() {
  @Composable
  override fun Switch(isChecked: Boolean, onClicked: ((Boolean) -> Unit)?, isEnabled: Boolean) {
    androidx.compose.material.Switch(
      checked = isChecked,
      onCheckedChange = onClicked,
      enabled = isEnabled,
      colors = SwitchDefaults.colors(uncheckedThumbColor = Color.LightGray)
    )
  }
}

val PlayUpNextAction.titleValuePair: Pair<String, PlayUpNextAction>
  get() = Pair(fetch(titleRes), this)

val EndOfQueueAction.titleValuePair: Pair<String, EndOfQueueAction>
  get() = Pair(fetch(titleRes), this)

val ScrobblerPackage.titleValuePair: Pair<String, ScrobblerPackage>
  get() = Pair(fetch(titleRes), this)

val ReplayGainMode.titleValuePair: Pair<String, ReplayGainMode>
  get() = Pair(fetch(titleRes), this)

val AudioOutputModule.titleViewPair: Pair<String, AudioOutputModule>
  get() = Pair(fetch(titleRes), this)
/*
          Pair(R.string.root_settings_pref_key, cmd_cog),
          Pair(R.string.look_and_feel_pref_key, cmd_eye),
          Pair(R.string.lists_look_and_feel_pref_key, cmd_view_list),
          Pair(R.string.now_playing_look_and_feel_pref_key, cmd_presentation_play),
          Pair(R.string.toasts_look_and_feel_pref_key, cmd_message_alert),
          Pair(R.string.library_pref_key, cmd_music_box_multiple),
          Pair(R.string.media_scanner_library_pref_key, cmd_refresh),
          Pair(R.string.when_to_scan_pref_key, cmd_alarm_check),
          Pair(R.string.scan_media_file_tag_pref_key, cmd_tag_outline),
          Pair(R.string.album_art_pref_key, cmd_image_multiple),
          Pair(R.string.audio_pref_key, cmd_headphones),
          Pair(R.string.replay_gain_pref_key, cmd_volume_high),
          Pair(R.string.fade_pref_key, cmd_elevation_rise),
          Pair(R.string.social_pref_key, cmd_earth),
          Pair(R.string.about_pref_key, cmd_information_outline),
          Pair(R.string.advanced_audio_pref_key, cmd_wrench),
          Pair(R.string.rate_alva_player_pref_key, cmd_star),
          Pair(R.string.advanced_never_ask_pref_key, GoogleMaterial.Icon.gmd_warning)
 */
