import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.kotlin.dsl.extra
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
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

        try {
            val pid = pidFile.readText().trim().toLong()
            val processHandle = ProcessHandle.of(pid).orElse(null)
            if (processHandle != null && processHandle.isAlive) {
                logger.lifecycle("Found stale service process (PID: $pid) from previous run, killing it...")
                processHandle.destroyForcibly()
                var attempts = 0
                while (processHandle.isAlive && attempts < 10) {
                    Thread.sleep(500)
                    attempts++
                }
                if (processHandle.isAlive) {
                    logger.warn("Failed to kill stale process (PID: $pid)")
                } else {
                    logger.lifecycle("Stale process killed successfully")
                }
            }
        } catch (e: Exception) {
            logger.warn("Error cleaning up stale process: ${e.message}")
        } finally {
            pidFile.delete()
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
            val conn = URI(healthUrl).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 1000
            conn.readTimeout = 1000
            conn.requestMethod = "GET"
            conn.responseCode == 200
        } catch (_: Throwable) {
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

