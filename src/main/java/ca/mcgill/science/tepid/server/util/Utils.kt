package ca.mcgill.science.tepid.server.util

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.text.SimpleDateFormat
import java.util.*
import javax.ws.rs.core.Response

/**
 * Created by Allan Wang on 2017-09-29.
 */
fun Exception.tepidLog() {
    System.err.println("WebServer Exception Caught: ${javaClass.canonicalName}")
    printStackTrace()
}

fun unauthorizedResponse(errorMessage: String): Response = Response.status(Response.Status.UNAUTHORIZED)
        .entity("{\"error\":\"$errorMessage\"}").build()

/**
 * Helper function to wrap a typical tepid response
 * Calls [action] while failing safely
 * If any error occurs, emit [Response.Status.UNAUTHORIZED]
 * else wrap the action result with [Response.ok]
 */
inline fun tepidResponse(errorMessage: String, action: () -> Any?): Response {
    try {
        val result: Any = action() ?: return unauthorizedResponse(errorMessage)
        return Response.ok(result).build()
    } catch (e: Exception) {
        e.tepidLog()
        return unauthorizedResponse(errorMessage)
    }
}

val mapper = jacksonObjectMapper()

/*
 * ------------------------------------------
 * Logger
 *
 * Simple Kotlin based logging system
 * to automatically pull class information
 * ------------------------------------------
 */

inline fun <reified T : Any> T.log(message: Any?)
        = logBase(true, false) { message }

inline fun <reified T : Any> T.logErr(message: Any?)
        = logBase(true, true) { message }

inline fun <reified T : Any> T.log(condition: Boolean = true, message: () -> Any?)
        = logBase(condition, false, message)

inline fun <reified T : Any> T.logErr(condition: Boolean = true, message: () -> Any?)
        = logBase(condition, true, message)

inline fun <reified T : Any> T.logBase(condition: Boolean = true, error: Boolean = false, message: () -> Any?) {
    if (condition) {
        val msg = "Tepid - ${this::class.java.simpleName}: ${message()}"
        if (error) System.err.println(msg) else System.out.println(msg)
    }
}


/**
 * We will use the benefits of log4j,
 * but wrap it in a delegate to make it cleaner
 *
 * See <a href="https://stackoverflow.com/a/34462577/4407321">Stack Overflow</a>
 */
//fun <T : Any> T.logger() = logger(this.javaClass)
//
//private fun <T : Any> logger(forClass: Class<T>): Logger
//        = Logger.getLogger(unwrapCompanionClass(forClass).name)
//
//// unwrap companion class to enclosing class given a Java Class
//private fun <T : Any> unwrapCompanionClass(ofClass: Class<T>): Class<*>
//        = if (ofClass.enclosingClass != null && ofClass.enclosingClass.kotlin.companionObject?.java == ofClass)
//    ofClass.enclosingClass else ofClass
//
//interface Loggable {
//    val log: Logger
//}

/**
 * Single configuration for log4j
 */
private val configurator: Unit by lazy {
    //    BasicConfigurator.configure()
}

/**
 * Base implementation of a static final logger
 *
 * Can by used through
 *
 * companion object: WithLogging()
 */
//abstract class WithLogging : Loggable {
//    override val log: Logger by lazy { configurator; this.logger() }
//}

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

}