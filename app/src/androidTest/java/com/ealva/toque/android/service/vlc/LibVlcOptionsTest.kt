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

package com.ealva.toque.android.service.vlc

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ealva.toque.service.vlc.LibVlcPrefs
import com.ealva.toque.service.vlc.NON_REF_FREQUENCY_TRIGGER_VALUE
import com.ealva.toque.service.vlc.ReplayGainMode
import com.ealva.toque.service.vlc.SkipLoopFilter
import com.ealva.toque.service.vlc.VlcUtil
import com.ealva.toque.service.vlc.libVlcOptions
import com.nhaarman.expect.expect
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import org.videolan.libvlc.util.VLCUtil

private const val AUDIO_TIME_STRETCH = "--audio-time-stretch"
private const val GAIN_MODE = "--audio-replay-gain-mode"
private const val GAIN_PREAMP = "--audio-replay-gain-preamp"
private const val GAIN_DEFAULT = "--audio-replay-gain-default"
private const val NETWORK_CACHING = "--network-caching"
private const val STATS = "--stats"
private const val RESAMPLER = "--audio-resampler"
//private const val LOG_VERBOSE = "--log-verbose"
private const val SKIP_LOOP_FILTER = "--avcodec-skiploopfilter"
private const val SKIP_FRAME = "--avcodec-skip-frame"
private const val SKIP_IDCT = "--avcodec-skip-idct"
private const val SUBSDEC_ENCODING = "--subsdec-encoding"
private const val SUB_AUTO_DETECT_PATH = "--sub-autodetect-path"
private const val CHROMA = "--android-display-chroma"
private const val NO_AUDIO_TIME_STRETCH = "--no-audio-time-stretch"
private const val VV_VERBOSE = "-vv"
private const val V_VERBOSE = "-v"

private const val SOXR_RESAMPLER = "soxr"
private const val UGLY_RESAMPLER = "ugly"

@RunWith(AndroidJUnit4::class)
class LibVlcOptionsTest {
  @Suppress("DEPRECATION")
  @get:Rule
  var thrown: ExpectedException = ExpectedException.none()

  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun testLibVlcOptions() {
    val vlcUtil = VlcUtil(appCtx)
    val prefs = StubLibVlcPrefs()
    prefs.skipLoopFilter._value = SkipLoopFilter.All
    libVlcOptions(prefs, vlcUtil).let { options ->
      expect(options.size).toBe(25)
      expect(options[0]).toBe(AUDIO_TIME_STRETCH)
      expect(options[1]).toBe(GAIN_MODE)
      expect(options[2]).toBe(ReplayGainMode.None.toString())
      expect(options[3]).toBe(GAIN_PREAMP)
      expect(options[4]).toBe(prefs.replayGainPreamp().toString())
      expect(options[5]).toBe(GAIN_DEFAULT)
      expect(options[6]).toBe(prefs.defaultReplayGain().toString())
      expect(options[7]).toBe(NETWORK_CACHING)
      expect(options[8]).toBe(prefs.networkCachingAmount().toString())
      expect(options[9]).toBe(STATS)
      expect(options[10]).toBe(RESAMPLER)
      expect(options[11])
        .toBe(if (vlcUtil.machineSpecs.processors > 2) SOXR_RESAMPLER else UGLY_RESAMPLER)
      expect(options[12]).toBe(V_VERBOSE)
      expect(options[13]).toBe(SKIP_LOOP_FILTER)
      // expect(options[14]).toBe() depends on machine specs
      expect(options[15]).toBe(SKIP_FRAME)
      expect(options[16]).toBe("0")
      expect(options[17]).toBe(SKIP_IDCT)
      expect(options[18]).toBe("0")
      expect(options[19]).toBe(SUBSDEC_ENCODING)
      expect(options[20]).toBe(prefs.subtitleEncoding().toString())
      expect(options[21]).toBe(SUB_AUTO_DETECT_PATH)
      expect(options[22]).toBe(LibVlcPrefs.SUB_AUTODETECT_PATHS)
      expect(options[23]).toBe(CHROMA)
      expect(options[24]).toBe(prefs.chroma().toString())
    }
  }

