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
import org.litote.kmongo.*
import pro.wsmi.roommap.lib.api.APIRoomListReq
import pro.wsmi.roommap.lib.api.APIServerListReq
import pro.wsmi.roommap.lib.api.APIServerReq
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.system.exitProcess

const val APP_NAME = "RoomMap-Backend"
const val APP_VERSION = "0.1.0"
val DEFAULT_CFG_FILE_DIR = File(System.getProperty("user.home"))
const val DEFAULT_CFG_FILE_NAME = ".roommap-api.yml"
const val MONGODB_MATRIX_SERVERS_COL_NAME = "matrix_servers"
const val MATRIX_API_PUBLIC_ROOMS_PATH = "/_matrix/client/r0/publicRooms"


@ExperimentalUnsignedTypes
@ExperimentalSerializationApi
fun getBaseRequests(matrixServers: List<MatrixServer>, backendCfg: BackendConfiguration) : Map<MatrixServer, Request> = matrixServers.associateWith { matrixServer ->

    val req = Request(
            method = Method.GET,
            Uri(
                    scheme = matrixServer.apiURL.protocol,
                    userInfo = "",
                    host = matrixServer.apiURL.host,
                    port = matrixServer.apiURL.port,
                    path = "",
                    query = "",
                    fragment = ""
            )
    )
    req.replaceHeader("User-Agent", "$APP_NAME/$APP_VERSION (${backendCfg.instanceName})")
}

@ExperimentalSerializationApi
fun getRoomListOfServer (baseHttpRequest: Request) : Result<List<MatrixRoom>>
{
    val publicRoomsReq = baseHttpRequest.uri(baseHttpRequest.uri.path(MATRIX_API_PUBLIC_ROOMS_PATH))

    val httpClient = ApacheClient()
    val publicRoomsResponse = httpClient(publicRoomsReq)

    if (publicRoomsResponse.status != Status.OK)
        return Result.failure(Exception("The server ${baseHttpRequest.uri.host} returned the HTTP code ${publicRoomsResponse.status.code}."))

    val publicRoomListReq200Response = try {
        Json.decodeFromString(PublicRoomListReq200Response.serializer(), publicRoomsResponse.bodyString())
    } catch (e: SerializationException) {
        return Result.failure(e)
    }

    return Result.success(
        publicRoomListReq200Response.chunk.map {
            MatrixRoom (
                it.roomId,
                it.aliases,
                it.canonicalAlias,
                it.name?.replace(regex = Regex("[\\n\\r\\f\\t]"), "")?.replace(regex = Regex("^ +"), ""),
                it.numJoinedMembers,
                it.topic?.replace(regex = Regex("^[\\n\\r\\f\\t ]+"), ""),
                it.worldReadable,
                it.guestCanJoin,
                it.avatarUrl
            )
        }
    )
}

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

        Logger.getLogger("org.mongodb.driver").level = Level.OFF

        val mongoClient = KMongo.createClient(
            "mongodb://${if (backendCfg.mongoCfg.credentials != null) backendCfg.mongoCfg.credentials.username + ":" + backendCfg.mongoCfg.credentials.password + "@" else ""}${backendCfg.mongoCfg.server.hostString}:${backendCfg.mongoCfg.server.port}"
        )
        val mongoDB = mongoClient.getDatabase(backendCfg.mongoCfg.dbName)

        println("OK")

        print("Loading of matrix servers list ... ")

        val matrixServersCol = mongoDB.getCollection<MatrixServer>(MONGODB_MATRIX_SERVERS_COL_NAME)

        val matrixServers = matrixServersCol.find().toList()

        val enabledMatrixServers = matrixServers.filterNot { it.disabled }.toMutableList()


        println("OK")

        print("First querying to Matrix servers to initialize the room list ... ")

        val baseHttpRequests = getBaseRequests(enabledMatrixServers, backendCfg)

        for ((server, baseReq) in baseHttpRequests)
        {
            val roomListResult = getRoomListOfServer(baseReq)
            val roomList = roomListResult.getOrElse {
                println("FAILED")
                it.printStackTrace()
                exitProcess(10)
            }
            if (roomList.isNotEmpty()) server.matrixRooms = roomList
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