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

package com.ealva.toque.db

import com.ealva.toque.audio.AudioOutputRoute
import com.ealva.toque.audio.longId
import com.ealva.toque.service.media.EqPreset
import com.ealva.toque.persist.HasConstId
import com.ealva.toque.persist.reify
import com.ealva.toque.persist.reifyRequire
import com.ealva.welite.db.Database
import com.ealva.welite.db.Queryable
import com.ealva.welite.db.Transaction
import com.ealva.welite.db.TransactionInProgress
import com.ealva.welite.db.expr.and
import com.ealva.welite.db.expr.bindLong
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.or
import com.ealva.welite.db.statements.deleteWhere
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.table.orderByAsc
import com.ealva.welite.db.table.select
import com.ealva.welite.db.table.selects
import com.ealva.welite.db.table.where
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.runCatching

typealias AssociationListResult = Result<List<PresetAssociation>, DaoMessage>

interface EqPresetAssociationDao {
  /**
   * As part of [txn] delete all media and album associations. Typically done when cleaning the DB.
   */
  suspend fun deleteMediaAndAlbumAssociations(txn: TransactionInProgress)

  /**
   * Delete any current default association and then set this [preset] as the default preset. The
   * default preset will be used when there is no song, album, or connection associated preset.
   * Returns [Ok] if successful, else [Err].
   */
  suspend fun setAsDefault(preset: EqPreset): BoolResult

  /**
   * Associate [preset] with the list of associations. If [Ok] and value is true, associations
   * were made, else if false the association list was empty. [Err] returned on exception
   */
  suspend fun makeAssociations(
    preset: EqPreset,
    associations: List<PresetAssociation>
  ): BoolResult

  /**
   * Get the list of associations for [preset]
   */
  suspend fun getAssociationsFor(preset: EqPreset): AssociationListResult

  suspend fun getPreferredId(
    mediaId: MediaId,
    albumId: AlbumId,
    route: AudioOutputRoute,
    defaultValue: () -> Long
  ): LongResult

  companion object {
    operator fun invoke(db: Database): EqPresetAssociationDao = EqPresetAssociationDaoImpl(db)
  }
}

private class EqPresetAssociationDaoImpl(val db: Database) : EqPresetAssociationDao {

  override suspend fun deleteMediaAndAlbumAssociations(txn: TransactionInProgress) = txn.run {
    EqPresetAssociationTable.delete {
      (associationType eq PresetAssociationType.Media.id) or
        (associationType eq PresetAssociationType.Album.id)
    }
    Unit
  }

  private fun Transaction.insertAssociation(
    preset: EqPreset,
    association: PresetAssociation
  ): Long = insertAssociation(
    preset.presetId,
    preset.isSystemPreset,
    association.type.id,
    association.id
  )

  override suspend fun makeAssociations(
    preset: EqPreset,
    associations: List<PresetAssociation>
  ): BoolResult = db.transaction {
    runCatching { doMakeAssociations(preset, associations) }
      .mapError { DaoExceptionMessage(it) }
      .onFailure { rollback() }
  }

  private fun Transaction.doMakeAssociations(
    preset: EqPreset,
    associations: List<PresetAssociation>
  ): Boolean = associations.isNotEmpty().also {
    deletePresetAssociations(preset.presetId)
    associations.forEach { assoc ->
      if (insertAssociation(preset, assoc) < 1)
        throw DaoException("Could not insert $preset/$assoc")
    }
  }

  override suspend fun getAssociationsFor(preset: EqPreset): AssociationListResult = db.query {
    runCatching { doGetAssociations(preset) }
      .mapError { DaoExceptionMessage(it) }
  }

  override suspend fun getPreferredId(
    mediaId: MediaId,
    albumId: AlbumId,
    route: AudioOutputRoute,
    defaultValue: () -> Long
  ): LongResult = db.query {
    runCatching {
      QUERY_DEFAULT
        .sequence({
          it[BIND_MEDIA_ID] = mediaId.id
          it[BIND_ALBUM_ID] = albumId.id
          it[BIND_ROUTE_ID] = route.longId
        }) { it[presetId] }
        .singleOrNull() ?: defaultValue()
    }.mapError { DaoExceptionMessage(it) }
  }

  private fun Queryable.doGetAssociations(preset: EqPreset): List<PresetAssociation> =
    QUERY_ASSOCIATIONS
      .sequence({
        it[BIND_PRESET_ID] = preset.presetId
        it[BIND_IS_SYSTEM] = preset.isSystemPreset
      }) { PresetAssociation.reify(it[associationType], it[associationId]) }
      .toList()

  private fun Transaction.deletePresetAssociations(presetId: Long) =
    DELETE_PRESET.delete { it[BIND_PRESET_ID] = presetId }

  private fun Transaction.insertAssociation(
    assocPresetId: Long,
    isSystem: Boolean,
    assocType: Int,
    assocId: Long
  ): Long = run {
    INSERT_STATEMENT.insert {
      it[presetId] = assocPresetId
      it[isSystemPreset] = isSystem
      it[associationType] = assocType
      it[associationId] = assocId
    }
  }

  private fun Transaction.deleteAssociation(
    association: PresetAssociation
  ): Long = run {
    EqPresetAssociationTable.delete {
      (associationType eq association.type.id) and (associationId eq association.id)
    }
  }

