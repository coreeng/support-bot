import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.kotlin.dsl.extra
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Helper class for managing service lifecycle during integration tests.
 * Handles starting/stopping the service, PID file management, and stale process cleanup.
 */
class ServiceLifecycle(
    private val project: Project,
    private val keyPrefix: String,
    private val logger: Logger
) {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(1))
        .build()
    private val pidFile: File = project.layout.buildDirectory.get().asFile.resolve("service.pid")
    private val serviceStateKey = "$keyPrefix.serviceProcess"
    private val serviceAlreadyRunningKey = "$keyPrefix.serviceAlreadyRunning"

    private fun getServiceProcess(): Process? {
        val extra = project.rootProject.extra
        return if (extra.has(serviceStateKey)) extra.get(serviceStateKey) as? Process else null
    }

    private fun setServiceProcess(process: Process?) {
        project.rootProject.extra.set(serviceStateKey, process)
    }

    fun isServiceAlreadyRunning(): Boolean {
        val extra = project.rootProject.extra
        return if (extra.has(serviceAlreadyRunningKey)) extra.get(serviceAlreadyRunningKey) as? Boolean ?: false else false
    }

    private fun setServiceAlreadyRunning(value: Boolean) {
        project.rootProject.extra.set(serviceAlreadyRunningKey, value)
    }

    /**
     * Kills any stale service process from a previous interrupted run.
     */
    fun killStaleProcess() {
	        if (!pidFile.exists()) return

	        val rawText = try {
	            pidFile.readText().trim()
	        } catch (e: Exception) {
	            logger.warn("Failed to read PID file ${pidFile.absolutePath}; leaving it in place", e)
	            return
	        }

	        if (rawText.isEmpty()) {
	            logger.warn("PID file ${pidFile.absolutePath} was empty; deleting it")
	            if (!pidFile.delete()) {
	                logger.warn("Failed to delete empty PID file ${pidFile.absolutePath}")
	            }
	            return
	        }

	        val pid = rawText.toLongOrNull()
	        if (pid == null) {
	            logger.warn("PID file ${pidFile.absolutePath} is corrupt ('$rawText'); deleting it")
	            if (!pidFile.delete()) {
	                logger.warn("Failed to delete corrupt PID file ${pidFile.absolutePath}")
	            }
	            return
	        }

	        val processHandle = try {
	            ProcessHandle.of(pid).orElse(null)
	        } catch (e: Exception) {
	            logger.warn("Error looking up process handle for PID $pid from PID file ${pidFile.absolutePath}", e)
	            return
	        }

	        if (processHandle == null || !processHandle.isAlive) {
	            logger.lifecycle("No live process with PID $pid; deleting stale PID file ${pidFile.absolutePath}")
	            if (!pidFile.delete()) {
	                logger.warn("Failed to delete stale PID file ${pidFile.absolutePath}")
	            }
	            return
	        }

	        try {
	            logger.lifecycle("Found stale service process (PID: $pid) from previous run, killing it...")
	            processHandle.destroyForcibly()
	            var attempts = 0
	            while (processHandle.isAlive && attempts < 10) {
	                Thread.sleep(500)
	                attempts++
	            }
	            if (processHandle.isAlive) {
	                logger.warn("Failed to kill stale process (PID: $pid); keeping PID file at ${pidFile.absolutePath}")
	            } else {
	                logger.lifecycle("Stale process killed successfully; deleting PID file ${pidFile.absolutePath}")
	                if (!pidFile.delete()) {
	                    logger.warn("Failed to delete PID file ${pidFile.absolutePath} after killing process")
	                }
	            }
	        } catch (e: Exception) {
	            logger.warn("Error killing stale process (PID: $pid); keeping PID file at ${pidFile.absolutePath}", e)
	        }
    }

    private fun savePid(process: Process) {
        pidFile.parentFile.mkdirs()
        pidFile.writeText(process.pid().toString())
    }

    private fun deletePidFile() {
        if (pidFile.exists()) {
            pidFile.delete()
        }
    }

    /**
     * Checks if the service is already running by probing the health endpoint.
     */
    fun checkServiceHealth(healthUrl: String): Boolean {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(healthUrl))
                .GET()
                .timeout(Duration.ofSeconds(1))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
            response.statusCode() == 200
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Starts the service and waits for it to become healthy.
     *
     * @param jarFile The service JAR file to start
     * @param springProfile The Spring profile to activate
     * @param healthUrl The health endpoint URL to check
     * @param logFile The file to redirect service output to
     * @param timeoutSeconds How long to wait for the service to become healthy
     * @return true if service started successfully, false otherwise
     */
    fun startService(
        jarFile: File,
        springProfile: String,
        healthUrl: String,
        logFile: File,
        timeoutSeconds: Long = 60
    ): Boolean {
        // Clean up any stale process first
        killStaleProcess()

        // Check if service is already running
        if (checkServiceHealth(healthUrl)) {
            setServiceAlreadyRunning(true)
            logger.lifecycle("Detected running service; skipping startup")
            return true
        }

        logger.lifecycle("No running service detected; starting service")
        logger.lifecycle("Starting service: $jarFile")

        logFile.parentFile.mkdirs()
        logFile.createNewFile()

        val process = ProcessBuilder(
            "${System.getProperty("java.home")}/bin/java",
            "-jar", jarFile.absolutePath,
            "--spring.profiles.active=$springProfile"
        )
            .redirectErrorStream(true)
            .redirectOutput(logFile)
            .start()

        setServiceProcess(process)
        savePid(process)

        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds)

        while (System.currentTimeMillis() < deadline) {
            if (checkServiceHealth(healthUrl)) {
                logger.lifecycle("Service is up")
                return true
            }
            if (!process.isAlive) {
                logger.error("Service terminated early. Check logs at: ${logFile.absolutePath}")
                return false
            }
            Thread.sleep(1000)
        }

        logger.error("Service not healthy within timeout. See ${logFile.absolutePath}")
        return false
    }

    /**
     * Stops the service if it was started by this build.
     */
    fun stopService() {
        val process = getServiceProcess()
        if (!isServiceAlreadyRunning() && process != null && process.isAlive) {
            logger.lifecycle("Stopping service...")
            process.destroy()
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly()
            }
        }
        deletePidFile()
    }
}

