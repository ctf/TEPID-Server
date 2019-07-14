package ca.mcgill.science.tepid.server.util

import ca.mcgill.science.tepid.models.data.FullSession
import ca.mcgill.science.tepid.server.auth.AuthenticationFilter
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.logging.log4j.Logger
import java.io.File
import java.io.InputStream
import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.*
import javax.ws.rs.WebApplicationException
import javax.ws.rs.container.ContainerRequestContext

/**
 * Where the magic happens
 * Provides class bindings for
 * serialization & deserialization
 */
val mapper = jacksonObjectMapper()

/**
 * Attempts to retrieve a session from a container context,
 * failing as unauthorized if none was found.
 *
 * This should only be used within REST endpoints that are annotated with a
 * role requirement. Typically, the session will be supplied by the [AuthenticationFilter]
 */
@Throws(WebApplicationException::class)
fun ContainerRequestContext.getSession(): FullSession =
        getSessionSafely() ?: failUnauthorized("Invalid Session")

/**
 * Returns a session or null if none was found
 */
fun ContainerRequestContext.getSessionSafely(): FullSession? =
        getProperty(AuthenticationFilter.SESSION) as? FullSession

/**
 * Copies the given [input] to the file, and ensure that
 * the input stream is closed
 *
 * By default, we will clear the current file before the copy
 */
fun File.copyFrom(input: InputStream,
                  vararg options: CopyOption = arrayOf(StandardCopyOption.REPLACE_EXISTING)) {
    input.use {
        Files.copy(it, toPath(), *options)
    }
}

/**
 * Collection of helper methods
 * May be extensive, which is why it is bound within the Utils singleton
 */
object Utils {

    /**
     * Returns string representing current time
     * Optionally format the date before return
     */
    fun now(pattern: String? = null): String {
        val t = Calendar.getInstance().time
        return if (pattern == null) t.toString()
        else SimpleDateFormat(pattern).format(t)
    }

    /**
     * Returns a nonnull file only if it exists
     */
    fun existingFile(path: String?): File? {
        if (path == null) return null
        val f = File(path)
        return if (f.exists()) f else null
    }

    /**
     * Create and run a thread with try catch
     */
    inline fun startCaughtThread(name: String, log: Logger? = null, crossinline action: () -> Unit) = object : Thread(name) {
        override fun run() {
            try {
                action()
            } catch (e: Exception) {
                log?.error("Caught exception from thread $name: ${e.message}")
            }
        }
    }.start()

}