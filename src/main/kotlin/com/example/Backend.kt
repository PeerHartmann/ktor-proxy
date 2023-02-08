package com.example

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import io.ktor.utils.io.*


fun main() {
    embeddedServer(Netty, port = 9090) {

        routing {
            route("/*") {

                handle {
                    if (call.request.header("Authorization") == null) {
                        call.respond(HttpStatusCode.Unauthorized);

                    } else {
                        call.respondText("Ich bin das Backend ${call.request.uri} ${call.request.httpMethod}\n AccessToken ${call.request.header("Authorization")}")
                    }
                }
            }

        }
    }.start(wait = true)
}