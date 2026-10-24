import dev.detekt.gradle.extensions.FailOnSeverity
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.springframework.boot.gradle.plugin.SpringBootPlugin
import java.io.StringReader
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

group = "com.autostatistic"
version = parseAppVersion(providers.fileContents(layout.projectDirectory.file("version.properties")).asText.orNull)

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)

    testImplementation(platform(libs.kotest.bom))
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.actuator.test)
    testImplementation(libs.spring.boot.resttestclient)
    testImplementation(libs.kotest.runner.junit6)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.extensions.spring)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform { includeEngines("kotest") }
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}

ktlint {
    version = libs.versions.ktlint.asProvider()
    verbose = true
    ignoreFailures = false
}

detekt {
    toolVersion = libs.versions.detekt
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(file("config/detekt/detekt.yml"))
    source.setFrom("src/main/kotlin", "src/test/kotlin")
    ignoreFailures = false
    failOnSeverity = FailOnSeverity.Error
}

/**
 * Reads the application version from the text of `version.properties` (single source of truth, ADR-0006).
 * Fails the build at configuration time if the file is absent, the `version` key is missing or empty,
 * or the value is not a SemVer core `X.Y.Z`.
 */
fun parseAppVersion(propertiesText: String?): String {
    // Local val on purpose: a top-level script val declared below the call site is still null when it runs.
    val semVerCore = Regex("""^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$""")
    if (propertiesText == null) {
        throw GradleException(
            "version.properties not found in the project root; it must contain 'version=X.Y.Z' (e.g. version=1.0.2)",
        )
    }
    val version =
        Properties()
            .apply { load(StringReader(propertiesText)) }
            .getProperty("version")
            ?.trim()
    if (version.isNullOrEmpty()) {
        throw GradleException(
            "version.properties: property 'version' is missing or empty; expected X.Y.Z (e.g. 1.0.2)",
        )
    }
    if (!semVerCore.matches(version)) {
        throw GradleException(
            "version.properties: invalid version '$version'; expected X.Y.Z - three non-negative integers " +
                "without leading zeros, prefix or suffix (e.g. 1.0.2)",
        )
    }
    return version
}
