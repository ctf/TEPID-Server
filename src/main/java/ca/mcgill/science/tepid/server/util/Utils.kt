package ca.mcgill.science.tepid.server.util

import ca.mcgill.science.tepid.models.bindings.TepidDb
import ca.mcgill.science.tepid.models.data.FullSession
import ca.mcgill.science.tepid.server.auth.AuthenticationFilter
import ca.mcgill.science.tepid.server.server.mapper
import org.apache.logging.log4j.kotlin.KotlinLogger
import org.slf4j.MDC
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
fun File.copyFrom(
    input: InputStream,
    vararg options: CopyOption = arrayOf(StandardCopyOption.REPLACE_EXISTING)
) {
    input.use {
        Files.copy(it, toPath(), *options)
    }
}

fun logMessage(msg: String, vararg params: Pair<String, Any?>): String {
    return mapper.writeValueAsString(
        listOf("msg" to msg, *params, "req" to MDC.get("req")).associateBy(
            { p -> p.first },
            { p -> p.second.toString() })
    )
}

fun logError(msg: String, e: Exception, vararg params: Pair<String, Any?>): String {
    return logMessage(msg, "error" to e, *params)
}

fun KotlinLogger.logError(msg: String, e: Exception, vararg params: Pair<String, Any?>) {
    this.error(logMessage(msg, *params), e)
}

fun logAnnounce(msg: String, vararg params: Pair<String, Any?>): String {
    return mapper.writeValueAsString(
        listOf("msg" to msg, *params).associateBy(
            { p -> p.first },
            { p -> p.second.toString() })
    )
}

fun <E : TepidDb> Collection<E>.toIdentifiedCollection(): Map<String, E> {
    return map {
        return@map (it._id ?: return@map) to it
    }.toMap()
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
    inline fun startCaughtThread(name: String, log: KotlinLogger? = null, crossinline action: () -> Unit) =
        object : Thread(name) {
            override fun run() {
                try {
                    action()
                } catch (e: Exception) {
                    log?.error("Caught exception from thread $name: ${e.message}")
                }
            }
        }.start()
}