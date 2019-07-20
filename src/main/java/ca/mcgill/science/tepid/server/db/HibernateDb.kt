package ca.mcgill.science.tepid.server.db

import ca.mcgill.science.tepid.models.data.*
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.server.server.mapper
import ca.mcgill.science.tepid.server.util.failNotFound
import ca.mcgill.science.tepid.utils.PropsDB
import java.io.InputStream
import java.util.*
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityNotFoundException
import javax.persistence.Persistence
import javax.ws.rs.core.Response


class HibernateDbLayer(val emf: EntityManagerFactory) : DbLayer,
        DbDestinationLayer by HibernateDestinationLayer(HibernateCrud(emf, FullDestination::class.java)),
        DbJobLayer by HibernateJobLayer(HibernateCrud(emf, PrintJob::class.java)),
        DbQueueLayer by HibernateQueueLayer(HibernateCrud(emf, PrintQueue::class.java)),
        DbMarqueeLayer by HibernateMarqueeLayer(HibernateCrud(emf, MarqueeData::class.java)),
        DbSessionLayer by HibernateSessionLayer(HibernateCrud(emf, FullSession::class.java)),
        DbUserLayer by HibernateUserLayer(HibernateCrud(emf, FullUser::class.java)){
    companion object{
        fun makeEntityManagerFactory(persistenceUnitName: String): EntityManagerFactory {
            val props = HashMap<String, String>()
            props.put("javax.persistence.jdbc.url", PropsDB.URL)
            props.put("javax.persistence.jdbc.user", PropsDB.USERNAME)
            props.put("javax.persistence.jdbc.password", PropsDB.PASSWORD)
            val emf = Persistence.createEntityManagerFactory(persistenceUnitName, props)
            return emf
        }
    }
}


class HibernateDestinationLayer(val hc : HibernateCrud<FullDestination, String?>) : DbDestinationLayer{
    override fun getDestination(id: Id): FullDestination {
        return hc.read(id) ?: failNotFound("Could not find destination {\"ID\":\"$id\"}")
    }

    override fun getDestinations(): List<FullDestination> {
        return hc.readAll()
    }

    override fun putDestinations(destinations: Map<Id, FullDestination>): String {
        val responses = mutableListOf<PutResponse>()
        destinations.map {
            try{
                it.value._id = it.key
                val u = hc.updateOrCreateIfNotExist(it.value)
                responses.add(PutResponse(ok=true, id=u.getId(),rev=u.getRev()))
            }catch (e:Exception){
                responses.add(PutResponse(ok=false, id=it.value.getId(), rev=it.value.getRev()))
            }
        }

        return mapper.writeValueAsString(responses)
    }

    override fun updateDestinationWithResponse(id: Id, updater: FullDestination.() -> Unit): Response {
        try{
            val destination : FullDestination = hc.read(id) ?: throw EntityNotFoundException()
            updater(destination)
            hc.update(destination)
            return Response.ok().entity(destination).build()
        }catch (e : Exception){
            return parsePersistenceErrorToResponse(e)
        }
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
        return hc.dbOp(){ em ->
                em.createQuery("SELECT c FROM PrintJob c WHERE c.queueName = :queueName AND c.started > :startTime ORDER BY c.started $sort", PrintJob::class.java).
                setParameter("queueName", queue).
                setParameter("startTime", startTime).
                setMaxResults(if(limit==-1) Int.MAX_VALUE else limit).
                resultList}
    }

    override fun getJobsByUser(sam: Sam, sortOrder: Order): List<PrintJob> {
        val sort = if(sortOrder == Order.ASCENDING) "ASC" else "DESC"
        return hc.dbOp {em ->
            em.createQuery("SELECT c FROM PrintJob c WHERE c.userIdentification = :userId ORDER BY c.started $sort", PrintJob::class.java)
                    .setParameter("userId", sam)
                    .resultList
        }
    }

