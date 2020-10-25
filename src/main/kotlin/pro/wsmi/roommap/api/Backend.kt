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

import com.charleskorn.kaml.Yaml
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import pro.wsmi.roommap.api.config.BackendConfiguration
import pro.wsmi.roommap.api.matrix.api.PublicRoomListReq200Response
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.http4k.client.ApacheClient
import org.http4k.core.*
import org.http4k.core.ContentType
import org.http4k.filter.DebuggingFilters
import org.http4k.filter.ServerFilters
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import pro.wsmi.kwsmilib.net.URL
import pro.wsmi.roommap.api.db.MatrixServers
import pro.wsmi.roommap.lib.api.APIRoomListReq
import pro.wsmi.roommap.lib.api.APIServerListReq
import pro.wsmi.roommap.lib.api.APIServerReq
import java.io.File
import kotlin.system.exitProcess

const val APP_NAME = "RoomMap-API"
const val APP_VERSION = "0.1.0"
val DEFAULT_CFG_FILE_DIR = File(System.getProperty("user.home"))
const val DEFAULT_CFG_FILE_NAME = ".roommap-api.yml"
const val MATRIX_API_PUBLIC_ROOMS_PATH = "/_matrix/client/r0/publicRooms"


@ExperimentalSerializationApi
fun configureAPIGlobalHttpFilter(debugMode: Boolean, backendCfg: BackendConfiguration) : Filter
{
    val serverHeaderFilter : Filter = Filter {next: HttpHandler ->
        { req: Request ->
            val originalResponse = next(req)
            originalResponse.header("Server", "$APP_NAME/$APP_VERSION")
        }
    }
    val contentTypeFilter = serverHeaderFilter.then(ServerFilters.SetContentType(ContentType.APPLICATION_JSON))
    val compressionFilter = if (backendCfg.apiHttpServer.compression) contentTypeFilter.then(ServerFilters.GZip()) else contentTypeFilter
    return if (debugMode) compressionFilter.then(DebuggingFilters.PrintRequestAndResponse()) else compressionFilter
}


@ExperimentalUnsignedTypes
class BaseLineCmd : CliktCommand(name = "RoomMap-API")
{
    private val cfgFilePathCLA: File? by option("-f", "--config-file", help = "Path of the backend configuration file")
        .file (
            mustExist = true,
            canBeFile = true,
            canBeDir = false,
            mustBeReadable = true,
            mustBeWritable = false,
            canBeSymlink = true
        )
    private val debugModeCLA by option("--debug", help = "Turn on the debug mode").flag()

    @ExperimentalSerializationApi
    override fun run(): Unit = runBlocking {
        print("Loading of backend configuration ... ")

        val configFile = this@BaseLineCmd.cfgFilePathCLA ?: File(DEFAULT_CFG_FILE_DIR, DEFAULT_CFG_FILE_NAME)
        if (!configFile.exists() || !configFile.isFile) {
            println("FAILED")
            println("The configuration file ${configFile.canonicalFile} does not exist.")
            exitProcess(1)
        }
        if (!configFile.canRead()) {
            println("FAILED")
            println("The configuration file ${configFile.canonicalFile} is not readable.")
            exitProcess(2)
        }

        val backendCfg = try {
            Yaml.default.decodeFromString(BackendConfiguration.serializer(), configFile.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            println("FAILED")
            println("There is an error in the configuration file ${configFile.canonicalFile}.")
            if (this@BaseLineCmd.debugModeCLA) e.printStackTrace()
            else println(e.localizedMessage)
            exitProcess(3)
        }

        println("OK")

        print("Connection to database ... ")

        val database = try {
            Database.connect (
                url = "jdbc:postgresql://${if (backendCfg.dbCfg.credentials != null) backendCfg.dbCfg.credentials.username + ":" + backendCfg.dbCfg.credentials.password + "@" else ""}${backendCfg.dbCfg.server.hostString}:${backendCfg.dbCfg.server.port}/${backendCfg.dbCfg.dbName}",
                driver = "org.postgresql.Driver"
            )
        } catch (e: Exception) {
            println("FAILED")
            println("Unable to connect to the database.")
            if (this@BaseLineCmd.debugModeCLA) e.printStackTrace()
            else println(e.localizedMessage)
            exitProcess(4)
        }

        println("OK")

        print("Loading of business data ... ")


        val businessData = try {
            BusinessData(backendCfg = backendCfg)
        } catch (e: Exception)
        {
            println("FAILED")
            println("Unable to load business data.")
            if (this@BaseLineCmd.debugModeCLA) e.printStackTrace()
            else println(e.localizedMessage)
            exitProcess(5)
        }

        println("OK")

        print("Getting matrix server list and room data ... ")

        try {
            businessData.updateMatrixServers()
        } catch (e: Exception)
        {
            println("FAILED")
            println("Unable to get server list or to get room data.")
            if (this@BaseLineCmd.debugModeCLA) e.printStackTrace()
            else println(e.localizedMessage)
            exitProcess(6)
        }

        println("OK")

        println("Backend started")

        val updateRoomJobs = baseHttpRequests.mapValues {
            launch {
                while (it.key.tryBeforeDisabling > 0u)
                {
                    delay(it.key.updateFreq)

                    val roomListResult = getRoomListOfServer(it.value)
                    val roomList = roomListResult.getOrElse { _ ->

                        it.key.tryBeforeDisabling--
                        null
                    }
                    if (roomList != null) it.key.matrixRooms = roomList
                }

                if (it.key.tryBeforeDisabling == 0u)
                {
                    it.key.disabled = true
                    matrixServersCol.updateOne (MatrixServer::id eq it.key.id, it.key)
                    enabledMatrixServers.remove(it.key)
                }
            }
        }


        configureAPIGlobalHttpFilter(debugModeCLA, backendCfg).then(routes(
            APIRoomListReq.REQ_PATH bind Method.POST to handleAPIRoomListReq(debugModeCLA, enabledMatrixServers),
            APIServerListReq.REQ_PATH bind Method.GET to handleAPIServerListReq(debugModeCLA, enabledMatrixServers),
            APIServerReq.REQ_PATH bind Method.POST to handleAPIServerReq(debugModeCLA, enabledMatrixServers)
        )).asServer(Jetty(backendCfg.apiHttpServer.port)).start()
    }
}

@ExperimentalUnsignedTypes
fun main(args: Array<String>) = BaseLineCmd().main(args)