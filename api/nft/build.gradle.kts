import org.gradle.api.tasks.bundling.Jar
import java.io.File

plugins {
    scala
    id("io.gatling.gradle") version "3.14.3"
}

val serviceLifecycle = ServiceLifecycle(project, "nft", logger)

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

val gatlingVersion = "3.14.3"

dependencies {
    // TestKit module provides all shared testing infrastructure
    // The Gatling plugin requires dependencies to be added to gatlingImplementation
    gatlingImplementation(project(":testkit"))

    // Scala library
    gatlingImplementation("org.scala-lang:scala-library:2.13.18")

    gatlingImplementation("io.gatling.highcharts:gatling-charts-highcharts:${gatlingVersion}")
    gatlingImplementation("io.gatling:gatling-core:${gatlingVersion}")
}

// Ensure testkit classes are included in Gatling runtime classpath
configurations.named("gatlingRuntimeClasspath") {
    extendsFrom(configurations.getByName("gatlingImplementation"))
}

tasks.register("gatlingRunIntegrated") {
    dependsOn(":service:bootJar", ":testkit:jar")
    finalizedBy("gatlingRun")

    doFirst {
        val bootJarTask = project(":service").tasks.named("bootJar").get() as Jar
        val jarFile = bootJarTask.archiveFile.get().asFile
        val logFile = File("${project(":service").projectDir.path}/logs/support-bot.log")

        val started = serviceLifecycle.startService(
            jarFile = jarFile,
            springProfile = "nft",
            healthUrl = "http://localhost:8081/health",
            logFile = logFile
        )

        if (!started) {
            throw GradleException("Service failed to start. See ${logFile.absolutePath}")
        }
        logger.lifecycle("Running NFT tests...")
    }
}

// Chain: gatlingRunIntegrated -> gatlingRun -> stopService
// This guarantees stopService runs after gatlingRun completes
tasks.named("gatlingRun") {
    finalizedBy("stopService")
}

tasks.register("stopService") {
    doLast {
        serviceLifecycle.stopService()
    }
}
