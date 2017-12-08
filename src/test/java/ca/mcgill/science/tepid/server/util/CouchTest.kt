package ca.mcgill.science.tepid.server.util

import ca.mcgill.science.tepid.server.rest.Queues
import org.junit.Test
import javax.ws.rs.core.MediaType

/**
 * Created by Allan Wang on 2017-11-18.
 */
class CouchTest {

    @Test
    fun test() {
        val rows = couchdb.path("_design/main/_view").path("queues")
                .request(MediaType.APPLICATION_JSON).get<Queues.QueueResultSet>(Queues.QueueResultSet::class.java).rows
        println(rows)
    }

}