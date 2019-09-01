package ca.mcgill.science.tepid.server.rest

import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.test.TestUtilsDelegate
import ca.mcgill.science.tepid.utils.PropsLDAPTestUser
import ca.mcgill.science.tepid.utils.PropsURL


open class ITBase(val cfg: Config = Config) {
    val server = TestUtilsDelegate(
            PropsLDAPTestUser.TEST_USER,
            PropsLDAPTestUser.TEST_PASSWORD,
            PropsURL.SERVER_URL_PRODUCTION!!,
            PropsURL.TESTING!!.toBoolean()
    )
}