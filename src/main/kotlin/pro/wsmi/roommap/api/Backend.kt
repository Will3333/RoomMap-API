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
const val DEFAULT_CFG_FILE_NAME = ".roommap-backend.yml"
const val MONGODB_MATRIX_SERVERS_COL_NAME = "matrix_servers"
const val MATRIX_API_PUBLIC_ROOMS_PATH = "/_matrix/client/r0/publicRooms"


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
fun getRoomListOfServer (baseHttpRequest: Request) : List<MatrixRoom>?
{
    val publicRoomsReq = baseHttpRequest.uri(baseHttpRequest.uri.path(MATRIX_API_PUBLIC_ROOMS_PATH))

    val httpClient = ApacheClient()
    val publicRoomsResponse = httpClient(publicRoomsReq)

    val publicRoomListReq200Response = if (publicRoomsResponse.status == Status.OK)
        Json.decodeFromString(PublicRoomListReq200Response.serializer(), publicRoomsResponse.bodyString())
    else null

    return publicRoomListReq200Response?.chunk?.map {
        MatrixRoom(it.roomId, it.aliases, it.canonicalAlias, it.name, it.numJoinedMembers, it.topic, it.worldReadable, it.guestCanJoin, it.avatarUrl)
    }
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


class BaseLineCmd : CliktCommand(name = "RoomMapBackend")
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

        println("OK")

        print("First querying to Matrix servers to initialize the room list ... ")

        val baseHttpRequests = getBaseRequests(matrixServers, backendCfg)

        for ((server, baseReq) in baseHttpRequests)
        {
            val roomList = getRoomListOfServer(baseReq)
            if (roomList != null) server.matrixRooms = roomList
        }

        println("OK")

        println("Backend started")

        val updateRoomJobs = baseHttpRequests.mapValues {
            launch {
                while (true)
                {
                    delay(it.key.updateFreq)

                    val roomList = getRoomListOfServer(it.value)
                    if (roomList != null) it.key.matrixRooms = roomList
                }
            }
        }


        configureAPIGlobalHttpFilter(debugModeCLA, backendCfg).then(routes(
            APIRoomListReq.REQ_PATH bind Method.POST to handleAPIRoomListReq(debugModeCLA, matrixServers),
            APIServerListReq.REQ_PATH bind Method.GET to handleAPIServerListReq(debugModeCLA, matrixServers),
            APIServerReq.REQ_PATH bind Method.POST to handleAPIServerReq(debugModeCLA, matrixServers)
        )).asServer(Jetty(backendCfg.apiHttpServer.port)).start()
    }
}

fun main(args: Array<String>) = BaseLineCmd().main(args)