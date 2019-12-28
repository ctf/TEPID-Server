package ca.mcgill.science.tepid.server

import ca.mcgill.science.tepid.models.data.FullDestination
import ca.mcgill.science.tepid.models.data.PrintQueue
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.test.TestUtilsDelegate
import ca.mcgill.science.tepid.test.get
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

    fun createTestQueues() {
        Config.DEBUG

        testDestinations.entries.map { server.testApi.putDestination(it.key, it.value).get() }
        testQueues.entries.map { server.testApi.putQueue(it.key, it.value).get() }
    }

    val d0 = "d0"
    val d1 = "d1"
    val testDestinations =
        listOf(d0, d1).map { it to FullDestination(name = "mangleD$it", up = true) }.map { it.apply { second._id = first } }.toMap()

    val q0 = "q0"
    val q1 = "q1"
    val testQueues = listOf(
        q0 to PrintQueue(name = "mangleQ$q0", destinations = listOf(d0), loadBalancer = "fiftyfifty"),
        q1 to PrintQueue(name = "mangleQ$q1", destinations = listOf(d0, d1), loadBalancer = "fiftyfifty")
    ).map { it.apply { second._id = first } }.toMap()
}