  override suspend fun setAsDefault(
    preset: EqPreset
  ): BoolResult = db.transaction {
    runCatching { doSetAsDefault(preset) }
      .mapError { DaoExceptionMessage(it) }
      .andThen { if (it > 0) Ok(true) else Err(DaoFailedToInsert("$preset")) }
      .onFailure { rollback() }
  }

  private fun Transaction.doSetAsDefault(preset: EqPreset): Long {
    deleteAssociation(PresetAssociation.DEFAULT)
    return insertAssociation(preset, PresetAssociation.DEFAULT)
  }
}

private val INSERT_STATEMENT = EqPresetAssociationTable.insertValues {
  it[presetId].bindArg()
  it[isSystemPreset].bindArg()
  it[associationType].bindArg()
  it[associationId].bindArg()
}

private val BIND_PRESET_ID = EqPresetAssociationTable.presetId.bindArg()
private val BIND_IS_SYSTEM = EqPresetAssociationTable.isSystemPreset.bindArg()
private val QUERY_ASSOCIATIONS = EqPresetAssociationTable
  .selects { listOf(associationType, associationId) }
  .where { (presetId eq BIND_PRESET_ID) and (isSystemPreset eq BIND_IS_SYSTEM) }
  .orderByAsc { associationType }

private val DELETE_PRESET = EqPresetAssociationTable.deleteWhere {
  presetId eq BIND_PRESET_ID
}

private val BIND_MEDIA_ID = bindLong()
private val BIND_ALBUM_ID = bindLong()
private val BIND_ROUTE_ID = bindLong()
private val QUERY_DEFAULT = EqPresetAssociationTable
  .select { presetId }
  .where {
    ((associationType eq PresetAssociationType.Media.id) and (associationId eq BIND_MEDIA_ID)) or
      ((associationType eq PresetAssociationType.Album.id) and (associationId eq BIND_ALBUM_ID)) or
      ((associationType eq PresetAssociationType.Output.id) and (associationId eq BIND_ROUTE_ID)) or
      (associationType eq PresetAssociationType.Default.id)
  }.orderByAsc { associationType }

/**
 * The [id] for this type must not be changed for 2 reasons:
 * 1. The [id] is persisted
 * 2. The [id] is used to order the preferred association
 *
 * When searching for a preferred preset and a preset is associated with any of these types, the
 * order is ASC based on [id]. So order of precedence is:
 * 1. Preset is associated with the given media ID
 * 2. Preset is associated with the given album ID
 * 3. Preset is associated with the given [AudioOutputRoute] (speaker, headphones, bluetooth)
 * 4. Preset is the default preset
 */
enum class PresetAssociationType(override val id: Int) : HasConstId {
  Media(1),
  Album(2),
  Output(3),
  Default(4);
}

class PresetAssociation {
  /** The type of association: Media, Album, Output Route, or default */
  val type: PresetAssociationType

  /**
   * The meaning of this [id] depends on [type]. When [PresetAssociationType] is:
   * * [Default][PresetAssociationType.Default] this id must be zero
   * * [Media][PresetAssociationType.Media] this id is a Media ID
   * * [Album][PresetAssociationType.Album] this id is an Album ID
   * * [Output][PresetAssociationType.Output] this id is an AudioOutputRoute.id
   */
  val id: Long

  /**
   * If the association is of type AudioOutput, returns the associated AudioOutput enum value
   *
   * @return the associate AudioOutput enum instance
   *
   * @throws IllegalArgumentException if there is not AudioOutput with [id]
   * @throws IllegalStateException if this is not an association of type
   * [PresetAssociationType.Output]
   */
  val outputRoute: AudioOutputRoute
    get() {
      check(PresetAssociationType.Output == type)
      return AudioOutputRoute::class.java.reifyRequire(id.toInt())
    }

  private constructor(type: Int, id: Long) {
    this.type = PresetAssociationType::class.java.reify(type, PresetAssociationType.Default)
    this.id = id
  }

  private constructor(type: PresetAssociationType, id: Long) {
    this.type = type
    this.id = id
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PresetAssociation

    if (type != other.type) return false
    if (id != other.id) return false

    return true
  }

  override fun hashCode(): Int {
    var result = type.hashCode()
    result = 31 * result + id.hashCode()
    return result
  }

  override fun toString(): String = when (type) {
    PresetAssociationType.Default -> "PresetAssociation[${type.name}, $id]"
    PresetAssociationType.Media -> "PresetAssociation[${type.name}, ${id.toMediaId()}]"
    PresetAssociationType.Album -> "PresetAssociation[${type.name}, ${id.toAlbumId()}]"
    PresetAssociationType.Output -> "PresetAssociation[${type.name}, ${outputRoute.name}]"
  }

  companion object {
    val DEFAULT = PresetAssociation(PresetAssociationType.Default.id, 0)
    fun makeForMedia(mediaId: MediaId) = PresetAssociation(PresetAssociationType.Media, mediaId.id)
    fun makeForAlbum(albumId: AlbumId) = PresetAssociation(PresetAssociationType.Album, albumId.id)
    fun makeForOutput(output: AudioOutputRoute) =
      PresetAssociation(PresetAssociationType.Output, output.longId)

    fun reify(type: Int, id: Long): PresetAssociation =
      if (type == PresetAssociationType.Default.id) DEFAULT else PresetAssociation(type, id)
  }
}
