package ca.mcgill.science.tepid.server.db

import ca.mcgill.science.tepid.models.data.FullDestination
import ca.mcgill.science.tepid.models.data.FullSession
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.MarqueeData
import ca.mcgill.science.tepid.models.data.PersonalIdentifier
import ca.mcgill.science.tepid.models.data.PrintJob
import ca.mcgill.science.tepid.models.data.PrintQueue
import ca.mcgill.science.tepid.models.data.PutResponse
import ca.mcgill.science.tepid.models.data.Semester
import ca.mcgill.science.tepid.models.data.ShortUser
import ca.mcgill.science.tepid.server.server.Config
import ca.mcgill.science.tepid.server.server.mapper
import ca.mcgill.science.tepid.server.util.failNotFound
import ca.mcgill.science.tepid.server.util.logError
import ca.mcgill.science.tepid.server.util.logMessage
import ca.mcgill.science.tepid.utils.PropsDB
import java.io.InputStream
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
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
    DbUserLayer by HibernateUserLayer(HibernateCrud(emf, FullUser::class.java)),
    DbQuotaLayer by HibernateQuotaLayer(emf) {
    companion object {
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

class HibernateDestinationLayer(hc: IHibernateCrud<FullDestination, String?>) : DbDestinationLayer, IHibernateCrud<FullDestination, String?> by hc {
    override fun getDestination(id: Id): FullDestination {
        return read(id) ?: failNotFound(logMessage("could not find destination", "ID" to id))
    }

    override fun getDestinations(): List<FullDestination> {
        return readAll()
    }

    override fun putDestinations(destinations: Map<Id, FullDestination>): String {
        val responses = mutableListOf<PutResponse>()
        destinations.map {
            try {
                it.value._id = it.key
                val u = updateOrCreateIfNotExist(it.value)
                responses.add(PutResponse(ok = true, id = u.getId(), rev = u.getRev()))
            } catch (e: Exception) {
                responses.add(PutResponse(ok = false, id = it.value.getId(), rev = it.value.getRev()))
            }
        }

        return mapper.writeValueAsString(responses)
    }

    override fun updateDestinationWithResponse(id: Id, updater: FullDestination.() -> Unit): Response {
        try {
            val destination: FullDestination = read(id) ?: throw EntityNotFoundException()
            updater(destination)
            update(destination)
            return Response.ok().entity(destination).build()
        } catch (e: Exception) {
            return parsePersistenceErrorToResponse(e)
        }
    }

    override fun deleteDestination(id: Id): String {
        val failures = mutableListOf<String>()
        try {
            deleteById(id)
        } catch (e: Exception) {
            failures.add(e.message ?: "Generic Failure for ID: $id")
        }

        return mapper.writeValueAsString(if (failures.isEmpty()) "Success" else failures)
    }
}

class HibernateJobLayer(hc: IHibernateCrud<PrintJob, String?>) : DbJobLayer, IHibernateCrud<PrintJob, String?> by hc {
    override fun getJob(id: Id): PrintJob {
        return read(id) ?: failNotFound("{\"id\":\"$id\"}")
    }

    override fun getJobsByQueue(queue: String, maxAge: Long, sortOrder: Order, limit: Int): List<PrintJob> {
        val sort = if (sortOrder == Order.ASCENDING) "ASC" else "DESC"
        val startTime = if (maxAge > 0) Date().time - maxAge else 0
        return dbOp() { em ->
            em.createQuery(
                "SELECT c FROM PrintJob c WHERE c.queueName = :queueName AND c.started > :startTime ORDER BY c.started $sort",
                PrintJob::class.java
            ).setParameter("queueName", queue).setParameter("startTime", startTime)
                .setMaxResults(if (limit == -1) Int.MAX_VALUE else limit).resultList
        }
    }

    override fun getJobsByUser(su: ShortUser, sortOrder: Order): List<PrintJob> {
        val sort = if (sortOrder == Order.ASCENDING) "ASC" else "DESC"
        return dbOp { em ->
            em.createQuery(
                "SELECT c FROM PrintJob c WHERE c.userIdentification = :userId ORDER BY c.started $sort",
                PrintJob::class.java
            )
                .setParameter("userId", su)
                .resultList
        }
    }

    override fun getStoredJobs(): List<PrintJob> {
        return dbOp { em ->
            em.createQuery("SELECT c FROM PrintJob c WHERE c.file != null", PrintJob::class.java).resultList
        }
    }

    override fun updateJob(id: Id, updater: PrintJob.() -> Unit): PrintJob? {

        try {
            val printJob: PrintJob = read(id) ?: throw EntityNotFoundException()
            updater(printJob)
            update(printJob)
            return printJob
        } catch (e: Exception) {
            logger.logError("error updating printjob", e, "id" to id)
        }
        return null
    }

    override fun updateJobWithResponse(id: Id, updater: PrintJob.() -> Unit): Response {
        try {
            val job = updateJob(id, updater)
            return Response.ok().entity(PutResponse(ok = true, id = id, rev = job?.getRev() ?: "")).build()
        } catch (e: Exception) {
            return parsePersistenceErrorToResponse(e)
        }
    }

    override fun postJob(job: PrintJob): Response {
        try {
            val out = create(job)
            return Response.ok().entity(PutResponse(ok = true, id = out._id ?: "", rev = out.getRev())).build()
        } catch (e: Exception) {
            return parsePersistenceErrorToResponse(e)
        }
    }

    override fun getJobFile(id: Id, file: String): InputStream? {
        throw NotImplementedError()
    }

    override fun getOldJobs(): List<PrintJob> {
        return dbOp { em ->
            em.createQuery("SELECT c FROM PrintJob c WHERE c.processed = -1 AND c.failed = -1", PrintJob::class.java)
                .resultList
        }
    }
}

class HibernateQueueLayer(hc: IHibernateCrud<PrintQueue, String?>) : DbQueueLayer, IHibernateCrud<PrintQueue, String?> by hc {
    override fun getQueue(id: Id): PrintQueue {
        val allQueues = readAll()
        return allQueues.find { it._id == id.padEnd(36) } ?: failNotFound(
            logMessage(
                "could not find PrintQueue",
                "ID" to id
            )
        )
    }

    override fun getQueues(): List<PrintQueue> {
        return readAll()
    }

    override fun putQueues(queues: Collection<PrintQueue>): Response {
        try {
            return Response.ok().entity(queues.map { updateOrCreateIfNotExist(it) }).build()
        } catch (e: Exception) {
            return parsePersistenceErrorToResponse(e)
        }
    }

    override fun deleteQueue(id: Id): String {
        val failures = mutableListOf<String>()
        try {
            deleteById(id)
        } catch (e: Exception) {
            failures.add(e.message ?: "Generic Failure for ID: $id")
        }

        return mapper.writeValueAsString(if (failures.isEmpty()) "Success" else failures)
    }

    override fun getEta(destinationId: Id): Long {
        return dbOp { em ->
            em.createQuery("SELECT max(c.eta) from PrintJob c WHERE c.destination = :destinationId")
                .setParameter("destinationId", destinationId)
                .singleResult as? Long ?: 0
        }
    }
}

class HibernateMarqueeLayer(hc: IHibernateCrud<MarqueeData, String?>) : DbMarqueeLayer, IHibernateCrud<MarqueeData, String?> by hc {

    override fun getMarquees(): List<MarqueeData> {
        return readAll()
    }
}

class HibernateSessionLayer(hc: IHibernateCrud<FullSession, String?>) : DbSessionLayer, IHibernateCrud<FullSession, String?> by hc {
    override fun putSession(session: FullSession): Response {
        try {
            return Response.ok().entity(updateOrCreateIfNotExist(session)).build()
        } catch (e: Exception) {
            return parsePersistenceErrorToResponse(e)
        }
    }

    override fun getSessionOrNull(id: Id): FullSession? {
        return read(id)
    }

    override fun getSessionIdsForUser(shortUser: ShortUser): List<Id> {
        return dbOp { em ->
            em.createQuery("SELECT c.id FROM FullSession c WHERE c.user.shortUser = :userId", String::class.java)
                .setParameter("userId", shortUser)
                .resultList
        }
    }

    override fun getAllSessions(): List<FullSession> {
        return readAll()
    }

    override fun deleteSession(id: Id): String {
        val failures = mutableListOf<String>()
        try {
            deleteById(id)
        } catch (e: Exception) {
            failures.add(e.message ?: "Generic Failure for ID: $id")
        }
        return mapper.writeValueAsString(if (failures.isEmpty()) "Success" else failures)
    }
}

class HibernateUserLayer(hc: IHibernateCrud<FullUser, String?>) : DbUserLayer, IHibernateCrud<FullUser, String?> by hc {
    override fun putUser(user: FullUser): Response {
        try {
            val updatedUser = updateOrCreateIfNotExist(user)
            return Response.ok().entity(PutResponse(ok = true, id = updatedUser.getId(), rev = updatedUser.getRev()))
                .build()
        } catch (e: Exception) {
            return parsePersistenceErrorToResponse(e)
        }
    }

    override fun putUsers(users: Collection<FullUser>) {
        dbOpTransaction { em ->
            users.forEach { em.merge(it) }
        }
    }

    private val numRegex = Regex("[0-9]+")

    override fun getUserOrNull(sam: PersonalIdentifier): FullUser? {
        try {
            return when {
                sam.contains(".") -> dbOp { em ->
                    em.createQuery("SELECT c FROM FullUser c WHERE c.longUser = :lu", FullUser::class.java)
                        .setParameter("lu", "${sam.substringBefore("@")}@${Config.ACCOUNT_DOMAIN}")
                        .singleResult
                }
                sam.matches(numRegex) -> dbOp { em ->
                    em.createQuery("SELECT c FROM FullUser c WHERE c.studentId = :id", FullUser::class.java)
                        .setParameter("id", sam.toInt())
                        .singleResult
                }
                else -> read("u$sam")
            }
        } catch (e: Exception) {
            // TODO: More judicious error handling
            return null
        }
    }

    override fun getAllIfPresent(ids: Set<String>): Set<FullUser> {
        return dbOp { em ->
            em.createQuery("SELECT c FROM FullUser c WHERE c._id in :ids", classParameter)
                .setParameter("ids", ids)
                .resultList.toSet()
        }
    }

    override fun isAdminConfigured(): Boolean {
        return (dbOp { em ->
            em.createQuery("SELECT COUNT(c) FROM FullUser c")
                .singleResult
        } as? Long ?: 0) > 0
    }
}

class HibernateQuotaLayer(emf: EntityManagerFactory) : DbQuotaLayer, IHibernateConnector by HibernateConnector(emf) {
    override fun getTotalPrintedCount(shortUser: ShortUser): Int {
        return (dbOp { em ->
            em.createQuery("SELECT SUM(c.pages)+2*SUM(c.colorPages) FROM PrintJob c WHERE c.userIdentification = :userId AND c.isRefunded = FALSE AND c.failed <= 0")
                .setParameter("userId", shortUser).singleResult
        } as? Long ?: 0).toInt()
    }

    /**
     * So this splits the list of users to fetch into batches small enough that Hibernate's AST parser doesn't choke
     * This is because it does a recursive descent of the list of names, which requires _many_ levels and causes a
     * stackoverflow. The folks at Hibernate are aware  of this, and generally think that there queries are silly.
     * Who knows, maybe I'm the fool.
     */
    override fun getAlreadyGrantedUsers(ids: Set<String>, semester: Semester): Set<String> {
        return dbOp { em ->
            {
                val counter: AtomicInteger = AtomicInteger()
                ids.chunked(300).map {
                    em.createQuery(
                        "SELECT c._id FROM FullUser c JOIN c.semesters s WHERE :t in elements(c.semesters) and c._id in :users",
                        String::class.java
                    )
                        .setParameter("t", Semester.current)
                        .setParameter("users", it)
                        .resultList
                }.flatten().toSet()
            }()
        }
    }
}
