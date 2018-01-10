package ca.mcgill.science.tepid.server.util

import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.PrintQueue
import ca.mcgill.science.tepid.models.enums.Room
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Created by Allan Wang on 2017-11-18.
 */
class CouchTest {

    companion object {

        @BeforeClass
        @JvmStatic
        fun before() {
//            CouchDb.logging = true
        }
    }

    private inline fun <T> List<T>.test(action: List<T>.() -> Unit) {
        println("\n$this\n")
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
        println(byShortUser)

        val byLongUser = SessionManager.getSam(longUser)
        assertNotNull(byLongUser, "Query for $longUser was null")
        println(byLongUser)

        assertEquals(longUser, byShortUser!!.longUser)
        assertEquals(shortUser, byLongUser!!.shortUser)

        assertEquals(byLongUser, byShortUser)
    }
    

}