  @Test
  fun testLibVlcOptionsWith2ProcessorsAndNoTimeStretch() {
    val prefs = StubLibVlcPrefs()
    prefs.allowTimeStretchAudio._value = false
    libVlcOptions(
      prefs,
      VlcUtilStub(
        VLCUtil.MachineSpecs().apply {
          processors = 2
        }
      )
    ).let { options ->
      expect(options.size).toBe(25)
      expect(options[0]).toBe(NO_AUDIO_TIME_STRETCH)
      expect(options[10]).toBe(RESAMPLER)
      expect(options[11]).toBe(UGLY_RESAMPLER)
    }
  }

  @Test
  fun testLibVlcOptionsVerboseMode() {
    val prefs = StubLibVlcPrefs()
    prefs.enableVerboseMode._value = true
    libVlcOptions(prefs, VlcUtil(appCtx)).let { options ->
      expect(options.size).toBe(25)
      expect(options[12]).toBe(VV_VERBOSE)
    }

    prefs.enableVerboseMode._value = false
    libVlcOptions(prefs, VlcUtil(appCtx)).let { options ->
      expect(options.size).toBe(25)
      expect(options[12]).toBe(V_VERBOSE)
    }
  }

  @Test
  fun testLibVlcOptionsSpecificSkipLoopFilters() {
    val prefs = StubLibVlcPrefs()
    prefs.skipLoopFilter._value = SkipLoopFilter.NonKey
    libVlcOptions(prefs, VlcUtil(appCtx)).let { options ->
      expect(options.size).toBe(25)
      expect(options[13]).toBe(SKIP_LOOP_FILTER)
      expect(options[14]).toBe(prefs.skipLoopFilter().toString())
    }
  }

  @Test
  fun testLibVlcOptionsAutoSkipLoopWithVaryingChipsAndFrequency() {
    try {
      val prefs = StubLibVlcPrefs()
      prefs.skipLoopFilter._value = SkipLoopFilter.Auto
      expectFilterGivenMachineSpecs(
        prefs,
        SkipLoopFilter.All,
        VLCUtil.MachineSpecs().apply {
          hasArmV6 = true
        }
      )

      expectFilterGivenMachineSpecs(
        prefs,
        SkipLoopFilter.All,
        VLCUtil.MachineSpecs().apply {
          hasMips = true
        }
      )

      expectFilterGivenMachineSpecs(
        prefs,
        SkipLoopFilter.NonRef,
        VLCUtil.MachineSpecs().apply {
          hasArmV7 = true
          frequency = NON_REF_FREQUENCY_TRIGGER_VALUE
          processors = 4
        }
      )

      expectFilterGivenMachineSpecs(
        prefs,
        SkipLoopFilter.NonRef,
        VLCUtil.MachineSpecs().apply {
          hasArmV7 = true
          bogoMIPS = NON_REF_FREQUENCY_TRIGGER_VALUE
          processors = 4
        }
      )

      expectFilterGivenMachineSpecs(
        prefs,
        SkipLoopFilter.NonKey,
        VLCUtil.MachineSpecs().apply {
          hasArmV7 = true
          bogoMIPS = NON_REF_FREQUENCY_TRIGGER_VALUE
          processors = 2
        }
      )
    } catch (e: Exception) {
      println(e)
    }
  }

  private fun expectFilterGivenMachineSpecs(
    prefs: StubLibVlcPrefs,
    skipLoopFilter: SkipLoopFilter,
    machineSpecs: VLCUtil.MachineSpecs
  ) {
    libVlcOptions(
      prefs,
      VlcUtilStub(machineSpecs)
    ).let { options ->
      options.forEach { option -> println(option) }
      expect(options.size).toBe(25)
      expect(options[13]).toBe(SKIP_LOOP_FILTER)
      expect(options[14]).toBe(skipLoopFilter.toString())
    }
  }
}

class VlcUtilStub(private val specs: VLCUtil.MachineSpecs) : VlcUtil {
  override val machineSpecs: VLCUtil.MachineSpecs
    get() = specs
}
