package ca.mcgill.science.tepid.server.db

import ca.mcgill.science.tepid.models.data.*
import ca.mcgill.science.tepid.server.util.failNotFound
import ca.mcgill.science.tepid.server.util.mapper
import java.io.InputStream
import javax.persistence.EntityNotFoundException
import javax.ws.rs.core.Response
import javax.ws.rs.core.UriInfo


class HibernateDestinationLayer(val hc : HibernateCrud<FullDestination, String?>) : DbDestinationLayer{
    override fun getDestinations(): List<FullDestination> {
        return hc.readAll()
    }

    override fun putDestinations(destinations: Map<Id, FullDestination>): String {
        val failures = mutableListOf<String>()
        destinations.map {
            try{
                it.value._id = it.key
                hc.updateOrCreateIfNotExist(it.value)
            }catch (e:Exception){
                failures.add(e.message ?: "Generic Failure for ID: ${it.key}")
            }
        }

        return mapper.writeValueAsString(if(failures.isEmpty()) "Success" else failures)
    }

    override fun updateDestinationWithResponse(id: Id, updater: FullDestination.() -> Unit): Response {
        try{
            val destination : FullDestination = hc.read(id) ?: throw EntityNotFoundException()
            updater(destination)
            hc.update(destination)
        }catch (e : Exception){
            return parsePersistenceErrorToResponse(e)
        }
        return Response.ok().build()
    }

    override fun deleteDestination(id: Id): String {
        val failures = mutableListOf<String>()
        try {
            hc.deleteById(id)
        } catch (e: Exception) {
            failures.add(e.message ?: "Generic Failure for ID: $id")
        }

        return mapper.writeValueAsString(if(failures.isEmpty()) "Success" else failures)
    }
}

class HibernateJobLayer(val hc : HibernateCrud<PrintJob, String?>) : DbJobLayer {
    override fun getJob(id: Id): PrintJob {
        return hc.read(id) ?: failNotFound("{\"id\":\"$id\"}")
    }

    override fun getJobsByQueue(queue: String, maxAge: Long, sortOrder: Order, limit: Int): List<PrintJob> {
        val sort = if(sortOrder == Order.ASCENDING) "ASC" else "DESC"
        return hc.em.
                createQuery("SELECT c FROM PrintJob c WHERE c.queueName = :queueName ORDER BY c.started $sort", PrintJob::class.java).
                setParameter("queueName", queue).
                resultList
        // TODO("limit")
    }

    override fun getJobsByUser(sam: Sam, sortOrder: Order): List<PrintJob> {
        val sort = if(sortOrder == Order.ASCENDING) "ASC" else "DESC"
        return hc.em.
                createQuery("SELECT c FROM PrintJob c WHERE c.userIdentification = :userId ORDER BY c.started $sort", PrintJob::class.java).
                setParameter("userId", sam).
                resultList
    }

    override fun updateJob(id: Id, updater: PrintJob.() -> Unit): PrintJob? {

        try{
            val printJob : PrintJob = hc.read(id) ?: throw EntityNotFoundException()
            updater(printJob)
            hc.update(printJob)
            return printJob
        }catch (e : Exception){
            hc.log.error("Error updating printjob {\"id\":\"$id\", \"error\":\"${e.message}\"}")
        }
        return null
    }

    override fun postJob(job: PrintJob): Response {
        try{
            hc.create(job)
        }catch (e : Exception){
            return parsePersistenceErrorToResponse(e)
        }
        return Response.ok().build()
    }

    override fun getJobChanges(id: Id, uriInfo: UriInfo): ChangeDelta {
        throw NotImplementedError("Changes is a CouchDb specific feature")
    }

    override fun getJobFile(id: Id, file: String): InputStream? {
        throw NotImplementedError()
    }

    @Deprecated("Only for oldMaxQuota, which is never used")
    override fun getEarliestJobTime(shortUser: ShortUser): Long {
        throw NotImplementedError()
    }
}

class HibernateQueueLayer(val hc : HibernateCrud<PrintQueue, String?>) : DbQueueLayer {
    override fun getQueues(): List<PrintQueue> {
        return hc.readAll()
    }

    override fun putQueues(queues: Collection<PrintQueue>): Response {
        try{
            queues.map { hc.updateOrCreateIfNotExist(it) }
        }catch (e : Exception){
            return parsePersistenceErrorToResponse(e)
        }
        return Response.ok().build()
    }

    override fun deleteQueue(id: Id): String {
        val failures = mutableListOf<String>()
        try {
            hc.deleteById(id)
        } catch (e: Exception) {
            failures.add(e.message ?: "Generic Failure for ID: $id")
        }

        return mapper.writeValueAsString(if(failures.isEmpty()) "Success" else failures)
    }

}

class HibernateMarqueeLayer(val hc : HibernateCrud<MarqueeData, String?>) : DbMarqueeLayer {

    override fun getMarquees(): List<MarqueeData> {

        return hc.readAll()
    }
}
