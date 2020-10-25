/*
 * Copyright 2020 William Smith
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the License.
 */

package pro.wsmi.roommap.api

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import pro.wsmi.roommap.api.db.MatrixRoomTags
import java.sql.SQLException

class MatrixRoomTag private constructor (
    val id: String,
    val unavailable: Boolean,
    val parent: MatrixRoomTag?
)
{
    fun update(dbConn: Database, unavailable: Boolean = this.unavailable, parent: MatrixRoomTag? = this.parent) : Result<MatrixRoomTag>
    {
        val newTag = MatrixRoomTag(id = this.id, unavailable = unavailable, parent = parent)

        try {
            transaction(dbConn) {
                MatrixRoomTags.update({ MatrixRoomTags.id eq this@MatrixRoomTag.id }) {
                    it[MatrixRoomTags.unavailable] = newTag.unavailable
                    it[MatrixRoomTags.parent] = newTag.parent?.id
                }
            }
        } catch (e: SQLException) {
            return Result.failure(e)
        }

        return Result.success(newTag)
    }

    companion object
    {
        fun new(dbConn: Database, id: String, unavailable: Boolean = false, parent: MatrixRoomTag? = null) : Result<MatrixRoomTag>
        {
            val newTag = MatrixRoomTag(
                id = id,
                unavailable = unavailable,
                parent = parent
            )

            try {
                transaction(dbConn) {
                    MatrixRoomTags.insert {
                        it[MatrixRoomTags.id] = newTag.id
                        it[MatrixRoomTags.unavailable] = newTag.unavailable
                        it[MatrixRoomTags.parent] = newTag.parent?.id
                    }
                }
            } catch (e: SQLException) {
                return Result.failure(e)
            }

            return Result.success(newTag)
        }

        fun getAllTags(dbCon: Database) : Result<Map<String, MatrixRoomTag>>
        {
            val tags = mutableMapOf<String, MatrixRoomTag>()

            try {
                tags.putAll(transaction(dbCon) {
                    MatrixRoomTags.select{ MatrixRoomTags.parent eq null }
                        .map {
                            MatrixRoomTag(
                                id = it[MatrixRoomTags.id],
                                unavailable = it[MatrixRoomTags.unavailable],
                                parent = null
                            )
                        }
                        .associateBy {
                            it.id
                        }
                })
            } catch (e: SQLException) {
                return Result.failure(e)
            }

            val frozenTagList = tags.toMap()
            frozenTagList.forEach {(_, tag) ->
                tags.putAll(getAllChildTags(dbCon = dbCon, parent = tag).getOrElse { e ->
                    return Result.failure(e)
                })
            }

            return Result.success(tags)
        }

        fun getAllChildTags(dbCon: Database, parent: MatrixRoomTag) : Result<Map<String, MatrixRoomTag>>
        {
            val tags = mutableMapOf<String, MatrixRoomTag>()

            try {
                tags.putAll(transaction(dbCon) {
                    MatrixRoomTags.select{ MatrixRoomTags.parent eq parent.id }
                        .map {
                            MatrixRoomTag(
                                id = it[MatrixRoomTags.id],
                                unavailable = it[MatrixRoomTags.unavailable],
                                parent = parent
                            )
                        }
                        .associateBy {
                            it.id
                        }
                })
            } catch (e: SQLException) {
                return Result.failure(e)
            }

            val frozenTagList = tags.toMap()
            frozenTagList.forEach {(_, tag) ->
                tags.putAll(getAllChildTags(dbCon = dbCon, parent = tag).getOrElse { e ->
                    return Result.failure(e)
                })
            }

            return Result.success(tags)
        }
    }
}