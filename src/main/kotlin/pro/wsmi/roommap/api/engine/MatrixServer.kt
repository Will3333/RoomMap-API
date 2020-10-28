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
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import pro.wsmi.kwsmilib.net.URL
import pro.wsmi.roommap.api.config.BackendConfiguration
import pro.wsmi.roommap.api.db.MatrixServers
import java.sql.SQLException
import java.time.Duration


@ExperimentalSerializationApi
class MatrixServer @ExperimentalUnsignedTypes private constructor (

    val backendCfg: BackendConfiguration,
    val dbConn: Database,
    val id: UInt,
    val name: String,
    val apiUrl: URL,
    val roomUpdateFreq: ULong,
    val roomExpirationTime: Duration,
    val disabled: Boolean,
    val tryBeforeDisabling: UInt,
    val rooms: List<MatrixRoom>,
)
{
    @ExperimentalUnsignedTypes
    fun update(name: String = this.name, apiUrl: URL = this.apiUrl, roomUpdateFreq: ULong = this.roomUpdateFreq, roomExpirationTime: Duration = this.roomExpirationTime, disabled: Boolean = this.disabled, tryBeforeDisabling: UInt = this.tryBeforeDisabling, rooms: List<MatrixRoom> = this.rooms) : Result<MatrixServer>
    {
        val newServer = MatrixServer(
            backendCfg = this.backendCfg,
            dbConn = this.dbConn,
            id = this.id,
            name = name,
            apiUrl = apiUrl,
            roomUpdateFreq = roomUpdateFreq,
            roomExpirationTime = roomExpirationTime,
            disabled = disabled,
            tryBeforeDisabling = tryBeforeDisabling,
            rooms = rooms,
        )

        if (newServer.name != this.name || newServer.apiUrl != this.apiUrl || newServer.roomUpdateFreq != this.roomUpdateFreq || newServer.disabled != this.disabled) {
            try {
                transaction(this.dbConn) {
                    MatrixServers.update({ MatrixServers.id eq this@MatrixServer.id.toInt() }) {
                        it[MatrixServers.name] = newServer.name
                        it[MatrixServers.apiUrl] = newServer.apiUrl.toString()
                        it[MatrixServers.roomUpdateFrequency] = newServer.roomUpdateFreq
                        it[MatrixServers.roomExpirationTime] = newServer.roomExpirationTime.seconds
                        it[MatrixServers.disabled] = newServer.disabled
                    }
                }
            } catch (e: SQLException) {
                return Result.failure(e)
            }
        }

        return Result.success(newServer)
    }

    companion object
    {
        @ExperimentalUnsignedTypes
        fun new(backendCfg: BackendConfiguration, dbConn: Database, name: String, apiUrl: URL, roomUpdateFreq: ULong = 3600000u, roomExpirationTime: Duration = Duration.ofSeconds(604800L), disabled: Boolean = false, tryBeforeDisabling: UInt = 3u) : Result<MatrixServer>
        {
            val serverId = try {
                transaction(dbConn) {
                    MatrixServers.insertAndGetId {
                        it[MatrixServers.name] = name
                        it[MatrixServers.apiUrl] = apiUrl.toString()
                        it[MatrixServers.roomUpdateFrequency] = roomUpdateFreq
                        it[MatrixServers.roomExpirationTime] = roomExpirationTime.seconds
                        it[MatrixServers.disabled] = disabled
                    }
                }
            } catch (e: SQLException) {
                return Result.failure(e)
            }

            return Result.success( MatrixServer (
                backendCfg = backendCfg,
                dbConn = dbConn,
                id = serverId.value.toUInt(),
                name = name,
                apiUrl = apiUrl,
                roomUpdateFreq = roomUpdateFreq,
                roomExpirationTime = roomExpirationTime,
                disabled = disabled,
                tryBeforeDisabling = tryBeforeDisabling,
                rooms = listOf()
            ))
        }

        @ExperimentalUnsignedTypes
        fun getAllServers(backendCfg: BackendConfiguration, dbConn: Database, notDisabled: Boolean = false) : Result<List<MatrixServer>>
        {
            val servers =  try {
                transaction(dbConn) {
                    if (notDisabled) {
                        MatrixServers.select(where = not(MatrixServers.disabled))
                    }
                    else {
                        MatrixServers.selectAll()
                    }
                        .orderBy(MatrixServers.name, SortOrder.ASC)
                        .mapNotNull {

                            val apiUrl = URL.parseURL(it[MatrixServers.apiUrl])

                            if (apiUrl != null)
                            {
                                MatrixServer (
                                    backendCfg = backendCfg,
                                    dbConn = dbConn,
                                    id = it[MatrixServers.id].value.toUInt(),
                                    name = it[MatrixServers.name],
                                    apiUrl = apiUrl,
                                    roomUpdateFreq = it[MatrixServers.roomUpdateFrequency],
                                    roomExpirationTime = Duration.ofSeconds(it[MatrixServers.roomExpirationTime]),
                                    disabled = it[MatrixServers.disabled],
                                    tryBeforeDisabling = 3u,
                                    rooms = listOf()
                                )
                            }
                            else {
                                //TODO add error logger
                                null
                            }
                        }
                }
            } catch (e: SQLException) {
                return Result.failure(e)
            }

            return Result.success(servers)
        }
    }
}