package com.autostatistic

import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.EntityExchangeResult
import org.springframework.test.web.servlet.client.RestTestClient

@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureRestTestClient
@ApplyExtension(SpringExtension::class)
class HealthEndpointTest(
    client: RestTestClient,
) : FunSpec({
        val jsonMap = object : ParameterizedTypeReference<Map<String, Any?>>() {}

        fun get(path: String): EntityExchangeResult<Map<String, Any?>> =
            client
                .get()
                .uri(path)
                .exchange()
                .returnResult(jsonMap)

        fun post(path: String): EntityExchangeResult<Map<String, Any?>> =
            client
                .post()
                .uri(path)
                .exchange()
                .returnResult(jsonMap)

        test("health endpoint returns 200 with status UP") {
            val result = get("/actuator/health")

            result.status.value() shouldBe HttpStatus.OK.value()
            result.responseBody.shouldNotBeNull()["status"] shouldBe "UP"
        }

        test("unknown path returns 404 problem detail and application keeps running") {
            val result = get("/does-not-exist")

            result.status.value() shouldBe HttpStatus.NOT_FOUND.value()
            result.responseHeaders.contentType
                .shouldNotBeNull()
                .isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) shouldBe true
            result.responseBody.shouldNotBeNull()["status"] shouldBe HttpStatus.NOT_FOUND.value()

            // приложение продолжает работать
            get("/actuator/health").status.value() shouldBe HttpStatus.OK.value()
        }

        test("actuator endpoints other than health are not exposed") {
            get("/actuator/env").status.value() shouldBe HttpStatus.NOT_FOUND.value()
        }

        test("health endpoint does not expose component details") {
            val body = get("/actuator/health").responseBody.shouldNotBeNull()

            body.keys shouldNotContain "components"
            body.keys shouldNotContain "details"
        }

        test("404 problem detail contains title and instance without internal error data") {
            val body = get("/does-not-exist").responseBody.shouldNotBeNull()

            body["title"] shouldBe "Not Found"
            body["instance"] shouldBe "/does-not-exist"
            body.keys shouldNotContain "trace"
            body.keys shouldNotContain "exception"
        }

        test("unsupported method on health returns 405 problem detail and application keeps running") {
            val result = post("/actuator/health")

            result.status.value() shouldBe HttpStatus.METHOD_NOT_ALLOWED.value()
            result.responseHeaders.contentType
                .shouldNotBeNull()
                .isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) shouldBe true
            result.responseBody.shouldNotBeNull()["status"] shouldBe HttpStatus.METHOD_NOT_ALLOWED.value()

            // приложение продолжает работать
            get("/actuator/health").status.value() shouldBe HttpStatus.OK.value()
        }
    })
