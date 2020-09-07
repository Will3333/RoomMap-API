package com.la2soft.roommap.backend

import com.charleskorn.kaml.Yaml
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.la2soft.roommap.backend.config.BackendConfiguration
import kotlinx.serialization.ExperimentalSerializationApi
import pro.wsmi.kwsmilib.jvm.io.checkFileExistsAndExitIfNot
import pro.wsmi.kwsmilib.jvm.io.checkIsReadableAndExitIfNot
import java.io.File
import kotlin.system.exitProcess

const val APP_NAME = "RoomMap"
const val APP_VERSION = "0.1"
val DEFAULT_CFG_FILE_DIR = File(System.getProperty("user.home"))
const val DEFAULT_CFG_FILE_NAME = ".roommap-backend.yml"

class BasicLineCmd : CliktCommand()
{
    val cfgFilePathCLA: String? by option("-f", "--config-file", help = "Path of the backend configuration file")

    @ExperimentalSerializationApi
    override fun run()
    {
        val configFile = if (!this.cfgFilePathCLA.isNullOrBlank())
            File(cfgFilePathCLA as String)
        else
            File(DEFAULT_CFG_FILE_DIR, DEFAULT_CFG_FILE_NAME)
        configFile.checkFileExistsAndExitIfNot("The configuration file ${configFile.canonicalFile} does not exist.", 1)
        configFile.checkIsReadableAndExitIfNot("The configuration file ${configFile.canonicalFile} is not readable.", 2)

        val backendCfg = try {
            Yaml.default.decodeFromString(BackendConfiguration.serializer(), configFile.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            println("There is an error in the configuration file ${configFile.canonicalFile}.")
            println(e.localizedMessage)
            exitProcess(3)
        }

        println(backendCfg)

        println(backendCfg.proxy?.hostString)
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