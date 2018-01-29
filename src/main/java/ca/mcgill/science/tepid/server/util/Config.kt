package ca.mcgill.science.tepid.server.util

import ca.mcgill.science.tepid.models.data.About
import ca.mcgill.science.tepid.utils.WithLogging
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
import java.io.File
import java.io.FileInputStream
import java.util.*


/**
 * Created by Allan Wang on 27/01/2017.
 *
 * The following are default keys used for testing
 * They are pulled from priv.properties under the root project folder
 * If no file is found, default values will be supplied (usually empty strings)
 */
object Config : WithLogging() {

    private const val COUCHDB_URL_MAIN = "http://tepid.science.mcgill.ca:5984/tepid"
    private const val COUCHDB_URL_TEST = "***REMOVED***"
    private const val BARCODES_URL_MAIN = "http://tepid.science.mcgill.ca:5984/barcodes"
    private const val PRIV_PROPERTIES = "priv.properties"

    /**
     * Global definition for whether a the build is in debug mode or not
     */
    val DEBUG: Boolean;
    /*
     * Couchdb data
     */
    val COUCHDB_URL: String
    val COUCHDB_USERNAME: String
    val COUCHDB_PASSWORD: String
    /*
     * Barcode data
     */
    val BARCODES_USERNAME: String
    val BARCODES_PASSWORD: String
    val BARCODES_URL: String
    /*
     * TEM data
     */
    val TEM_URL: String
    const val RESOURCE_USER = "***REMOVED***"
    val RESOURCE_CREDENTIALS: String
    /*
     * Boolean to enable ldap authentication
     * Defaults to !DEBUG
     */
    val LDAP_ENABLED: Boolean
    /*
     * Optional arguments used to run unit tests for ldap
     */
    val TEST_USER: String
    val TEST_PASSWORD: String

    val HASH: String

    /**
     * Encapsulates config data that can be made public
     */
    val PUBLIC: About

    /**
     * Unfortunately, due to the nature of bundled and exploded wars,
     * it isn't easy to locate the priv.properties file.
     * The workaround is to check multiple common locations, which should hopefully cover most situations
     */
    private fun privFinder(): File? {
        val paths = listOf(PRIV_PROPERTIES, "webapps/tepid/$PRIV_PROPERTIES", "../webapps/ROOT/$PRIV_PROPERTIES")
        val valid = paths.map(::File).firstOrNull(File::exists) ?: return null
        log.debug("Found $PRIV_PROPERTIES at ${valid.absolutePath}")
        return valid
    }

    init {
        log.info("**********************************")
        log.info("*       Setting up Configs       *")
        log.info("**********************************")
        val props = Properties()

        val f = privFinder()
        if (f != null)
            FileInputStream(f).use { props.load(it) }
        else
            log.warn("Could not find $PRIV_PROPERTIES")

        fun get(key: String, default: String?) = props.getProperty(key, default)
        fun get(key: String) = get(key, "")

        DEBUG = get("DEBUG", "true").toBoolean()
        LDAP_ENABLED = get("LDAP_ENABLED", null)?.toBoolean() ?: true
        COUCHDB_URL = if (DEBUG) COUCHDB_URL_TEST else COUCHDB_URL_MAIN
        COUCHDB_USERNAME = get("COUCHDB_USERNAME")
        COUCHDB_PASSWORD = get("COUCHDB_PASSWORD")
        BARCODES_URL = get("BARCODES_URL", BARCODES_URL_MAIN)
        BARCODES_USERNAME = get("BARCODES_USERNAME")
        BARCODES_PASSWORD = get("BARCODES_PASSWORD")
        TEM_URL = get("TEM_URL")
        RESOURCE_CREDENTIALS = get("RESOURCE_CREDENTIALS")
        TEST_USER = get("TEST_USER")
        TEST_PASSWORD = get("TEST_PASSWORD")
        HASH = get("HASH", "local")

        if (DEBUG)
            setLoggingLevel(Level.TRACE)

        /*
         * For logging
         */
        val warnings = mutableListOf<String>()
        fun warn(msg: String) {
            warnings.add(msg)
            log.warn("Warning: $msg")
        }

        log.info("Debug mode: $DEBUG")
        log.info("LDAP mode: $LDAP_ENABLED")
        if (COUCHDB_URL.isEmpty())
            warn("COUCHDB_URL not set")
        if (COUCHDB_PASSWORD.isEmpty())
            warn("COUCHDB_PASSWORD not set")
        if (RESOURCE_CREDENTIALS.isEmpty())
            warn("RESOURCE_CREDENTIALS not set")
        log.info("Build hash: $HASH")

        PUBLIC = About(DEBUG, LDAP_ENABLED, System.currentTimeMillis(), Utils.now(), HASH, warnings)
    }

    fun setLoggingLevel(level: Level) {
        log.info("Updating log level to $level")
        val ctx = LogManager.getContext(false) as LoggerContext
        val config = ctx.configuration
        val loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME)
        loggerConfig.level = level
        ctx.updateLoggers()
    }

}