    override fun getStoredJobs(): List<PrintJob> {
        return hc.dbOp { em ->
            em.createQuery("SELECT c FROM PrintJob c WHERE c.file != null", PrintJob::class.java).resultList
        }
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
            val job = updateJob(id,updater)
            return Response.ok().entity(PutResponse(ok=true, id = id, rev = job?.getRev() ?: "")).build()
        }catch(e : Exception){
            return parsePersistenceErrorToResponse(e)
        }
    }

    override fun postJob(job: PrintJob): Response {
        try{
            val out = hc.create(job)
            return Response.ok().entity(PutResponse(ok=true, id = out._id ?: "", rev = out.getRev())).build()
        }catch (e : Exception){
            return parsePersistenceErrorToResponse(e)
        }
    }

    override fun getJobFile(id: Id, file: String): InputStream? {
        throw NotImplementedError()
    }

    override fun getOldJobs(): List<PrintJob> {
        return hc.dbOp { em ->
            em.createQuery("SELECT c FROM PrintJob c WHERE c.processed = -1 AND c.failed = -1", PrintJob::class.java).resultList
        }
    }
}

class HibernateQueueLayer(val hc : HibernateCrud<PrintQueue, String?>) : DbQueueLayer {
    override fun getQueue(id: Id): PrintQueue {
        val allQueues = hc.readAll()
        val q = allQueues.find { it._id == id.padEnd(36)} ?: failNotFound("Could not find PrintQueue {\"ID\":\"$id\"}")
        return q
    }

    override fun getQueues(): List<PrintQueue> {
        return hc.readAll()
    }

    override fun putQueues(queues: Collection<PrintQueue>): Response {
        try{
            return Response.ok().entity(queues.map { hc.updateOrCreateIfNotExist(it) }).build()
        }catch (e : Exception){
            return parsePersistenceErrorToResponse(e)
        }
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
        return hc.dbOp { em ->
            em.createQuery("SELECT max(c.eta) from PrintJob c WHERE c.destination = :destinationId")
                    .setParameter("destinationId", destinationId)
                    .singleResult as? Long ?: 0
        }
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
            return Response.ok().entity(hc.updateOrCreateIfNotExist(session)).build()
        }catch (e : Exception){
            return parsePersistenceErrorToResponse(e)
        }
    }

    override fun getSessionOrNull(id: Id): FullSession? {
        return hc.read(id)
    }

    override fun getSessionIdsForUser(shortUser: ShortUser): List<Id> {
        return hc.dbOp { em ->
            em.createQuery("SELECT c.id FROM FullSession c WHERE c.user.shortUser = :userId", String::class.java)
                    .setParameter("userId", shortUser)
                    .resultList
        }
    }

    override fun getAllSessions(): List<FullSession> {
        return hc.readAll()
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
            val updatedUser = hc.updateOrCreateIfNotExist(user)
            return Response.ok().entity(PutResponse(ok=true, id = updatedUser.getId(), rev = updatedUser.getRev())).build()
        }catch (e : Exception){
            return parsePersistenceErrorToResponse(e)
        }
    }

    private val numRegex = Regex("[0-9]+")

    override fun getUserOrNull(sam: Sam): FullUser? {
        try {
            return when {
                sam.contains(".") -> hc.dbOp { em ->
                    em.createQuery("SELECT c FROM FullUser c WHERE c.longUser = :lu", FullUser::class.java)
                            .setParameter("lu", "${sam.substringBefore("@")}%40${Config.ACCOUNT_DOMAIN}")
                            .singleResult
                }
                sam.matches(numRegex) -> hc.dbOp { em ->
                    em.createQuery("SELECT c FROM FullUser c WHERE c.studentId = :id", FullUser::class.java)
                            .setParameter("id", sam.toInt())
                            .singleResult
                }
                else -> hc.read("u$sam")
            }
        } catch (e: Exception){
            // TODO: More judicious error handling
            return null
        }
    }

    override fun isAdminConfigured(): Boolean {
        return (hc.dbOp { em ->
            em.createQuery("SELECT COUNT(c) FROM FullUser c")
                    .singleResult
        } as? Long ?: 0) > 0
    }

    override fun getTotalPrintedCount(shortUser: ShortUser): Int {
        return (hc.dbOp { em ->
            em.createQuery("SELECT SUM(c.pages)+2*SUM(c.colorPages) FROM PrintJob c WHERE c.userIdentification = :userId AND c.isRefunded = FALSE").setParameter("userId", shortUser).singleResult
        } as? Long ?: 0).toInt()
    }
}
