package ca.mcgill.science.tepid.server.util

import ca.mcgill.science.tepid.models.data.PrintQueue
import ca.mcgill.science.tepid.models.enums.Room
import ca.mcgill.science.tepid.utils.WithLogging
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Created by Allan Wang on 2017-11-18.
 */
class CouchTest : WithLogging() {

    private inline fun <T> List<T>.test(action: List<T>.() -> Unit) {
        log.debug("\n$this\n")
        action()
    }

    @Test
    fun queues() {
        CouchDb.getViewRows<PrintQueue>("queues").test {
            val names = toList().map(PrintQueue::name).toSet()
            Room.values.forEach {
                val roomName = it.toString()
                assert(names.contains(roomName)) {
                    "$roomName not found in queues; not a one to one mapping"
                }
            }
        }
    }

    @Test
    fun user() {
        val shortUser = "***REMOVED***"
        val longUser = "***REMOVED***"

        val byShortUser = SessionManager.getSam(shortUser)
        assertNotNull(byShortUser, "Query for $shortUser was null")
        log.info(byShortUser)

        val byLongUser = SessionManager.getSam(longUser)
        assertNotNull(byLongUser, "Query for $longUser was null")
        log.info(byLongUser)

        assertEquals(longUser, byShortUser!!.longUser)
        assertEquals(shortUser, byLongUser!!.shortUser)

        assertEquals(byLongUser, byShortUser)
    }

}