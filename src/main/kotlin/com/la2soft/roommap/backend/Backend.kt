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

        val servers = serversCol.find()

        println("OK")

        println("Backend started")

        val httpClients = mutableMapOf<Server, HttpClient>()
        for (server in servers)
        {
            val httpClient = HttpClient(Apache) {

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

            httpClients[server] = httpClient
        }

        for ((server, httpClient) in httpClients)
        {
            launch {
                while (true)
                {
                    val httpResponse = httpClient.get<HttpResponse>() {

                        method = HttpMethod.Get
                        url {
                            encodedPath = "/_matrix/client/r0/publicRooms"
                        }
                    }

                    val publicRoomsListReq200Response = if (httpResponse.status == HttpStatusCode.OK)
                        Json.decodeFromString(PublicRoomsListReq200Response.serializer(), httpResponse.readText(Charsets.UTF_8))
                    else
                        null

                    if (publicRoomsListReq200Response != null)
                        println(publicRoomsListReq200Response)

                    delay(60000L)
                }
            }
        }
    }
}

fun main(args: Array<String>) = BasicLineCmd().main(args)



    /*
    val serverList = mutableMapOf<Server, HttpClient>()

    val newServer = Server(1, "LA²Soft", URL("https", "matrix.la2soft.com", 8448, ""), listOf())
    serverList[newServer] = HttpClient(Apache) {

        engine {
            proxy = httpProxy
        }
        install(UserAgent) {
            agent = "$APP_NAME/$APP_VERSION ($APP_INSTANCE_NAME)"
        }
        Charsets {
            register(Charsets.UTF_8)
        }
        defaultRequest {
            url {
                host = newServer.apiURL.host
                port = newServer.apiURL.port
                protocol = URLProtocol(newServer.apiURL.protocol, -1)
            }
        }
    }

    for ((server, httpClient) in serverList)
    {
        launch {
            while (true)
            {
                val httpResponse = httpClient.get<HttpResponse>() {
                    method = HttpMethod.Get
                    url {
                        encodedPath = "/_matrix/client/r0/publicRooms"
                    }
                }

                val publicRoomsListReq200Response = if (httpResponse.status == HttpStatusCode.OK) {
                    Json.decodeFromString(PublicRoomsListReq200Response.serializer(), httpResponse.readText())
                } else
                    null

                if (publicRoomsListReq200Response != null)
                    println(publicRoomsListReq200Response)

                delay(60000L)
            }
        }
    }
    */