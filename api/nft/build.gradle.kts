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
    gatlingImplementation(project(":testkit"))

    gatlingImplementation("org.scala-lang:scala-library:2.13.18")

    gatlingImplementation("io.gatling.highcharts:gatling-charts-highcharts:${gatlingVersion}")
    gatlingImplementation("io.gatling:gatling-core:${gatlingVersion}")
}

// Ensure testkit classes are included in Gatling runtime classpath
configurations.named("gatlingRuntimeClasspath") {
    extendsFrom(configurations.getByName("gatlingImplementation"))
}

// Configure Gatling Gradle plugin for container/K8s execution
// - Point Java preferences to /tmp to avoid java.util.prefs warnings/errors when
//   running with a read-only root filesystem.
// - When running in CI (including inside the K8s Job), direct Gatling's results
//   directory to a dedicated volume mounted at /mnt/nft-reports so that
//   Kubernetes can persist and expose HTML reports via a PVC.
gatling {
    if (System.getenv("CI") != null) {
        // Keep Java preferences under /tmp to avoid java.util.prefs issues
        // with read-only filesystems.
        systemProperties = mapOf(
            "java.util.prefs.userRoot" to "/tmp/java-prefs",
        )
    }
}

tasks.register("gatlingRunIntegrated") {
	// Ensure the service and all Gatling runtime dependencies are built
    dependsOn(":testkit:jar")
    dependsOn(":service:bootJar")
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

// Helper task used during Docker image build to fully resolve and download the
// Gatling runtime classpath so the runtime container can run Gradle in
// --offline mode using the cached artifacts.
tasks.register("warmupGatlingRuntimeClasspath") {
    group = "verification"
    description = "Resolves gatlingRuntimeClasspath so Docker runtime can use --offline."
    dependsOn(":testkit:jar")
    doLast {
        configurations.named("gatlingRuntimeClasspath").get().files
    }
}
