package ca.mcgill.science.tepid.server.db

import ca.mcgill.science.tepid.models.data.*
import ca.mcgill.science.tepid.server.util.failNotFound
import ca.mcgill.science.tepid.server.util.mapper
import java.io.InputStream
import java.util.*
import javax.persistence.EntityManager
import javax.persistence.EntityNotFoundException
import javax.ws.rs.core.Response


class HibernateDbLayer(val em: EntityManager) : DbLayer,
        DbDestinationLayer by HibernateDestinationLayer(HibernateCrud(em, FullDestination::class.java)),
        DbJobLayer by HibernateJobLayer(HibernateCrud(em, PrintJob::class.java)),
        DbQueueLayer by HibernateQueueLayer(HibernateCrud(em, PrintQueue::class.java)),
        DbMarqueeLayer by HibernateMarqueeLayer(HibernateCrud(em, MarqueeData::class.java)),
        DbSessionLayer by HibernateSessionLayer(HibernateCrud(em, FullSession::class.java)),
        DbUserLayer by HibernateUserLayer(HibernateCrud(em, FullUser::class.java))


class HibernateDestinationLayer(val hc : HibernateCrud<FullDestination, String?>) : DbDestinationLayer{
    override fun getDestination(id: Id): FullDestination {
        return hc.read(id) ?: failNotFound("Could not find destination {\"ID\":\"$id\"}")
    }

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
        val startTime = if(maxAge > 0) Date().time - maxAge else 0
        return hc.em.
                createQuery("SELECT c FROM PrintJob c WHERE c.queueName = :queueName AND c.started > :startTime ORDER BY c.started $sort", PrintJob::class.java).
                setParameter("queueName", queue).
                setParameter("startTime", startTime).
                setMaxResults(if(limit==-1) Int.MAX_VALUE else limit).
                resultList
    }

    override fun getJobsByUser(sam: Sam, sortOrder: Order): List<PrintJob> {
        val sort = if(sortOrder == Order.ASCENDING) "ASC" else "DESC"
        return hc.em.
                createQuery("SELECT c FROM PrintJob c WHERE c.userIdentification = :userId ORDER BY c.started $sort", PrintJob::class.java).
                setParameter("userId", sam).
                resultList
    }

    override fun getStoredJobs(): List<PrintJob> {
        return hc.em.createQuery("SELECT c FROM PrintJob c WHERE c.file != null", PrintJob::class.java).resultList
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

    override fun updateJobWithResponse(id: Id, updater: PrintJob.() -> Unit): Response {
        try{
            updateJob(id,updater)
        }catch(e : Exception){
            return parsePersistenceErrorToResponse(e)
        }
        return Response.ok().build()
    }

    override fun postJob(job: PrintJob): Response {
        try{
            hc.create(job)
        }catch (e : Exception){
            return parsePersistenceErrorToResponse(e)
        }
        return Response.ok().build()
    }

    override fun getJobFile(id: Id, file: String): InputStream? {
        throw NotImplementedError()
    }

    override fun getOldJobs(): List<PrintJob> {
        return hc.em.createQuery("SELECT c FROM PrintJob c WHERE c.processed = -1 AND c.failed = -1", PrintJob::class.java).resultList
    }
}

class HibernateQueueLayer(val hc : HibernateCrud<PrintQueue, String?>) : DbQueueLayer {
    override fun getQueue(id: Id): PrintQueue {
        return hc.read(id) ?: failNotFound("Could not find PrintQueue {\"ID\":\"$id\"}")
    }

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

    override fun getEta(destinationId: Id): Long {
        return hc.em.createQuery("SELECT max(c.eta) from PrintJob c WHERE c.destination = :destinationId").
                setParameter("destinationId", destinationId)
                .singleResult as Long
    }
}

class HibernateMarqueeLayer(val hc : HibernateCrud<MarqueeData, String?>) : DbMarqueeLayer {

    override fun getMarquees(): List<MarqueeData> {

        return hc.readAll()
    }
}

class HibernateSessionLayer(val hc : HibernateCrud<FullSession, String?>) : DbSessionLayer {
    override fun putSession(session: FullSession): Response {
        try{
            hc.updateOrCreateIfNotExist(session)
        }catch (e : Exception){
            return parsePersistenceErrorToResponse(e)
        }
        return Response.ok().build()
    }

    override fun getSessionOrNull(id: Id): FullSession? {
        return hc.read(id)
    }

    override fun getSessionIdsForUser(shortUser: ShortUser): List<Id> {
        return hc.em.
                createQuery("SELECT c.id FROM FullSession c WHERE c.user.shortUser = :userId", String::class.java).
                setParameter("userId", shortUser).
                resultList
    }

    override fun deleteSession(id: Id): String {
        val failures = mutableListOf<String>()
        try {
            hc.deleteById(id)
        } catch (e: Exception) {
            failures.add(e.message ?: "Generic Failure for ID: $id")
        }
        return mapper.writeValueAsString(if(failures.isEmpty()) "Success" else failures)
    }
}

class HibernateUserLayer(val hc : HibernateCrud<FullUser, String?>) : DbUserLayer{
    override fun putUser(user: FullUser): Response {
        try{
            hc.updateOrCreateIfNotExist(user)
        }catch (e : Exception){
            return parsePersistenceErrorToResponse(e)
        }
        return Response.ok().build()
    }

    override fun getUserOrNull(sam: Sam): FullUser? {
        return hc.read(sam)
    }

    override fun isAdminConfigured(): Boolean {
        return (hc.em.
                createQuery("SELECT COUNT(c) FROM FullUser c").singleResult as Long > 0)
    }

    override fun getTotalPrintedCount(shortUser: ShortUser): Int {
        return (hc.em.
                createQuery("SELECT SUM(c.pages)+2*SUM(c.colorPages) FROM PrintJob c WHERE c.userIdentification = :userId AND c.isRefunded = FALSE").
                setParameter("userId", shortUser).
                singleResult as Long).toInt()
    }

}