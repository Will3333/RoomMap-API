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

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.exists
import org.jetbrains.exposed.sql.transactions.transaction
import pro.wsmi.roommap.api.config.BackendConfiguration
import pro.wsmi.roommap.api.db.*
import java.sql.SQLException

@ExperimentalSerializationApi
typealias MatrixServerListChangeHandler = (newList: List<MatrixServer>) -> Unit

@ExperimentalUnsignedTypes
@ExperimentalSerializationApi
class Engine private constructor(private val backendCfg: BackendConfiguration, private val debugMode: Boolean, private val dbConn: Database, matrixRoomTags: Map<String, MatrixRoomTag>, matrixServers: List<MatrixServer>)
{
    var matrixRoomTags: Map<String, MatrixRoomTag> = matrixRoomTags
        private set

    val matrixServersMutex = Mutex()
    @ExperimentalUnsignedTypes
    var matrixServers: List<MatrixServer> = matrixServers
        private set
    private var matrixServerRoomUpdateJobs = this.matrixServers.associateWith { server ->
        createMatrixServerRoomUpdateJob(matrixServer = server)
    }
    private val matrixServerListChangeListenersMutex = Mutex()
    private var matrixServerListChangeListeners: Set<MatrixServerListChangeHandler> = setOf()

    private fun createMatrixServerRoomUpdateJob(matrixServer: MatrixServer) : Job = GlobalScope.launch(start = CoroutineStart.LAZY) {

        println("Appel d'une coroutine de mise à jour des salons du serveur ${matrixServer.name}")

        val newServer = updateMatrixServerRooms(backendCfg = this@Engine.backendCfg, debugMode = this@Engine.debugMode, dbConn = this@Engine.dbConn, matrixRoomTags = this@Engine.matrixRoomTags, matrixServer = matrixServer).getOrElse { e ->
            //TODO add error logger
            if (debugMode)
                e.printStackTrace()
            null
        }

        if (newServer != null)
        {
            val newJob = if (!newServer.disabled) createMatrixServerRoomUpdateJob(matrixServer = newServer) else null
            this@Engine.matrixServersMutex.withLock {

                this@Engine.matrixServers = this@Engine.matrixServers.toMutableList().let { newServerList ->
                    newServerList.remove(matrixServer)
                    newServerList.add(newServer)
                    newServerList.sortedBy {
                        it.name
                    }
                    newServerList.toList()
                }

                if (newJob != null)
                {
                    this@Engine.matrixServerRoomUpdateJobs = this@Engine.matrixServerRoomUpdateJobs.toMutableMap().let { newJobList ->
                        newJobList.remove(matrixServer)
                        newJobList[newServer] = newJob
                        newJobList.toMap()
                    }
                }

                launch {
                    val newList = this@Engine.matrixServers
                    val listeners = this@Engine.matrixServerListChangeListeners
                    listeners.forEach { handler ->
                        handler(newList)
                    }
                }
            }

            delay(newServer.roomUpdateFreq.toLong())

            newJob?.start()
        }
    }

    @ExperimentalUnsignedTypes
    fun startMatrixServerRoomListUpdateLoops()
    {
        this.matrixServerRoomUpdateJobs.forEach { (_, job) ->
            job.start()
        }
    }

    suspend fun addMatrixServerListChangeListener(listener: MatrixServerListChangeHandler)
    {
        this.matrixServerListChangeListenersMutex.withLock {

            this.matrixServerListChangeListeners = this.matrixServerListChangeListeners.toMutableSet().let {
                it.add(listener)
                it.toSet()
            }
        }
    }

    suspend fun removeMatrixServerListChangeListener(listener: MatrixServerListChangeHandler)
    {
        this.matrixServerListChangeListenersMutex.withLock {

            this.matrixServerListChangeListeners = this.matrixServerListChangeListeners.toMutableSet().let {
                it.remove(listener)
                it.toSet()
            }
        }
    }


    companion object
    {
        @ExperimentalUnsignedTypes
        fun new(backendCfg: BackendConfiguration, debugMode: Boolean) : Result<Engine>
        {
            val dbConn = try {

                val urlStr = "jdbc:postgresql://${backendCfg.dbCfg.server.hostString}:${backendCfg.dbCfg.server.port}/${backendCfg.dbCfg.dbName}"
                val driverStr = "org.postgresql.Driver"

                if (backendCfg.dbCfg.credentials != null)
                {
                    Database.connect(
                        url = urlStr,
                        driver = driverStr,
                        user = backendCfg.dbCfg.credentials.username,
                        password = backendCfg.dbCfg.credentials.password
                    )
                }
                else {
                    Database.connect (
                        url = urlStr,
                        driver = driverStr
                    )
                }
            } catch (e: Exception) {
                return Result.failure(e)
            }

            try {
                transaction(dbConn) {
                    if (
                        !MatrixRoomTags.exists() ||
                        !MatrixServers.exists() ||
                        !MatrixRooms.exists() ||
                        !MatrixRoomsMatrixRoomLanguages.exists() ||
                        !MatrixRoomsMatrixRoomTags.exists()
                    )
                        SchemaUtils.createMissingTablesAndColumns (
                            MatrixRoomTags,
                            MatrixServers,
                            MatrixRooms,
                            MatrixRoomsMatrixRoomLanguages,
                            MatrixRoomsMatrixRoomTags
                        )
                }
            } catch (e: SQLException) {
                return Result.failure(e)
            }

            val tags = MatrixRoomTag.getAllTags(dbConn).getOrElse { e ->
                return Result.failure(e)
            }

            val matrixServersWithoutRooms = MatrixServer.getAllServers(
                backendCfg = backendCfg,
                dbConn = dbConn,
                notDisabled = true
            ).getOrElse { e ->
                return Result.failure(e)
            }

            return Result.success(Engine(backendCfg = backendCfg, debugMode = debugMode, dbConn = dbConn, matrixRoomTags = tags, matrixServers = matrixServersWithoutRooms))
        }

        private fun updateMatrixServerRooms(backendCfg: BackendConfiguration, debugMode: Boolean, dbConn: Database, matrixRoomTags: Map<String, MatrixRoomTag>, matrixServer: MatrixServer) : Result<MatrixServer>
        {
            val roomsResult = MatrixRoom.getAllRoomsAndUpdateDb(
                backendCfg = backendCfg,
                debugMode = debugMode,
                dbConn = dbConn,
                matrixServer = matrixServer,
                matrixRoomTags = matrixRoomTags
            )
            val rooms = roomsResult.getOrElse { matrixServer.rooms }

            val newServerTryBeforeDisabling =  if (roomsResult.isFailure)
                if (matrixServer.tryBeforeDisabling > 0u) matrixServer.tryBeforeDisabling-1u else matrixServer.tryBeforeDisabling
            else
                3u
            val newServerDisabled = if (newServerTryBeforeDisabling > 0u) matrixServer.disabled else true

            val newServer = matrixServer.update(
                tryBeforeDisabling = newServerTryBeforeDisabling,
                disabled = newServerDisabled,
                rooms = rooms
            ).getOrElse { e ->
                return Result.failure(e)
            }

            return if (roomsResult.isSuccess)
                Result.success(newServer)
            else
                Result.failure(roomsResult.exceptionOrNull()!!)
        }
    }
}