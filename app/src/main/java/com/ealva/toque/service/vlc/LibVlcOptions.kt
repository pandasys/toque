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

import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.service.vlc.LibVlcPrefs.Companion.SUB_AUTODETECT_PATHS

private val LOG by lazyLogger("libVlcOptions")

private const val PARM_AUDIO_TIME_STRETCH = "--audio-time-stretch"
private const val PARM_GAIN_MODE = "--audio-replay-gain-mode"
private const val PARM_GAIN_PREAMP = "--audio-replay-gain-preamp"
private const val PARM_GAIN_DEFAULT = "--audio-replay-gain-default"
private const val PARM_NETWORK_CACHING = "--network-caching"
private const val PARM_STATS = "--stats"
private const val PARM_RESAMPLER = "--audio-resampler"
private const val PARM_LOG_VERBOSE = "--log-verbose"
private const val PARM_SKIP_LOOP_FILTER = "--avcodec-skiploopfilter"
private const val PARM_SKIP_FRAME = "--avcodec-skip-frame"
private const val PARM_SKIP_IDCT = "--avcodec-skip-idct"
private const val PARM_SUBSDEC_ENCODING = "--subsdec-encoding"
private const val PARM_SUB_AUTO_DETECT_PATH = "--sub-autodetect-path"
private const val PARM_CHROMA = "--android-display-chroma"
private const val PARM_NO_AUDIO_TIME_STRETCH = "--no-audio-time-stretch"
private const val PARM_VVV_VERBOSE = "-vvv"
private const val PARM_VV_VERBOSE = "-vv"

private const val SOXR_RESAMPLER = "soxr"
private const val UGLY_RESAMPLER = "ugly"

internal const val NON_REF_FREQUENCY_TRIGGER_VALUE = 1200F
private const val OPTIONS_DEFAULT_CAPACITY = 128

fun libVlcOptions(
  prefs: LibVlcPrefs,
  vlcUtil: VlcUtil
): ArrayList<String> {
  return ArrayList<String>(OPTIONS_DEFAULT_CAPACITY).apply {
    audioTimeStretchOption(prefs)
    audioReplayGainMode(prefs)
    networkCachingOption(prefs)
    add(PARM_STATS)
    resamplerOption(vlcUtil)
    verboseModeOption(prefs)
    skipLoopFilterOption(prefs, vlcUtil)
    skipFrameAndIdctOptions(prefs)
    subsEncodingOption(prefs)
    subtitlesAutoDetectPath()
    androidWindowChromaOption(prefs)
//    dumpOptions()
  }
}

private fun getResampler(vlcUtil: VlcUtil): String =
  if (vlcUtil.machineSpecs.processors > 2) SOXR_RESAMPLER else UGLY_RESAMPLER

@Suppress("unused")
private fun ArrayList<String>.dumpOptions() {
  forEach { option ->
    LOG.i { it(option) }
  }
}

private fun ArrayList<String>.subtitlesAutoDetectPath() {
//  subtitlesDirectory.mkdirs()
  add(PARM_SUB_AUTO_DETECT_PATH)
  add(SUB_AUTODETECT_PATHS)
//  options.add("./Subtitles, ./subtitles, ./Subs, ./subs, " +
//    if (FileUtil.SUBTITLES_DIRECTORY.isDirectory) FileUtil.SUBTITLES_DIRECTORY.path else "")
}

private fun ArrayList<String>.verboseModeOption(prefs: LibVlcPrefs) {
  if (prefs.debugAndLogging) {
    add(if (prefs.enableVerboseMode()) PARM_VVV_VERBOSE else PARM_VV_VERBOSE)
  } else {
    add(PARM_LOG_VERBOSE)
    add("-1")
  }
}

private fun ArrayList<String>.resamplerOption(vlcUtil: VlcUtil) {
  add(PARM_RESAMPLER)
  add(getResampler(vlcUtil))
}

private fun ArrayList<String>.androidWindowChromaOption(prefs: LibVlcPrefs) {
  add(PARM_CHROMA)
  add(prefs.chroma().toString())
}

private fun ArrayList<String>.networkCachingOption(prefs: LibVlcPrefs) {
  add(PARM_NETWORK_CACHING)
  add(prefs.networkCachingAmount().toString())
}

private fun ArrayList<String>.subsEncodingOption(prefs: LibVlcPrefs) {
  add(PARM_SUBSDEC_ENCODING)
  add(prefs.subtitleEncoding().toString())
}

private fun ArrayList<String>.audioReplayGainMode(prefs: LibVlcPrefs) {
  add(PARM_GAIN_MODE)
  add(prefs.replayGainMode().toString())
  add(PARM_GAIN_PREAMP)
  add(prefs.replayPreamp().toString())
  add(PARM_GAIN_DEFAULT)
  add(prefs.defaultReplayGain().toString())
}

private fun ArrayList<String>.skipFrameAndIdctOptions(prefs: LibVlcPrefs) {
  val frameSkip = prefs.enableFrameSkip()
  add(PARM_SKIP_FRAME)
  add(if (frameSkip) "2" else "0")
  add(PARM_SKIP_IDCT)
  add(if (frameSkip) "2" else "0")
}

private fun ArrayList<String>.skipLoopFilterOption(
  prefs: LibVlcPrefs,
  vlcUtil: VlcUtil
) {
  add(PARM_SKIP_LOOP_FILTER)
  add(prefs.skipLoopFilter().getDeblocking(vlcUtil).toString())
}

private fun ArrayList<String>.audioTimeStretchOption(prefs: LibVlcPrefs) {
  add(if (prefs.allowTimeStretchAudio()) PARM_AUDIO_TIME_STRETCH else PARM_NO_AUDIO_TIME_STRETCH)
}

private fun SkipLoopFilter.getDeblocking(vlcUtil: VlcUtil): SkipLoopFilter {
  var ret = this
  if (this == SkipLoopFilter.Auto) {
    /*
     * Set some reasonable Deblocking defaults:
     *
     * Skip all (4) for armv6 and MIPS by default
     * Skip non-ref (1) for all armv7 more than 1.2 Ghz and more than 2 cores
     * Skip non-key (3) for all devices that don't meet anything above
     */
    val m = vlcUtil.machineSpecs
    ret = if (m.hasArmV6 && !m.hasArmV7 || m.hasMips) {
      SkipLoopFilter.All
    } else if (m.frequency >= NON_REF_FREQUENCY_TRIGGER_VALUE && m.processors > 2) {
      SkipLoopFilter.NonRef
    } else if (m.bogoMIPS >= NON_REF_FREQUENCY_TRIGGER_VALUE && m.processors > 2) {
      SkipLoopFilter.NonRef
    } else {
      SkipLoopFilter.NonKey
    }
  }
  return ret
}
