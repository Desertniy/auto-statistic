package com.autostatistic

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BuildSmokeTest :
    FunSpec({
        test("tests run on the JDK 17 toolchain") {
            Runtime.version().feature() shouldBe 17
        }
    })
