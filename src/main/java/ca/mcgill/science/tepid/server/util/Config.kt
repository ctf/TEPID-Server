package ca.mcgill.science.tepid.server.util

import ca.mcgill.science.tepid.models.data.About
import ca.mcgill.science.tepid.utils.*
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

    private val illegalLDAPCharacters = ",\\+\"\\\\<>;=".toRegex()

    private const val COUCHDB_URL_MAIN = "http://tepid.science.mcgill.ca:5984/tepid"
    private const val COUCHDB_URL_TEST = "***REMOVED***"
    private const val BARCODES_URL_MAIN = "http://tepid.science.mcgill.ca:5984/barcodes"
    private const val PRIV_PROPERTIES = "priv.properties"

    /**
     * Global definition for whether a the build is in debug mode or not
     */
    val DEBUG: Boolean

    /*
     * Server
     */
    val TEPID_URL_PRODUCTION: String
    val TEPID_URL_TESTING: String

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

    /*
     * Boolean to enable ldap authentication
     * Defaults to !DEBUG
     */
    val LDAP_ENABLED: Boolean

    val LDAP_SEARCH_BASE : String
    val ACCOUNT_DOMAIN : String
    val PROVIDER_URL : String
    val SECURITY_PRINCIPAL_PREFIX : String

    val RESOURCE_USER : String
    val RESOURCE_CREDENTIALS: String

    val EXCHANGE_STUDENTS_GROUP_BASE : String
    val EXCANGE_STUDENTS_GROUP_LOCATION : String
    val ELDERS_GROUP : List<String>
    val CTFERS_GROUP : List<String>
    val USERS_GROUP : List<String>

    /*
     * Optional arguments used to run unit tests for ldap
     */
    val TEST_USER: String
    val TEST_PASSWORD: String

    val HASH: String

    val TAG: String

    val CREATION_TIMESTAMP: Long
    val CREATION_TIME: String

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
        fun getListOfString(key: String) = props.getProperty(key).split("\\")

        DEBUG = PropsURL.TESTING.toBoolean()

        TEPID_URL_PRODUCTION = PropsURL.SERVER_URL_PRODUCTION
        TEPID_URL_TESTING = PropsURL.WEB_URL_TESTING

        COUCHDB_URL = PropsDB.COUCHDB_URL
        COUCHDB_USERNAME = PropsDB.COUCHDB_USERNAME
        COUCHDB_PASSWORD = PropsDB.COUCHDB_PASSWORD

        BARCODES_URL = get("BARCODES_URL", BARCODES_URL_MAIN)
        BARCODES_USERNAME = get("BARCODES_USERNAME")
        BARCODES_PASSWORD = get("BARCODES_PASSWORD")
        LDAP_ENABLED = PropsLDAP.LDAP_ENABLED.toBoolean() ?: true
        LDAP_SEARCH_BASE = PropsLDAP.LDAP_SEARCH_BASE
        ACCOUNT_DOMAIN = PropsLDAP.ACCOUNT_DOMAIN
        PROVIDER_URL = PropsLDAP.PROVIDER_URL
        SECURITY_PRINCIPAL_PREFIX = PropsLDAP.SECURITY_PRINCIPAL_PREFIX

        RESOURCE_USER = PropsLDAPResource.LDAP_RESOURCE_USER
        RESOURCE_CREDENTIALS = PropsLDAPResource.LDAP_RESOURCE_CREDENTIALS

        EXCHANGE_STUDENTS_GROUP_BASE = PropsLDAPGroups.EXCHANGE_STUDENTS_GROUP_BASE
        EXCANGE_STUDENTS_GROUP_LOCATION = PropsLDAPGroups.EXCHANGE_STUDENTS_GROUP_LOCATION
        ELDERS_GROUP = PropsLDAPGroups.ELDERS_GROUPS.split(illegalLDAPCharacters)
        CTFERS_GROUP = PropsLDAPGroups.CTFERS_GROUPS.split(illegalLDAPCharacters)
        
//        USERS_GROUP
        fun getCurrentExchangeGroup(): String {
            val cal = Calendar.getInstance()
            return EXCHANGE_STUDENTS_GROUP_BASE + cal.get(Calendar.YEAR) + if (cal.get(Calendar.MONTH) < 8) "W" else "F"
        }
        USERS_GROUP = (PropsLDAPGroups.USERS_GROUPS.split(illegalLDAPCharacters)).plus(getCurrentExchangeGroup())

        TEM_URL = get("TEM_URL")

        TEST_USER = get("TEST_USER")
        TEST_PASSWORD = get("TEST_PASSWORD")

        HASH = get("HASH", "local")
        TAG = get("TAG", "")
        CREATION_TIMESTAMP = get("CREATION_TIMESTAMP")?.toLongOrNull() ?: -1
        CREATION_TIME = get("CREATION_TIME", "")

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

        PUBLIC = About(debug = DEBUG,
                ldapEnabled = LDAP_ENABLED,
                startTimestamp = System.currentTimeMillis(),
                startTime = Utils.now(),
                hash = HASH,
                warnings = warnings,
                tag = TAG,
                creationTime = CREATION_TIME,
                creationTimestamp = CREATION_TIMESTAMP)
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