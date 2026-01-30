import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.io.File

plugins {
    java
}

val serviceLifecycle = ServiceLifecycle(project, "functional", logger)

repositories {
    mavenCentral()
}

dependencies {
    // TestKit module provides all shared testing infrastructure
    testImplementation(project(":testkit"))

    // Lombok for test code
    testCompileOnly("org.projectlombok:lombok:1.18.+")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.+")

    // JUnit (needed for @Test annotation and test execution)
    testImplementation(platform("org.junit:junit-bom:5.13.+"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.+")
    testRuntimeOnly("org.junit.platform:junit-platform-console")

    // JSpecify for null annotations
    testImplementation("org.jspecify:jspecify:1.0.0")
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
	// Make this fat jar depend on whatever tasks are needed to build the test runtime
	// classpath (including any project dependencies like :testkit).
	dependsOn(testRuntimeClasspath.buildDependencies)

	from(sourceSetsContainer.getByName("test").output)
	from(testRuntimeClasspath.files.map { file -> if (file.isDirectory) file else zipTree(file) })
	manifest {
		attributes["Main-Class"] = "org.junit.platform.console.ConsoleLauncher"
	}
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register("testIntegrated") {
    dependsOn(":service:bootJar")
    finalizedBy("test")

    doFirst {
        val bootJarTask = project(":service").tasks.named("bootJar").get() as Jar
        val jarFile = bootJarTask.archiveFile.get().asFile
        val logFile = File("${project(":service").projectDir.path}/logs/support-bot.log")

        val started = serviceLifecycle.startService(
            jarFile = jarFile,
            springProfile = "functionaltests",
            healthUrl = "http://localhost:8081/health",
            logFile = logFile
        )

        if (!started) {
            throw GradleException("Service failed to start. See ${logFile.absolutePath}")
        }
        logger.lifecycle("Running functional tests...")
    }
}

// Chain: testIntegrated -> test -> stopService
// This guarantees stopService runs after test completes
tasks.test {
    finalizedBy("stopService")
}

tasks.register("stopService") {
    doLast {
        serviceLifecycle.stopService()
    }
}
