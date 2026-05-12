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
    checkstyle
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

val serviceLifecycle = ServiceLifecycle(project, "functional", logger)

/** True when the build runs :functional:testIntegrated (starts API before :functional:test). */
var functionalTestsFromIntegrated = false
gradle.taskGraph.whenReady {
    functionalTestsFromIntegrated = allTasks.any { task -> task.path.contains("testIntegrated", ignoreCase = true) }
}

repositories {
    mavenCentral()
}

checkstyle {
    toolVersion = "10.25.0"
    isIgnoreFailures = false
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
}

dependencies {
    // TestKit module provides all shared testing infrastructure
    testImplementation(project(":testkit"))

    // Lombok for test code
    testCompileOnly("org.projectlombok:lombok:1.18.46")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.46")

    // JUnit (needed for @Test annotation and test execution)
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-console")

    // JSpecify for null annotations
    testImplementation("org.jspecify:jspecify:1.0.0")
}

// Standalone :functional:test expects a running API. Root ./gradlew test must NOT run these by default:
// a TCP probe on :8080 is unreliable (other processes listen there but are not Support Bot → ConnectException).
// Opt in with RUN_FUNCTIONAL_TESTS=true, -PrunFunctionalTests, or :functional:testIntegrated.

// Print test summary details and configure test task
tasks.test {
    onlyIf(":functional:testIntegrated, RUN_FUNCTIONAL_TESTS=true, or -PrunFunctionalTests") {
        val runByEnv = System.getenv("RUN_FUNCTIONAL_TESTS") == "true"
        when {
            functionalTestsFromIntegrated -> true
            project.hasProperty("runFunctionalTests") -> true
            runByEnv -> true
            else -> {
                logger.lifecycle(
                    ":functional:test skipped — black-box tests need the API. " +
                        "Use :functional:testIntegrated, or with the service already up: " +
                        "RUN_FUNCTIONAL_TESTS=true ./gradlew :functional:test (or -PrunFunctionalTests). " +
                        "See api/functional/README.md."
                )
                false
            }
        }
    }

    finalizedBy("stopService")

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

        val javaToolchains = project.extensions.getByType(JavaToolchainService::class.java)
        val javaCompiler = project.extensions.getByType(JavaPluginExtension::class.java).toolchain
        val javaLauncher = javaToolchains.launcherFor(javaCompiler).get()

        val started = serviceLifecycle.startService(
            jarFile = jarFile,
            springProfile = "functionaltests",
            healthUrl = "http://localhost:8081/health",
            logFile = logFile,
            javaExecutable = javaLauncher.executablePath.asFile.absolutePath
        )

        if (!started) {
            throw GradleException("Service failed to start. See ${logFile.absolutePath}")
        }
        logger.lifecycle("Running functional tests...")
    }
}

// Chain: testIntegrated -> test -> stopService (finalizedBy on test task above)

tasks.register("stopService") {
    doLast {
        serviceLifecycle.stopService()
    }
}
