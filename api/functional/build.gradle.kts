import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit

plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.slf4j:slf4j-api:2.0.+")
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.+")
    testRuntimeOnly("org.apache.logging.log4j:log4j-core:2.25.+")

    testCompileOnly("org.projectlombok:lombok:1.18.+")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.+")

    testImplementation("org.apache.commons:commons-text:1.13.1")

    testImplementation(platform("org.junit:junit-bom:5.13.+"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.+")
    testImplementation("org.junit.platform:junit-platform-launcher:1.13.+")
    testRuntimeOnly("org.junit.platform:junit-platform-console")
    testImplementation("org.awaitility:awaitility:4.3.+")
    testImplementation("org.assertj:assertj-core:3.27.+")
    testImplementation("org.wiremock:wiremock:3.13.+")

// Override Jetty to address security findings reported for wiremock's transitive dependency
    testImplementation("org.eclipse.jetty:jetty-server:11.0.+")

    testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-guava")
    testImplementation("io.rest-assured:rest-assured:5.5.+") {
        exclude(group = "commons-logging", module = "commons-logging")
    }

    // Avoiding vulnerability reports from rest-assured
    testImplementation("org.apache.commons:commons-lang3:3.18.+")
    testImplementation("commons-codec:commons-codec:1.18.+")

    testImplementation("net.javacrumbs.json-unit:json-unit-assertj:4.1.+")
    testImplementation("net.datafaker:datafaker:2.4.3")

    // Spring like properties
    testImplementation("org.springframework.boot:spring-boot:3.5.6")
}

// Test will be called from make target stubbed-functional
// You can also run it manually if you have the app started

// Print test summary details and configure test task
tasks.test {
    useJUnitPlatform()

    // Support test filtering via -Dtests="pattern" (e.g., -Dtests="*SomeTest*")
    // Useful when we run tests via testIntegrated task, because `--test` doesn't work with it
    System.getProperty("tests")?.let { pattern ->
        filter {
            includeTestsMatching(pattern)
        }
    }

    testLogging {
        events = setOf(
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED,
            TestLogEvent.FAILED,
            TestLogEvent.STANDARD_OUT,
            TestLogEvent.STANDARD_ERROR
        )
        showStandardStreams = true
    }

    systemProperty("serviceEndpoint", System.getenv("SERVICE_ENDPOINT") ?: "http://localhost:8080")

    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) {}
        override fun beforeTest(testDescriptor: TestDescriptor) {}
        override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {}
        override fun afterSuite(desc: TestDescriptor, result: TestResult) {
            if (desc.parent == null) {
                val output = "Results: ${result.resultType} (${result.testCount} tests, ${result.successfulTestCount} passed, ${result.failedTestCount} failed, ${result.skippedTestCount} skipped)"
                val startItem = "|  "
                val endItem = "  |"
                val repeatLength = startItem.length + output.length + endItem.length
                println("\n" + "-".repeat(repeatLength) + "\n" + startItem + output + endItem + "\n" + "-".repeat(repeatLength))
            }
        }
    })

    outputs.upToDateWhen { System.getProperty("rerun") == null }
}

val sourceSetsContainer = the<SourceSetContainer>()
val testRuntimeClasspath by configurations.getting

tasks.named<Jar>("jar") {
    from(sourceSetsContainer.getByName("test").output)
    from(testRuntimeClasspath.files.map { file -> if (file.isDirectory) file else zipTree(file) })
    manifest {
        attributes["Main-Class"] = "org.junit.platform.console.ConsoleLauncher"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

var serviceProcess: Process? = null
var serviceAlreadyRunning = false


tasks.register("testIntegrated") {
    dependsOn(":service:bootJar")
    finalizedBy("test")
    finalizedBy("stopService")
    doFirst {
        val healthUrl = URI("http://localhost:8081/health").toURL()
        var up = false
        logger.lifecycle("Checking for running service at $healthUrl")

        // Quick probe: if service is already running, skip startup
        try {
            val conn = healthUrl.openConnection() as HttpURLConnection
            conn.connectTimeout = 1000
            conn.readTimeout = 1000
            conn.requestMethod = "GET"
            if (conn.responseCode == 200) {
                serviceAlreadyRunning = true
                up = true
                logger.lifecycle("Detected running service at $healthUrl; skipping startup")
            }
        } catch (_: Throwable) {
            logger.lifecycle("No running service detected at $healthUrl; starting service")
        }

        val logfilePath = "${project(":service").projectDir.path}/logs/support-bot.log"
        if (!up) {
            val bootJarTask = project(":service").tasks.named("bootJar").get() as Jar
            val jarFile = bootJarTask.archiveFile.get().asFile

            logger.lifecycle("Starting service: $jarFile")
            val logfile = File(logfilePath).apply {
                parentFile.mkdirs()
                createNewFile()
            }
            serviceProcess = ProcessBuilder(
                "${System.getProperty("java.home")}/bin/java",
                "-jar", jarFile.absolutePath,
                "--spring.profiles.active=functionaltests"
            )
                .redirectErrorStream(true)
                .redirectOutput(logfile)
                .start()

            val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10)

            while (System.currentTimeMillis() < deadline) {
                try {
                    val conn = healthUrl.openConnection() as HttpURLConnection
                    conn.connectTimeout = 1000
                    conn.readTimeout = 1000
                    conn.requestMethod = "GET"
                    if (conn.responseCode == 200) {
                        up = true
                        break
                    }
                } catch (_: Throwable) {}
                if (serviceProcess?.isAlive != true) {
                    throw GradleException("Service terminated early. See ${project.layout.buildDirectory}")
                }
                Thread.sleep(1000)
            }
        }

        if (!up) {
            throw GradleException("Service not healthy at http://localhost:8081/health within timeout. See ${logfilePath}")
        }
        logger.lifecycle("Service is up; running functional tests...")
    }
}

tasks.register("stopService") {
    doLast {
        if (!serviceAlreadyRunning && serviceProcess != null && serviceProcess?.isAlive == true) {
            logger.lifecycle("Stopping service...")
            serviceProcess?.destroy()
            if (serviceProcess?.waitFor(10, TimeUnit.SECONDS) != true) {
                serviceProcess?.destroyForcibly()
            }
        }
    }
}
