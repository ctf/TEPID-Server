package ca.mcgill.science.tepid.server.util

import org.junit.Assume
import org.junit.BeforeClass
import org.junit.Test

/**
 * Created by Allan Wang on 2017-10-31.
 */
class LdapTest {

    companion object {

        @BeforeClass
        @JvmStatic
        fun before() {
            Assume.assumeTrue(Config.LDAP_ENABLED)
            Assume.assumeTrue(Config.TEST_USER.isNotEmpty())
            Assume.assumeTrue(Config.TEST_PASS.isNotEmpty())
            println("Running ldap tests with test user")
        }
    }


//    @Test
//    fun test() {
//        Ldap.queryUser(Config.TEST_USER, Config.TEST_USER)
//    }
}
