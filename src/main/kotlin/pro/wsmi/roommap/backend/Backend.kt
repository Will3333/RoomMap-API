package pro.wsmi.roommap.backend

import com.charleskorn.kaml.Yaml
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import pro.wsmi.roommap.backend.config.BackendConfiguration
import pro.wsmi.roommap.backend.matrix.api.PublicRoomsListReq200Response
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
import org.http4k.core.*
import org.http4k.core.ContentType
import org.http4k.filter.DebuggingFilters
import org.http4k.filter.ServerFilters
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.litote.kmongo.*
import pro.wsmi.roommap.lib.api.APIRoomListReqResponse
import java.io.File
import java.net.Proxy
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.system.exitProcess

const val APP_NAME = "RoomMap"
const val APP_VERSION = "0.1"
val DEFAULT_CFG_FILE_DIR = File(System.getProperty("user.home"))
const val DEFAULT_CFG_FILE_NAME = ".roommap-backend.yml"
const val MONGODB_MATRIX_SERVERS_COL_NAME = "matrix_servers"
const val MATRIX_API_PUBLIC_ROOMS_PATH = "/_matrix/client/r0/publicRooms"

@KtorExperimentalAPI
@ExperimentalSerializationApi
fun getHttpClients(matrixServers: List<MatrixServer>, backendCfg: BackendConfiguration) : Map<MatrixServer, HttpClient> = matrixServers.associateWith { matrixServer: MatrixServer ->
    HttpClient(Apache) {

        engine {
            proxy = if (backendCfg.proxy != null ) Proxy(Proxy.Type.HTTP, backendCfg.proxy) else null
        }
        install(UserAgent) {
            agent = "$APP_NAME/$APP_VERSION (${backendCfg.instanceName})"
        }
        defaultRequest {
            url {
                host = matrixServer.apiURL.host
                if (matrixServer.apiURL.port != null)
                    port = matrixServer.apiURL.port!!
                protocol = URLProtocol(matrixServer.apiURL.protocol, -1)
            }
        }
    }
}

@ExperimentalSerializationApi
suspend fun getRoomListOfServer (httpClient: HttpClient) : List<MatrixRoom>?
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
        MatrixRoom(it.roomId, it.aliases, it.canonicalAlias, it.name, it.numJoinedMembers, it.topic, it.worldReadable, it.guestCanJoin, it.avatarUrl)
    }
}

@ExperimentalSerializationApi
fun configureGlobalHttpFilter(debugMode: Boolean, backendCfg: BackendConfiguration) : Filter
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

@ExperimentalSerializationApi
fun matrixRoomsAPIReqHandler(debugMode: Boolean, backendCfg: BackendConfiguration, matrixServers: List<MatrixServer>) : HttpHandler = { req: Request ->
    val jsonEncoder = Json {
        prettyPrint = debugMode
    }

    val apiRoomListReqResponse = APIRoomListReqResponse(
        matrixServers.map {
            pro.wsmi.roommap.lib.api.MatrixServer(it.id.toString(), it.name, it.apiURL, it.updateFreq)
        },
        matrixServers.associateBy(
            { server ->
                server.id.toString()
            },
            { server ->
                server.matrixRooms.map {
                    pro.wsmi.roommap.lib.api.MatrixRoom(it.roomId, it.aliases, it.canonicalAlias, it.name, it.numJoinedMembers, it.topic, it.worldReadable, it.guestCanJoin, it.avatarUrl)
                }
            }
        )
    )

    Response(Status.OK).body(jsonEncoder.encodeToString(APIRoomListReqResponse.serializer(), apiRoomListReqResponse))
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
    override fun run(): Unit = runBlocking {
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

        val matrixServersCol = mongoDB.getCollection<MatrixServer>(MONGODB_MATRIX_SERVERS_COL_NAME)

        val matrixServers = matrixServersCol.find().toList()

        println("OK")

        print("First querying to Matrix servers to initialize the room list ... ")

        val httpClients = getHttpClients(matrixServers, backendCfg)

        for ((server, httpClient) in httpClients)
        {
            val roomList = getRoomListOfServer(httpClient)
            if (roomList != null) server.matrixRooms = roomList
        }

        println("OK")

        println("Backend started")

        val updateRoomJobs = httpClients.mapValues {
            launch {
                while (true)
                {
                    delay(it.key.updateFreq)

                    val roomList = getRoomListOfServer(it.value)
                    if (roomList != null) it.key.matrixRooms = roomList
                }
            }
        }

        launch {
            configureGlobalHttpFilter(debugModeCLA, backendCfg).then(routes(
                "/api/rooms" bind Method.GET to matrixRoomsAPIReqHandler(debugModeCLA, backendCfg, matrixServers)
            )).asServer(Jetty(backendCfg.apiHttpServer.port)).start()
        }
    }
}

fun main(args: Array<String>) = BasicLineCmd().main(args)