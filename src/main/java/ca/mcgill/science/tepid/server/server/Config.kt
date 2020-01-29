package ca.mcgill.science.tepid.server.server

import ca.mcgill.science.tepid.models.data.About
import ca.mcgill.science.tepid.models.data.AdGroup
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.server.db.DbLayer
import ca.mcgill.science.tepid.server.db.makeEntityManagerFactory
import ca.mcgill.science.tepid.server.db.makeHibernateDb
import ca.mcgill.science.tepid.server.printing.GSException
import ca.mcgill.science.tepid.server.printing.Gs
import ca.mcgill.science.tepid.server.util.Utils
import ca.mcgill.science.tepid.utils.DefaultProps
import ca.mcgill.science.tepid.utils.FilePropLoader
import ca.mcgill.science.tepid.utils.JarPropLoader
import ca.mcgill.science.tepid.utils.PropsCreationInfo
import ca.mcgill.science.tepid.utils.PropsDB
import ca.mcgill.science.tepid.utils.PropsLDAP
import ca.mcgill.science.tepid.utils.PropsLDAPGroups
import ca.mcgill.science.tepid.utils.PropsLDAPResource
import ca.mcgill.science.tepid.utils.PropsPrinting
import ca.mcgill.science.tepid.utils.PropsURL
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.kotlin.Logging
import java.util.*
import javax.persistence.EntityManagerFactory

object Config : Logging {

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
     * DB data
     */
    val DB_URL: String
    val DB_USERNAME: String
    val DB_PASSWORD: String
    var emf: EntityManagerFactory? = null

    /*
     * LDAP and Permission Groups
     */

    val LDAP_SEARCH_BASE: String
    val ACCOUNT_DOMAIN: String
    val PROVIDER_URL: String
    val SECURITY_PRINCIPAL_PREFIX: String

    val RESOURCE_USER: String
    val RESOURCE_CREDENTIALS: String

    val EXCHANGE_STUDENTS_GROUP_BASE: String
    val GROUPS_LOCATION: String
    val ELDERS_GROUP: List<AdGroup>
    val CTFERS_GROUP: List<AdGroup>
    val CURRENT_EXCHANGE_GROUP: AdGroup
    val QUOTA_GROUP: List<AdGroup>

    /*
     * Printing configuration
     */

    val MAX_PAGES_PER_JOB: Int

    /*
     * About information
     */

    val HASH: String
    val TAG: String
    val CREATION_TIMESTAMP: Long
    val CREATION_TIME: String

    /**
     * Encapsulates config data that can be made public
     */
    val PUBLIC: About

