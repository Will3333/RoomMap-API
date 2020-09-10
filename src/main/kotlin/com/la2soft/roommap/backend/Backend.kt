package com.la2soft.roommap.backend

import com.charleskorn.kaml.Yaml
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.la2soft.roommap.backend.config.BackendConfiguration
import com.la2soft.roommap.backend.matrix.api.PublicRoomsListReq200Response
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.KtorExperimentalAPI
import io.ktor.utils.io.charsets.Charsets
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.litote.kmongo.*
import java.io.File
import java.net.Proxy
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.system.exitProcess

const val APP_NAME = "RoomMap"
const val APP_VERSION = "0.1"
val DEFAULT_CFG_FILE_DIR = File(System.getProperty("user.home"))
const val DEFAULT_CFG_FILE_NAME = ".roommap-backend.yml"
const val MONGODB_SERVERS_COL_NAME = "servers"
const val MATRIX_API_PUBLIC_ROOMS_PATH = "/_matrix/client/r0/publicRooms"

@KtorExperimentalAPI
@ExperimentalSerializationApi
fun getHttpClients(servers: List<Server>, backendCfg: BackendConfiguration) : Map<Server, HttpClient> = servers.associateWith { server: Server ->
    HttpClient(Apache) {

        engine {
            proxy = if (backendCfg.proxy != null ) Proxy(Proxy.Type.HTTP, backendCfg.proxy) else null
        }
        install(UserAgent) {
            agent = "$APP_NAME/$APP_VERSION (${backendCfg.instanceName})"
        }
        defaultRequest {
            url {
                host = server.apiURL.host
                if (server.apiURL.port != null)
                    port = server.apiURL.port!!
                protocol = URLProtocol(server.apiURL.protocol, -1)
            }
        }
    }
}

@ExperimentalSerializationApi
suspend fun getRoomListOfServer (httpClient: HttpClient) : List<Room>?
{
    val httpResponse = httpClient.get<HttpResponse>() {

        method = HttpMethod.Get
        url {
            encodedPath = MATRIX_API_PUBLIC_ROOMS_PATH
        }
    }

    val publicRoomsListReq200Response = if (httpResponse.status == HttpStatusCode.OK)
        Json.decodeFromString(PublicRoomsListReq200Response.serializer(), httpResponse.readText(Charsets.UTF_8))
    else null

    return publicRoomsListReq200Response?.chunk?.map {
        Room(it.roomId, it.aliases, it.canonicalAlias, it.name, it.numJoinedMembers, it.topic, it.worldReadable, it.guestCanJoin, it.avatarUrl)
    }
}

class BasicLineCmd : CliktCommand(name = "Backend")
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

    @KtorExperimentalAPI
    @ExperimentalSerializationApi
    override fun run() = runBlocking {
        print("Loading of backend configuration ... ")

        val configFile = this@BasicLineCmd.cfgFilePathCLA ?: File(DEFAULT_CFG_FILE_DIR, DEFAULT_CFG_FILE_NAME)
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
            if (this@BasicLineCmd.debugModeCLA) e.printStackTrace()
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

        val serversCol = mongoDB.getCollection<Server>(MONGODB_SERVERS_COL_NAME)

        val servers = serversCol.find().toList()

        println("OK")

        print("First querying to Matrix servers to initialize the room list ... ")

        val httpClients = getHttpClients(servers, backendCfg)

        for ((server, httpClient) in httpClients)
        {
            val roomList = getRoomListOfServer(httpClient)
            if (roomList != null) server.rooms = roomList
        }

        println("OK")

        println("Backend started")

        for ((server, httpClient) in httpClients)
        {
            launch {
                while (true)
                {
                    delay(server.updateFreq)

                    val roomList = getRoomListOfServer(httpClient)
                    if (roomList != null) server.rooms = roomList

                    println(roomList)
                }
            }
        }
    }
}

fun main(args: Array<String>) = BasicLineCmd().main(args)