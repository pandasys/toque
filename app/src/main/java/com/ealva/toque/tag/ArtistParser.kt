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

package com.ealva.toque.tag

/**
 * Will apply some sort of parsing of each "artist" in a list into possibly multiple artists.
 */
interface ArtistParser {
  fun parseAll(artistList: List<String>): List<String>
}

data class ArtistParserInfo(val delimiter: String, val exceptions: MediaArtistExceptions)

interface ArtistParserInfoDao {
  suspend fun getParseInfo(): ArtistParserInfo

  companion object {
    val NONE = object : ArtistParserInfoDao {
      override suspend fun getParseInfo(): ArtistParserInfo =
        ArtistParserInfo("", MediaArtistExceptions.NONE)
    }
  }
}

interface ArtistParserFactory {
  suspend fun make(): ArtistParser

  companion object {
    operator fun invoke(parserInfoDao: ArtistParserInfoDao): ArtistParserFactory =
      ArtistParserFactoryImpl(parserInfoDao)
  }
}

class ArtistParserFactoryImpl(
  private val parserInfoDao: ArtistParserInfoDao
) : ArtistParserFactory {
  override suspend fun make(): ArtistParser {
    val (delimiter, exceptions) = parserInfoDao.getParseInfo()
    return if (delimiter.isNotEmpty())
      ArtistParserImpl(delimiter, HashSet(exceptions.getAll().map { it.second }))
    else IdentityArtistParser()
  }
}

private class IdentityArtistParser : ArtistParser {
  override fun parseAll(artistList: List<String>): List<String> {
    return if (artistList.isNotEmpty()) artistList else listOf(SongTag.UNKNOWN)
  }
}

private const val INITIAL_ARTISTS_CAPACITY = 8
private fun String.emptyToNull(): String? = if (isNotEmpty()) this else null

private class ArtistParserImpl(
  private val delimiter: String,
  private val exceptionList: Set<String>
) : ArtistParser {

  /**
   * Parses each String in [artistList] as if it were a possible
   * delimited list. The returned list is in the same order
   * as artists in the original [artistList] and duplicates are removed.
   */
  override fun parseAll(artistList: List<String>): List<String> {
    val result = LinkedHashSet<String>(INITIAL_ARTISTS_CAPACITY)
    artistList.forEach { artist ->
      if (exceptionList.contains(artist)) {
        result.add(artist)
      } else {
        artist.splitToSequence(delimiter)
          .mapNotNullTo(result) { split -> split.trim().emptyToNull() }
      }
    }
    return if (result.isNotEmpty()) result.toList() else listOf(SongTag.UNKNOWN)
  }
}

interface MediaArtistExceptions {
  /**
   * Returns a sorted list of artist exceptions - artist strings which are not to be parsed
   */
  fun getAll(): Set<Pair<Long, String>>

  companion object {
    val NONE = object : MediaArtistExceptions {
      override fun getAll(): Set<Pair<Long, String>> = emptySet()
    }
  }
}
