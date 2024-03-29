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

package com.ealva.toque.db

import com.ealva.toque.audioout.AudioOutputRoute
import com.ealva.toque.audioout.longId
import com.ealva.toque.audioout.toAudioOutputRoute
import com.ealva.toque.common.EqPresetId
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.HasConstId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.persist.asAlbumId
import com.ealva.toque.persist.asMediaId
import com.ealva.toque.persist.reify
import com.ealva.welite.db.Database
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
import com.github.michaelbull.result.coroutines.runSuspendCatching

typealias AssociationListResult = DaoResult<List<PresetAssociation>>

interface EqPresetAssociationDao {
  /**
   * As part of [this@deleteMediaAndAlbumAssociations] delete all media and album associations.
   * Typically done when cleaning the DB.
   */
  fun TransactionInProgress.deleteMediaAndAlbumAssociations()

  /**
   * Associate [eqPresetId] with the list of associations.
   */
  suspend fun makeAssociations(
    eqPresetId: EqPresetId,
    associations: List<PresetAssociation>
  ): BoolResult

  /**
   * Get the list of associations for [eqPresetId]
   */
  suspend fun getAssociationsFor(eqPresetId: EqPresetId): AssociationListResult

  suspend fun getPreferredId(
    mediaId: MediaId,
    albumId: AlbumId,
    route: AudioOutputRoute,
    defaultValue: EqPresetId
  ): DaoResult<EqPresetId>

  companion object {
    operator fun invoke(db: Database): EqPresetAssociationDao = EqPresetAssociationDaoImpl(db)
  }
}

private class EqPresetAssociationDaoImpl(val db: Database) : EqPresetAssociationDao {

  override fun TransactionInProgress.deleteMediaAndAlbumAssociations() {
    EqPresetAssociationTable.delete {
      (associationType eq PresetAssociationType.Media.id) or
        (associationType eq PresetAssociationType.Album.id)
    }
  }

  private fun Transaction.insertAssociation(
    eqPresetId: EqPresetId,
    association: PresetAssociation
  ): Long = insertAssociation(
    eqPresetId,
    association.type.id,
    association.id
  )

  override suspend fun makeAssociations(
    eqPresetId: EqPresetId,
    associations: List<PresetAssociation>
  ): BoolResult = runSuspendCatching {
    db.transaction {
      doMakeAssociations(
        eqPresetId,
        associations
      )
    }
  }


  private fun Transaction.doMakeAssociations(
    eqPresetId: EqPresetId,
    associations: List<PresetAssociation>
  ): Boolean = associations.isNotEmpty().also {
    deletePresetAssociations(eqPresetId)
    associations.forEach { assoc ->
      if (insertAssociation(eqPresetId, assoc) < 1)
        throw DaoException("Could not insert $eqPresetId/$assoc")
    }
  }

  override suspend fun getAssociationsFor(eqPresetId: EqPresetId) = runSuspendCatching {
    db.query {
      QUERY_ASSOCIATIONS
        .sequence(bind = { bindings ->
          bindings[BIND_PRESET_ID] = eqPresetId.value
        }) { cursor -> PresetAssociation.reify(cursor[associationType], cursor[associationId]) }
        .toList()
    }
  }

  override suspend fun getPreferredId(
    mediaId: MediaId,
    albumId: AlbumId,
    route: AudioOutputRoute,
    defaultValue: EqPresetId
  ): DaoResult<EqPresetId> = runSuspendCatching {
    db.query {
      QUERY_DEFAULT
        .sequence(bind = { bindings ->
          bindings[BIND_MEDIA_ID] = mediaId.value
          bindings[BIND_ALBUM_ID] = albumId.value
          bindings[BIND_ROUTE_ID] = route.longId
        }) { cursor -> EqPresetId(cursor[presetId]) }
        .singleOrNull() ?: defaultValue
    }
  }

  private fun Transaction.deletePresetAssociations(id: EqPresetId) =
    DELETE_PRESET.delete { it[BIND_PRESET_ID] = id.value }

  private fun Transaction.insertAssociation(
    assocPresetId: EqPresetId,
    assocType: Int,
    assocId: Long
  ): Long = run {
    INSERT_STATEMENT.insert {
      it[presetId] = assocPresetId.value
      it[associationType] = assocType
      it[associationId] = assocId
    }
  }
}

private val INSERT_STATEMENT = EqPresetAssociationTable.insertValues {
  it[presetId].bindArg()
  it[associationType].bindArg()
  it[associationId].bindArg()
}

private val BIND_PRESET_ID = EqPresetAssociationTable.presetId.bindArg()
private val QUERY_ASSOCIATIONS = EqPresetAssociationTable
  .selects { listOf(associationType, associationId) }
  .where { presetId eq BIND_PRESET_ID }
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
 * The [id] for this type must NOT be changed for 2 reasons:
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
   * If the association is of type AudioOutput, returns associated [AudioOutputRoute]
   * @throws IllegalArgumentException if there is not AudioOutput with [id]
   * @throws IllegalStateException if this is not an association of type
   * [PresetAssociationType.Output]
   */
  val outputRoute: AudioOutputRoute
    get() {
      check(PresetAssociationType.Output == type)
      return id.toAudioOutputRoute()
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
    PresetAssociationType.Media -> "PresetAssociation[${type.name}, ${id.asMediaId}]"
    PresetAssociationType.Album -> "PresetAssociation[${type.name}, ${id.asAlbumId}]"
    PresetAssociationType.Output -> "PresetAssociation[${type.name}, ${outputRoute.name}]"
  }

  companion object {
    val DEFAULT = PresetAssociation(PresetAssociationType.Default.id, 0)
    fun makeForMedia(mediaId: MediaId) =
      PresetAssociation(PresetAssociationType.Media, mediaId.value)

    fun makeForAlbum(albumId: AlbumId) =
      PresetAssociation(PresetAssociationType.Album, albumId.value)

    fun makeForOutput(output: AudioOutputRoute) =
      PresetAssociation(PresetAssociationType.Output, output.longId)

    fun reify(type: Int, id: Long): PresetAssociation =
      if (type == PresetAssociationType.Default.id) DEFAULT else PresetAssociation(type, id)
  }
}
