package com.autostatistic

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.springframework.boot.SpringApplication
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class ServerPortOverrideTest :
    FunSpec({
        test("application listens on the port given by server.port argument") {
            // given: свободный порт, отличный от порта по умолчанию
            val port = ServerSocket(0).use { it.localPort }

            // when: старт с переопределением порта аргументом, без изменения кода
            SpringApplication.run(AutoStatisticApplication::class.java, "--server.port=$port").use { context ->
                // then: сервер слушает именно этот порт и отвечает на health
                context.environment.getProperty("local.server.port") shouldBe port.toString()

                val response =
                    HttpClient.newHttpClient().send(
                        HttpRequest.newBuilder(URI.create("http://localhost:$port/actuator/health")).GET().build(),
                        HttpResponse.BodyHandlers.ofString(),
                    )
                response.statusCode() shouldBe 200
            }
        }
    })