    init {
        // TODO: revise to use getNonNull, possibly implement a get with default

        logger.info("**********************************")
        logger.info("*       Setting up Configs       *")
        logger.info("**********************************")

        DefaultProps.withName = { fileName ->
            listOf(
                FilePropLoader("/etc/tepid/$fileName"),
                FilePropLoader("webapps/tepid/$fileName"),
                JarPropLoader("/$fileName"),
                FilePropLoader("/config/$fileName")
            )
        }

        DEBUG = PropsURL.TESTING?.toBoolean() ?: true

        TEPID_URL_PRODUCTION = PropsURL.SERVER_URL_PRODUCTION ?: throw RuntimeException()
        TEPID_URL_TESTING = PropsURL.WEB_URL_TESTING ?: TEPID_URL_PRODUCTION

        DB_URL = PropsDB.URL
        DB_USERNAME = PropsDB.USERNAME
        DB_PASSWORD = PropsDB.PASSWORD

        LDAP_SEARCH_BASE = PropsLDAP.LDAP_SEARCH_BASE ?: ""
        ACCOUNT_DOMAIN = PropsLDAP.ACCOUNT_DOMAIN ?: ""
        PROVIDER_URL = PropsLDAP.PROVIDER_URL ?: ""
        SECURITY_PRINCIPAL_PREFIX = PropsLDAP.SECURITY_PRINCIPAL_PREFIX ?: ""

        RESOURCE_USER = PropsLDAPResource.LDAP_RESOURCE_USER ?: ""
        RESOURCE_CREDENTIALS = PropsLDAPResource.LDAP_RESOURCE_CREDENTIALS ?: ""

        EXCHANGE_STUDENTS_GROUP_BASE = PropsLDAPGroups.EXCHANGE_STUDENTS_GROUP_BASE ?: ""
        GROUPS_LOCATION = PropsLDAPGroups.GROUPS_LOCATION
        ELDERS_GROUP = PropsLDAPGroups.ELDERS_GROUPS
        CTFERS_GROUP = PropsLDAPGroups.CTFERS_GROUPS

        CURRENT_EXCHANGE_GROUP = {
            val cal = Calendar.getInstance()
            val groupName =
                EXCHANGE_STUDENTS_GROUP_BASE + cal.get(Calendar.YEAR) + if (cal.get(Calendar.MONTH) < 8) "W" else "F"
            AdGroup(groupName)
        }()
        QUOTA_GROUP = PropsLDAPGroups.QUOTA_GROUPS.plus(CURRENT_EXCHANGE_GROUP).plus(CTFERS_GROUP).plus(ELDERS_GROUP)

        HASH = PropsCreationInfo.HASH ?: ""
        TAG = PropsCreationInfo.TAG ?: ""
        CREATION_TIMESTAMP = PropsCreationInfo.CREATION_TIMESTAMP?.toLongOrNull() ?: -1
        CREATION_TIME = PropsCreationInfo.CREATION_TIME ?: ""

        MAX_PAGES_PER_JOB = PropsPrinting.MAX_PAGES_PER_JOB ?: -1

        getDb()

        if (DEBUG)
            setLoggingLevel(Level.TRACE)
        logger.trace(ELDERS_GROUP)
        logger.trace(CTFERS_GROUP)
        logger.trace(QUOTA_GROUP)

        /*
         * For logging
         */
        val warnings = mutableListOf<String>()

        logger.trace("Validating configs settings")

        logger.info("Debug mode: $DEBUG")
        if (DB_URL.isEmpty())
            logger.fatal("DB_URL not set")
        if (DB_PASSWORD.isEmpty())
            logger.fatal("DB_PASSWORD not set")
        if (RESOURCE_CREDENTIALS.isEmpty())
            logger.error("RESOURCE_CREDENTIALS not set")

        logger.info("Build hash: $HASH")

        PUBLIC = About(
            debug = DEBUG,
            ldapEnabled = true,
            startTimestamp = System.currentTimeMillis(),
            startTime = Utils.now(),
            hash = HASH,
            warnings = warnings,
            tag = TAG,
            creationTime = CREATION_TIME,
            creationTimestamp = CREATION_TIMESTAMP
        )

        logger.trace("Completed setting configs")

        logger.trace("Initialising subsystems")

        DB = getDb()

        try {
            Gs.testRequiredDevicesInstalled()
        } catch (e: GSException) {
            logger.fatal("GS ink_cov device unavailable")
        }

        logger.trace("Completed initialising subsystems")
    }

    fun setLoggingLevel(level: Level) {
        logger.info("Updating log level to $level")
        val ctx = LogManager.getContext(false) as LoggerContext
        val config = ctx.configuration
        val loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME)
        loggerConfig.level = level
        ctx.updateLoggers()
    }

    fun getDb(): DbLayer {
        val persistenceUnit = if (DEBUG) "hibernate-pu-test" else "tepid-pu"
        logger.debug("creating database for persistence unit ${persistenceUnit}")
        val newEmf = makeEntityManagerFactory(persistenceUnit)
        this.emf = newEmf
        logger.debug("entity manager factory created")
        return makeHibernateDb(newEmf)
    }
}