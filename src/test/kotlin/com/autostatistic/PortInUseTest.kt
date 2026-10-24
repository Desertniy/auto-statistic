package com.autostatistic

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.boot.SpringApplication
import org.springframework.boot.web.server.PortInUseException
import java.net.ServerSocket

class PortInUseTest :
    FunSpec({
        test("startup fails with PortInUseException when the port is taken") {
            ServerSocket(0).use { socket ->
                val port = socket.localPort

                val outcome =
                    runCatching {
                        SpringApplication.run(AutoStatisticApplication::class.java, "--server.port=$port")
                    }
                outcome.getOrNull()?.close()

                generateSequence(outcome.exceptionOrNull().shouldNotBeNull()) { it.cause }
                    .filterIsInstance<PortInUseException>()
                    .firstOrNull()
                    .shouldNotBeNull()
                    .port shouldBe port
            }
        }
    })
