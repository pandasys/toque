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
package com.ealva.toque.service.media

import com.ealva.toque.persist.HasConstId
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap
import org.videolan.libvlc.util.Extensions

enum class MediaFormat(
  override val id: Int,
  val mediaType: MediaType,
  private val types: Array<String>,
  private val extensions: Array<String>
) : HasConstId {
  Unknown(1, MediaType.Unknown, emptyArray(), emptyArray()),

  ThirdGenAudio(2, MediaType.Audio, arrayOf("audio/3gpp"), arrayOf("3ga")),
  DolbyDigitalA52(3, MediaType.Audio, emptyArray(), arrayOf("a52")),
  DolbyDigitalAc3(
    4,
    MediaType.Audio,
    arrayOf("audio/ac3", "audio/aac-adts", "audio/x-aac", "audio/aac"),
    arrayOf("ac3", "aac")
  ),
  AudioDataTransport(5, MediaType.Audio, emptyArray(), arrayOf("adt", "adts")),
  AudioInterchange(6, MediaType.Audio, arrayOf("audio/x-aiff"), arrayOf("aif", "aifc", "aiff")),
  AdaptiveMultiRate(7, MediaType.Audio, arrayOf("audio/amr"), arrayOf("amr")),
  DvdAudioObject(8, MediaType.Audio, emptyArray(), arrayOf("aob")),
  AvsPluginEffects(9, MediaType.Audio, arrayOf("audio/x-ape"), arrayOf("ape")),
  AdaptiveMultiRateWideBand(10, MediaType.Audio, arrayOf("audio/amr-wb"), arrayOf("awb")),
  CoreAudio(11, MediaType.Audio, emptyArray(), arrayOf("caf")),
  DigitalTheaterSystems(12, MediaType.Audio, emptyArray(), arrayOf("dts")),
  Flac(13, MediaType.Audio, arrayOf("audio/flac", "application/x-flac"), arrayOf("flac")),
  Mp3(14, MediaType.Audio, arrayOf("audio/mpeg"), arrayOf("mp3", "mp1", "mp2", "mp2a")),
  MpegAudio(
    15,
    MediaType.Audio,
    arrayOf("audio/mp4", "audio/mpeg"),
    arrayOf("m4a", "m4b", "m4p", "mpa", "mpga")
  ),
  ImpulseTracker(16, MediaType.Audio, emptyArray(), arrayOf("it")),
  Midi(17, MediaType.Audio, arrayOf("audio/midi"), arrayOf("rtx", "mid", "rmi")),
  MatroskaAudio(18, MediaType.Audio, arrayOf("audio/x-matroska"), arrayOf("mka")),
  MeridianLossless(19, MediaType.Audio, arrayOf("application/vnd.dolby.mlp"), arrayOf("mlp")),
  AmigaMusicModule(20, MediaType.Audio, emptyArray(), arrayOf("mod")),
  OggVorbisAudio(21, MediaType.Audio, arrayOf("audio/ogg"), arrayOf("oga", "ogg", "opus", "spx")),
  SonyOpenMg(22, MediaType.Audio, emptyArray(), arrayOf("oma")),
  RealAudio(
    24,
    MediaType.Audio,
    arrayOf("audio/x-pn-realaudio", "audio/x-realaudio"),
    arrayOf("ra", "ram")
  ),
  ScreamTracker3(26, MediaType.Audio, emptyArray(), arrayOf("s3m")),
  TrueAudio(27, MediaType.Audio, emptyArray(), arrayOf("tta")),
  CreativeLabs(28, MediaType.Audio, emptyArray(), arrayOf("voc")),
  TwinVq(29, MediaType.Audio, emptyArray(), arrayOf("vqf")),
  SonyWave64(30, MediaType.Audio, emptyArray(), arrayOf("w64")),
  WaveAudio(31, MediaType.Audio, arrayOf("audio/x-wav"), arrayOf("wav")),
  WindowsMediaAudio(32, MediaType.Audio, arrayOf("audio/x-ms-wma"), arrayOf("wma")),
  WavePack(33, MediaType.Audio, emptyArray(), arrayOf("wv")),
  SimsAudio(34, MediaType.Audio, emptyArray(), arrayOf("xa")),
  FastTracker2(35, MediaType.Audio, emptyArray(), arrayOf("xm")),
  MusePack(36, MediaType.Audio, arrayOf("chemical/x-mopac-input"), arrayOf("mop", "mpc")),
  AppleLossless(37, MediaType.Audio, emptyArray(), arrayOf("alac")),

  Asx(1000, MediaType.Playlist, arrayOf("video/x-ms-asf"), arrayOf("asx")),
  M3u(1001, MediaType.Playlist, arrayOf("audio/x-mpegurl"), arrayOf("m3u")),
  M3u8(
    1002,
    MediaType.Playlist,
    arrayOf("application/x-mpegurl", "application/vnd.apple.mpegurl"),
    arrayOf("m3u8")
  ),
  Pls(1003, MediaType.Playlist, arrayOf("audio/x-scpls"), arrayOf("pls")),
  Xspf(1004, MediaType.Playlist, arrayOf("application/xspf+xml"), arrayOf("xspf")),

  Jpeg(1100, MediaType.Image, arrayOf("image/jpeg"), arrayOf("jpg", "jpeg")),
  Gif(1101, MediaType.Image, arrayOf("image/gif"), arrayOf("gif")),
  Png(1102, MediaType.Image, arrayOf("image/png"), arrayOf("png")),

  ThirdGenPP(
    2000,
    MediaType.Video,
    arrayOf("video/3gpp", "application/3gpp", "application/vnd.3gpp"),
    arrayOf("3gp", "3gpp")
  ),
  ThirdGenPP2(
    2001,
    MediaType.Video,
    arrayOf("video/3gpp2", "application/3gpp2", "application/vnd.3gpp2"),
    arrayOf("3g2", "3gp2")
  ),
  Iso(2003, MediaType.Video, arrayOf("application/x-iso9660-image"), arrayOf("iso", "udf")),
  Nut(2004, MediaType.Video, emptyArray(), arrayOf("nut")),
  NuppelVideo(2005, MediaType.Video, emptyArray(), arrayOf("nuv")),
  TopfieldPVR(2006, MediaType.Video, emptyArray(), arrayOf("rec")),
  DigitalVideo(2007, MediaType.Video, arrayOf("video/dv"), arrayOf("dv")),
  RealMediaVideo(2008, MediaType.Video, arrayOf("audio/x-pn-realaudio"), arrayOf("rm", "rmvb")),
  Mpeg(
    2009,
    MediaType.Video,
    arrayOf("video/mpeg"),
    arrayOf("mpeg", "mpeg2", "mpg", "mpe", "m2v", "m1v", "mp2v", "mpv2", "tts", "mpeg1")
  ),
  Mpeg4(
    2016,
    MediaType.Video,
    arrayOf("video/mp4", "video/m4v", "application/mp4"),
    arrayOf("mp4", "mpeg4", "m4v", "mp4v")
  ),
  BBCDirac(2010, MediaType.Video, emptyArray(), arrayOf("drc")),
  GeneralExchange(2011, MediaType.Video, emptyArray(), arrayOf("gxf")),
  OGG(
    2012,
    MediaType.Video,
    arrayOf("video/ogg", "application/ogg", "application/x-ogg"),
    arrayOf("ogm", "ogv", "ogx")
  ),
  QuickTime(2013, MediaType.Video, arrayOf("video/quicktime"), arrayOf("mov")),
  BluRay(2014, MediaType.Video, arrayOf("video/mp2t"), arrayOf("m2ts")),
  Matroska(2017, MediaType.Video, arrayOf("video/x-matroska"), arrayOf("mkv")),
  MXF(2018, MediaType.Video, arrayOf("application/mxf"), arrayOf("mxf")),
  MXG(2019, MediaType.Video, emptyArray(), arrayOf("mxg")),
  Divx(2020, MediaType.Video, arrayOf("video/divx"), arrayOf("divx")),
  AdobeFlashVideo(2021, MediaType.Video, arrayOf("video/x-flv"), arrayOf("flv", "f4v")),
  AVCHD(2022, MediaType.Video, arrayOf("video/mp2t"), arrayOf("mts")),
  HDV(2023, MediaType.Video, arrayOf("video/mp2ts"), arrayOf("m2t")),
  MTV(2024, MediaType.Video, emptyArray(), arrayOf("mtv")),
  WEBMVideo(2025, MediaType.Video, arrayOf("video/webm"), arrayOf("webm")),
  DVD(2026, MediaType.Video, emptyArray(), arrayOf("vro")),
  H264(2027, MediaType.Video, emptyArray(), arrayOf("h264")),
  WindowsTV(2028, MediaType.Video, emptyArray(), arrayOf("wtv")),
  Expression(2029, MediaType.Video, emptyArray(), arrayOf("xesc")),
  JVCEverio(2030, MediaType.Video, emptyArray(), arrayOf("tod")),
  DVDVob(2031, MediaType.Video, emptyArray(), arrayOf("vob")),
  AnimeMusicVideo(2032, MediaType.Video, emptyArray(), arrayOf("amv")),
  NullSoftVideo(2033, MediaType.Video, emptyArray(), arrayOf("nsv")),
  AVI(2034, MediaType.Video, arrayOf("video/avi"), arrayOf("avi")),
  WindowsMedia(
    2035,
    MediaType.Video,
    arrayOf("video/x-ms-wmv", "video/x-ms-asf", "video/x-ms-wm"),
    arrayOf("wmv", "wm")
  ),
  GoogleVideo(2036, MediaType.Video, emptyArray(), arrayOf("gvi")),
  IISSmoothStreaming(2037, MediaType.Video, emptyArray(), arrayOf("ismv")),
  ASF(2038, MediaType.Video, arrayOf("video/x-ms-asf"), arrayOf("asf")),
  VideoTransport(2039, MediaType.Video, arrayOf("video/mp2ts"), arrayOf("ts")),
  ;

  override fun toString(): String = buildString {
    append(name)
    append("[mediaType=")
    append(mediaType.name)
    append(", mimeTypes=")
    append(types.contentToString())
    append(", exts=")
    append(extensions.contentToString())
    append("]")
  }

  companion object {
    private val extensionToFormatMap: Object2ReferenceMap<String, MediaFormat>
    private val mimeTypeToFormatMap: Object2ReferenceMap<String, MediaFormat>
    private val idToFormatMap: Int2ReferenceMap<MediaFormat>

    init {
      val values = values()
      mimeTypeToFormatMap = Object2ReferenceOpenHashMap(values.size)
      extensionToFormatMap = Object2ReferenceOpenHashMap(values.size)
      idToFormatMap = Int2ReferenceOpenHashMap<MediaFormat>(values.size).apply {
        defaultReturnValue(Unknown)
      }
      values.forEach { format ->
        format.extensions.associateWithTo(extensionToFormatMap) { format }
        format.types.associateWithTo(mimeTypeToFormatMap) { format }
        idToFormatMap[format.id] = format
      }
    }

    fun mediaFormatFromId(id: Int): MediaFormat = idToFormatMap.get(id)

    /**
     * Get the MediaFormat for mimeType. MIME types are case insensitive; they are only lower case
     * by convention. https://tools.ietf.org/html/rfc2045 So mime types are converted to lower
     * case for searching. Returns [Unknown] if not found
     */
    fun mediaFormatFromMimeType(mimeType: String): MediaFormat = mimeType
      .splitToSequence(',')
      .map { mimeTypeToFormatMap[it.trim().lowercase()] }
      .filterNotNull()
      .firstOrNull() ?: Unknown

    /**
     * Get the MediaFormat for the file extension. Returns [Unknown] if not found.
     */
    fun mediaFormatFromExtension(extension: String): MediaFormat {
      return extensionToFormatMap.getOrDefault(extension.lowercase(), Unknown)
    }
  }

  val isAudio: Boolean
    get() = mediaType === MediaType.Audio
  val isVideo: Boolean
    get() = mediaType === MediaType.Video
  val isPlaylist: Boolean
    get() = mediaType === MediaType.Playlist
  val isMediaOrPlaylist: Boolean
    get() = when (mediaType) {
      MediaType.Audio, MediaType.Video, MediaType.Playlist -> true
      else -> false
    }

  val mimeTypes: List<String>
    get() = types.asList()

  fun hasMimeType(mimeType: String): Boolean {
    return mimeType.splitToSequence(',').any { it.trim().lowercase() in types }
  }

  val fileExtensions: List<String>
    get() = extensions.toList()

  val preferredExtension: String?
    get() = extensions.firstOrNull()

  fun hasFileExtension(fileExtension: String): Boolean {
    return extensions.contains(fileExtension.lowercase())
  }

  val preferredMimeType: String?
    get() = types.firstOrNull()
}

@Suppress("NOTHING_TO_INLINE")
inline fun Int.toMediaFormat(): MediaFormat = MediaFormat.mediaFormatFromId(this)

object FileExtensions {
  val audio by lazy { Extensions.AUDIO.trimLeadingDot() }

  // mp2 is audio, android thinks ps is Postscript by default
  val video by lazy { Extensions.VIDEO.trimLeadingDot().apply { removeAll(listOf("mp2", "ps")) } }

  // add m3u8 as valid playlist extension
  val playlist by lazy {
    Extensions.PLAYLIST.trimLeadingDot().apply {
      add("m3u8")
      remove("b4s")
    }
  }

  val subtitles by lazy { Extensions.SUBTITLES.trimLeadingDot() }

  private fun HashSet<String>.trimLeadingDot(): HashSet<String> =
    mapTo(HashSet(size)) { it.trimStart('.') }
}
