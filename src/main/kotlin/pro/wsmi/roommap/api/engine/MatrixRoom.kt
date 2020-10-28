/*
 * Copyright 2020 William Smith
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the License.
 */

package pro.wsmi.roommap.api.engine

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.http4k.client.ApacheClient
import org.http4k.core.Status
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import pro.wsmi.kwsmilib.language.Language
import pro.wsmi.roommap.api.config.BackendConfiguration
import pro.wsmi.roommap.api.db.MatrixRooms
import pro.wsmi.roommap.api.db.MatrixRoomsMatrixRoomLanguages
import pro.wsmi.roommap.api.db.MatrixRoomsMatrixRoomTags
import pro.wsmi.roommap.api.getBaseRequest
import pro.wsmi.roommap.api.matrix.api.MATRIX_API_PUBLIC_ROOMS_PATH
import pro.wsmi.roommap.api.matrix.api.PublicRoomListReq200Response
import pro.wsmi.roommap.api.matrix.api.PublicRoomsChunk
import java.sql.SQLException
import java.util.*

@ExperimentalSerializationApi
class MatrixRoom @ExperimentalUnsignedTypes private constructor (
    val id: String,
    val aliases: Set<String>?,
    val canonicalAlias: String?,
    val name: String?,
    val numJoinedMembers: UInt,
    val topic: String?,
    val worldReadable: Boolean,
    val guestCanJoin: Boolean,
    val avatarUrl: String?,
    val dateAdded: Date,
    val excluded: Boolean,
    val languages: List<Language>?,
    val tags: Set<MatrixRoomTag>?
)
{
    private data class DbRoomData(val dateAdded: Date, val excluded: Boolean)

    companion object
    {
        private fun String.filterName() : String = this
            .replace(regex = Regex("[\\n\\r\\f\\t]"), "")
            .replace(regex = Regex("^ +"), "")
        private fun String.filterTopic() : String = this.replace(regex = Regex("^[\\n\\r\\f\\t ]+"), "")

        @ExperimentalUnsignedTypes
        fun new(dbConn: Database, matrixServer: MatrixServer, matrixServerRoomChunk: PublicRoomsChunk, excluded: Boolean = false, languages: List<Language>? = null, tags: Set<MatrixRoomTag>? = null) : Result<MatrixRoom>
        {
            val newTag = MatrixRoom(
                id = matrixServerRoomChunk.roomId,
                aliases = matrixServerRoomChunk.aliases?.toSet(),
                canonicalAlias = matrixServerRoomChunk.canonicalAlias,
                name = matrixServerRoomChunk.name?.filterName(),
                numJoinedMembers = matrixServerRoomChunk.numJoinedMembers.toUInt(),
                topic = matrixServerRoomChunk.topic?.filterTopic(),
                worldReadable = matrixServerRoomChunk.worldReadable,
                guestCanJoin = matrixServerRoomChunk.guestCanJoin,
                avatarUrl = matrixServerRoomChunk.avatarUrl,
                dateAdded = Date(),
                excluded = excluded,
                languages = languages,
                tags = tags
            )

            try {
                transaction(dbConn) {
                    MatrixRooms.insert {
                        it[MatrixRooms.id] = newTag.id
                        it[MatrixRooms.server] = matrixServer.id.toInt()
                        it[MatrixRooms.dateAdded] = newTag.dateAdded.time
                        it[MatrixRooms.excluded] = newTag.excluded
                    }
                    newTag.languages?.forEach { lang ->
                        MatrixRoomsMatrixRoomLanguages.insert {
                            it[MatrixRoomsMatrixRoomLanguages.room] = newTag.id
                            it[MatrixRoomsMatrixRoomLanguages.language] = lang
                        }
                    }
                    newTag.tags?.forEach {tag ->
                        MatrixRoomsMatrixRoomTags.insert {
                            it[MatrixRoomsMatrixRoomTags.room] = newTag.id
                            it[MatrixRoomsMatrixRoomTags.tag] = tag.id
                        }
                    }
                }
            } catch (e: SQLException) {
                return Result.failure(e)
            }

            return Result.success(newTag)
        }

        @ExperimentalUnsignedTypes
        suspend fun getAllRooms(backendCfg: BackendConfiguration, dbConn: Database, matrixServer: MatrixServer, matrixRoomTags: Map<String, MatrixRoomTag>) : Result<List<MatrixRoom>>
        {
            val baseHttpRequest = getBaseRequest(backendCfg, matrixServer.apiUrl)

            val publicRoomListReq = baseHttpRequest.uri(baseHttpRequest.uri.path(MATRIX_API_PUBLIC_ROOMS_PATH))
            val publicRoomListResponse = ApacheClient()(publicRoomListReq)

            if (publicRoomListResponse.status != Status.OK) {
                return Result.failure(Exception("The server ${baseHttpRequest.uri.host} returned the HTTP code ${publicRoomListResponse.status.code}."))
            }

            val publicRoomListReq200Response = try {
                Json.decodeFromString(PublicRoomListReq200Response.serializer(), publicRoomListResponse.bodyString())
            } catch (e: SerializationException) {
                return Result.failure(e)
            }


            val dbRooms = try { transaction(dbConn) {
                MatrixRooms.select(where = MatrixRooms.server eq matrixServer.id.toInt()).associateBy(
                    keySelector = {
                        it[MatrixRooms.id]
                    },
                    valueTransform = {
                        DbRoomData(dateAdded = Date(it[MatrixRooms.dateAdded]), excluded = it[MatrixRooms.excluded])
                    }
                )
            } } catch (e: SQLException) {
                return Result.failure(e)
            }

            val dbAllRoomsLangs = try { transaction(dbConn) {
                MatrixRoomsMatrixRoomLanguages.selectAll().let { query ->

                    val langs = mutableMapOf<String, MutableList<Language>>()
                    query.forEach {
                        val roomId = it[MatrixRoomsMatrixRoomLanguages.room]
                        val roomLang = it[MatrixRoomsMatrixRoomLanguages.language]

                        if (langs.containsKey(roomId))
                            langs[roomId]!!.add(roomLang)
                        else
                            langs[roomId] = mutableListOf(roomLang)
                    }
                    langs.toMap()
                }
            } } catch (e: SQLException) {
                return Result.failure(e)
            }

            val dbAllRoomsTags = try { transaction(dbConn) {
                MatrixRoomsMatrixRoomTags.selectAll().let { query ->

                    val tags = mutableMapOf<String, MutableSet<MatrixRoomTag>>()
                    query.forEach {
                        val roomId = it[MatrixRoomsMatrixRoomTags.room]
                        val roomTag = matrixRoomTags[it[MatrixRoomsMatrixRoomTags.tag]]

                        if (roomTag != null) {
                            if (tags.containsKey(roomId))
                                tags[roomId]!!.add(roomTag)
                            else
                                tags[roomId] = mutableSetOf(roomTag)
                        }
                    }
                    tags.toMap()
                }
            } } catch (e: SQLException) {
                return Result.failure(e)
            }


            val newRoomList = publicRoomListReq200Response.chunk.map { roomChunk ->

                val dbRoom = dbRooms[roomChunk.roomId]
                val dbRoomLangs = dbAllRoomsLangs[roomChunk.roomId]?.toList()
                val dbRoomTags = dbAllRoomsTags[roomChunk.roomId]?.toSet()

                if (dbRoom != null)
                {
                    MatrixRoom (
                        id = roomChunk.roomId,
                        aliases = roomChunk.aliases?.toSet(),
                        canonicalAlias = roomChunk.canonicalAlias,
                        name = roomChunk.name?.filterName(),
                        numJoinedMembers = roomChunk.numJoinedMembers.toUInt(),
                        topic = roomChunk.topic?.filterTopic(),
                        worldReadable = roomChunk.worldReadable,
                        guestCanJoin = roomChunk.guestCanJoin,
                        avatarUrl = roomChunk.avatarUrl,
                        dateAdded = dbRoom.dateAdded,
                        excluded = dbRoom.excluded,
                        languages = if (dbRoomLangs != null && dbRoomLangs.isNotEmpty()) dbRoomLangs else null,
                        tags = if (dbRoomTags != null && dbRoomTags.isNotEmpty()) dbRoomTags else null
                    )
                }
                else
                    new(dbConn, matrixServer, roomChunk).getOrElse { e ->
                        return Result.failure(e)
                    }
            }

            println("test")

            return Result.success(newRoomList)
        }
    }
}