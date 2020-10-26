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

import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import org.jetbrains.exposed.sql.Database
import pro.wsmi.roommap.api.config.BackendConfiguration


@ExperimentalUnsignedTypes
@ExperimentalSerializationApi
class Engine private constructor(private val backendCfg: BackendConfiguration, private val dbConn: Database, matrixRoomTags: Map<String, MatrixRoomTag>, matrixServers: List<MatrixServer>)
{
    var matrixRoomTags: Map<String, MatrixRoomTag> = matrixRoomTags
        private set

    @ExperimentalUnsignedTypes
    var matrixServers: List<MatrixServer> = matrixServers
        private set


    private var matrixServerRoomUpdateJob = this.matrixServers.associateWith { server ->

        GlobalScope.launch(start = CoroutineStart.LAZY) {
            val newServer = updateMatrixServerRooms(backendCfg = this@Engine.backendCfg, dbConn = this@Engine.dbConn, matrixRoomTags = this@Engine.matrixRoomTags, matrixServer = server).getOrElse {
                //TODO add error logger
                null
            }

            if (newServer != null)
                this@Engine.updateMatrixServerList(oldServer = server, newServer = newServer)
        }
    }


    @ExperimentalUnsignedTypes
    fun startMatrixServerRoomListUpdateLoops()
    {
        this.matrixServerRoomUpdateJob.forEach { server, job ->
            job.start()
        }
    }


    private fun updateMatrixServerList(oldServer: MatrixServer, newServer: MatrixServer)
    {
        this.matrixServers = this.matrixServers.toMutableList().let { newServerList ->
            newServerList.remove(oldServer)
            newServerList.add(newServer)
            newServerList.sortedBy {
                it.name
            }
            newServerList.toList()
        }

        val oldJob = this.matrixServerRoomUpdateJob[oldServer]
        val newCoroutineStartType = if(oldJob != null && (oldJob.isActive || oldJob.isCompleted || oldJob.isCancelled)) CoroutineStart.DEFAULT else CoroutineStart.LAZY
        val newJob = GlobalScope.launch(start = newCoroutineStartType) {

            if (newCoroutineStartType == CoroutineStart.DEFAULT)
                delay(newServer.updateFreq.toLong())

            val newServer2 = updateMatrixServerRooms(backendCfg = this@Engine.backendCfg, dbConn = this@Engine.dbConn, matrixRoomTags = this@Engine.matrixRoomTags, matrixServer = newServer).getOrElse {
                //TODO add error logger
                null
            }

            if (newServer2 != null)
                this@Engine.updateMatrixServerList(oldServer = newServer, newServer = newServer2)
        }

        this.matrixServerRoomUpdateJob = this.matrixServerRoomUpdateJob.toMutableMap().let { newJobList ->
            newJobList.remove(oldServer)
            newJobList[newServer] = newJob
            newJobList.toMap()
        }
    }

    companion object
    {
        @ExperimentalUnsignedTypes
        fun new(backendCfg: BackendConfiguration) : Result<Engine>
        {
            val dbConn = try {
                Database.connect (
                    url = "jdbc:postgresql://${if (backendCfg.dbCfg.credentials != null) backendCfg.dbCfg.credentials.username + ":" + backendCfg.dbCfg.credentials.password + "@" else ""}${backendCfg.dbCfg.server.hostString}:${backendCfg.dbCfg.server.port}/${backendCfg.dbCfg.dbName}",
                    driver = "org.postgresql.Driver"
                )
            } catch (e: Exception) {
                return Result.failure(e)
            }

            val tags = MatrixRoomTag.getAllTags(dbConn).getOrElse { e ->
                return Result.failure(e)
            }

            val matrixServersWithoutRooms = MatrixServer.getAllServers(backendCfg = backendCfg, dbConn = dbConn, notDisabled = true).getOrElse { e ->
                return Result.failure(e)
            }

            return Result.success(Engine(backendCfg = backendCfg, dbConn = dbConn, matrixRoomTags = tags, matrixServers = matrixServersWithoutRooms))
        }

        private fun updateMatrixServerRooms(backendCfg: BackendConfiguration, dbConn: Database, matrixRoomTags: Map<String, MatrixRoomTag>, matrixServer: MatrixServer) : Result<MatrixServer>
        {
            val rooms = MatrixRoom.getAllRooms(backendCfg = backendCfg, dbConn = dbConn, matrixServer = matrixServer, matrixRoomTags = matrixRoomTags).getOrElse { e ->
                return Result.failure(e)
            }

            val newServerTryBeforeDisabling = if (matrixServer.tryBeforeDisabling > 0u) matrixServer.tryBeforeDisabling-1u else matrixServer.tryBeforeDisabling
            val newServerDisabled = if (newServerTryBeforeDisabling > 0u) matrixServer.disabled else true

            val newServer = matrixServer.update(
                tryBeforeDisabling = newServerTryBeforeDisabling,
                disabled = newServerDisabled,
                rooms = rooms
            ).getOrElse { e ->
                return Result.failure(e)
            }

            return Result.success(newServer)
        }
    }
}