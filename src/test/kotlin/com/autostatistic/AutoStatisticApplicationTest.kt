package com.autostatistic

import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import javax.sql.DataSource

@SpringBootTest
@ApplyExtension(SpringExtension::class)
class AutoStatisticApplicationTest(
    context: ApplicationContext,
) : FunSpec({
        test("context loads without database") {
            context.getBeanNamesForType(DataSource::class.java).shouldBeEmpty()
        }
    })
