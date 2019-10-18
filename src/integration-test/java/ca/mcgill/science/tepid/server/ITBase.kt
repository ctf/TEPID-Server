package ca.mcgill.science.tepid.server

import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.test.TestUtilsDelegate
import ca.mcgill.science.tepid.utils.PropsLDAPTestUser
import ca.mcgill.science.tepid.utils.PropsURL
import org.apache.logging.log4j.kotlin.Logging

open class ITBase(val cfg: Config = Config) : Logging {
    val server = TestUtilsDelegate(
        PropsLDAPTestUser.TEST_USER,
        PropsLDAPTestUser.TEST_PASSWORD,
        PropsURL.SERVER_URL_PRODUCTION!!,
        PropsURL.TESTING!!.toBoolean()
    )
}