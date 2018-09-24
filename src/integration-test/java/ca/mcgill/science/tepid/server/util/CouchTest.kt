package ca.mcgill.science.tepid.server.util

import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.PrintQueue
import ca.mcgill.science.tepid.server.auth.SessionManager
import ca.mcgill.science.tepid.server.db.CouchDb
import ca.mcgill.science.tepid.server.db.getViewRows
import ca.mcgill.science.tepid.utils.WithLogging
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Created by Allan Wang on 2017-11-18.
 */
class CouchTest : WithLogging() {

    private inline fun <T> List<T>.test(action: List<T>.() -> Unit) {
        log.debug("\n$this\n")
        action()
    }

    //TODO: parametrise for test user, not real data
    @Ignore("BROKEN TEST SMH: does not fail if couchdb is unavailable")
    @Test
    fun putUser() {
        val response = CouchDb.updateWithResponse<FullUser>("") {
            nick = "a${System.currentTimeMillis()}"
        }
        log.debug("response $response")
    }

    @Test
    fun queues() {
        CouchDb.getViewRows<PrintQueue>("queues").test {
            val names = toList().map(PrintQueue::name).toSet()
            CouchDb.path(CouchDb.CouchDbView.Queues).getViewRows<PrintQueue>().forEach {
                val roomName = it.toString()
                assert(names.contains(roomName)) {
                    "$roomName not found in queues; not a one to one mapping"
                }
            }
        }
    }

    //TODO: parametrise for test user, not real data
    @Test
    fun user() {
        val shortUser = ""
        val longUser = ""

        val byShortUser = SessionManager.queryUserDb(shortUser)
        assertNotNull(byShortUser, "Query for $shortUser was null")

        val byLongUser = SessionManager.queryUserDb(longUser)
        assertNotNull(byLongUser, "Query for $longUser was null")

        assertEquals(longUser, byShortUser!!.longUser)
        assertEquals(shortUser, byLongUser!!.shortUser)

        assertEquals(byLongUser, byShortUser)

        assertNotNull(byShortUser._rev)
        assertNotNull(byLongUser._rev)
        assertEquals(byShortUser._rev, byLongUser._rev)

        assertFalse(byShortUser._id.isNullOrEmpty())
        assertFalse(byLongUser._id.isNullOrEmpty())
        assertEquals(byShortUser._id, byLongUser._id)
    }

}