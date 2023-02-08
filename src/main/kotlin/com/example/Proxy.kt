package com.example

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import io.ktor.utils.io.*


fun String.bla(i: Int): String {
    return this + i
}

fun embeddedServer(module: String.() -> Unit) {
    "abc".module()
}

val keycloakAddress = "http://localhost:8080"

val keycloakProvider = OAuthServerSettings.OAuth2ServerSettings(
    name = "keycloak",
    authorizeUrl = "$keycloakAddress/realms/Test-Realm/protocol/openid-connect/auth",
    accessTokenUrl = "$keycloakAddress/realms/Test-Realm/protocol/openid-connect/token",
    clientId = "Test-Client",
    clientSecret = "123456",
    accessTokenRequiresBasicAuth = false,
    requestMethod = HttpMethod.Post, // must POST to token endpoint
    defaultScopes = listOf("roles")
)
val keycloakOAuth = "keycloakOAuth"




fun main() {
    embeddedServer(Netty, port = 8181) {
        val secretEncryptKey = hex("00112233445566778899aabbccddeeff")
        val secretSignKey = hex("6819b57a326945c1968f45236589")

        /*install(Authentication) {
            oauth(keycloakOAuth) {
                client = HttpClient(CIO)
                providerLookup = { keycloakProvider }
                urlProvider = {
                    this.
                    redirectUrl("/")
                }
            }
        }*/
        data class AuthData(val token: String = "")

        val redirects = mutableMapOf<String, String>()
        install(Authentication) {
            oauth("auth-oauth-kc") {
                urlProvider = { "http://localhost:8181/callback" }
                providerLookup = {
                    OAuthServerSettings.OAuth2ServerSettings(
                        name = "google",
                        authorizeUrl = "$keycloakAddress/realms/Test-Realm/protocol/openid-connect/auth",
                        accessTokenUrl = "$keycloakAddress/realms/Test-Realm/protocol/openid-connect/token",
                        requestMethod = HttpMethod.Post,
                        clientId = "MasterFrontend",
                        clientSecret = "egal",
                        extraAuthParameters = listOf("access_type" to "offline"),
                        onStateCreated = { call, state ->
                            redirects[state] = call.request.queryParameters["redirectUrl"]!!
                        }
                    )
                }
                client = HttpClient(CIO)
            }
        }
        routing {
            authenticate("auth-oauth-kc") {
                get("/login") {
                    // Redirects to 'authorizeUrl' automatically
                }
//http://localhost:8181/login?redirectUri=http://localhost:9090/backend
                get("/callback") {
                    val principal: OAuthAccessTokenResponse.OAuth2? = call.principal()
                    println("SessionData: $principal!!.state!!, $principal.accessToken")
                    val redirect = redirects[principal!!.state!!]
                    call.sessions.set(AuthData(principal.accessToken))
                    call.respondRedirect(redirect!!)
                }
            }

        }



        install(Sessions) {
            cookie<AuthData>("AUTHDATA") {
                cookie.extensions["SameSite"] = "strict"
                //transform(SessionTransportTransformerEncrypt(secretEncryptKey, secretSignKey))
            }
        }

        routing {


            val client = HttpClient(CIO) {
                followRedirects = true
            }

        /*    get("/login") {
                call.sessions.set(AuthData())
                call.respondText("Login")
            }
*/
            get("/logout") {
                call.sessions.clear<AuthData>()
                call.respondText("Logout")
            }

            route("/*") {
                handle {
                    val channel: ByteReadChannel = call.request.receiveChannel()
                    val size = channel.availableForRead
                    val byteArray: ByteArray = ByteArray(size)
                    channel.readFully(byteArray)

                    if (call.sessions.get<AuthData>() == null) {
                        call.respondRedirect("/login?redirectUrl=" + call.request.uri)

                    } else {

                        try {
                            val backendResponse: HttpResponse =
                                client.request("http://localhost:9090${call.request.uri}") {
                                    method = call.request.httpMethod
                                    headers {
                                        appendAll(call.request.headers.filter { key, _ ->
                                            !key.equals(
                                                HttpHeaders.ContentType,
                                                ignoreCase = true
                                            ) && !key.equals(
                                                HttpHeaders.ContentLength, ignoreCase = true
                                            ) && !key.equals(HttpHeaders.Host, ignoreCase = true)
                                        })
                                        append("Authorization", "Bearer ${call.sessions.get<AuthData>()?.token}")
                                    }

                                    if (call.request.httpMethod.equals(HttpMethod.Post)) {
                                        setBody(ByteArrayContent(byteArray, call.request.contentType()))
                                    }
                                }
                            val proxiedHeaders = backendResponse.headers
                            val location = proxiedHeaders[HttpHeaders.Location]
                            val contentType = proxiedHeaders[HttpHeaders.ContentType]
                            val contentLength = proxiedHeaders[HttpHeaders.ContentLength]
                            call.respond(object : OutgoingContent.WriteChannelContent() {
                                override val contentLength: Long? = contentLength?.toLong()
                                override val contentType: ContentType? =
                                    contentType?.let { ContentType.parse(it) }
                                override val headers: Headers = Headers.build {
                                    appendAll(proxiedHeaders.filter { key, _ ->
                                        !key.equals(
                                            HttpHeaders.ContentType,
                                            ignoreCase = true
                                        ) && !key.equals(HttpHeaders.ContentLength, ignoreCase = true)
                                                && !key.equals(HttpHeaders.TransferEncoding, ignoreCase = true)
                                    })
                                }
                                override val status: HttpStatusCode = backendResponse.status
                                override suspend fun writeTo(clientChannel: ByteWriteChannel) {
                                    backendResponse.bodyAsChannel().copyAndClose(clientChannel)
                                }
                            })
                        } catch (e: Exception) {
                            System.err.println(e);
                        }
                    }
                }

            }

        }
    }.start(wait = true)
}