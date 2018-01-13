package ca.mcgill.science.tepid.server.util


/**
 * Created by Allan Wang on 2017-09-29.
 */
fun Exception.tepidLog() {
    System.err.println("WebServer Exception Caught: ${javaClass.canonicalName}")
    printStackTrace()
}