package ca.mcgill.science.tepid.server.db

import ca.mcgill.science.tepid.models.bindings.TepidDb
import ca.mcgill.science.tepid.server.util.logMessage
import ca.mcgill.science.tepid.server.util.text
import org.apache.logging.log4j.kotlin.Logging
import java.util.*
import javax.persistence.EntityExistsException
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityNotFoundException
import javax.persistence.NoResultException
import javax.ws.rs.core.Response

interface IHibernateConnector : Logging {
    val emf: EntityManagerFactory
    fun <T> dbOp(errorLogger: (Exception) -> String = { logMessage("DB error") }, f: (em: EntityManager) -> T): T
    fun <T> dbOpTransaction(
            errorLogger: (Exception) -> String = { logMessage("DB error") },
            f: (em: EntityManager) -> T
    ): T
}

class HibernateConnector(override val emf: EntityManagerFactory) : IHibernateConnector {

    override fun <T> dbOp(errorLogger: (Exception) -> String, f: (em: EntityManager) -> T): T {
        val em = emf.createEntityManager()
        try {
            return f(em)
        } catch (e: Exception) {
            e.printStackTrace()
            logger.error(errorLogger(e), e)
            throw e
        } finally {
            em.close()
        }
    }

    override fun <T> dbOpTransaction(
            errorLogger: (Exception) -> String,
            f: (em: EntityManager) -> T
    ): T {
        return dbOp(errorLogger) {
            try {
                it.transaction.begin()
                val o = f(it)
                it.transaction.commit()
                return@dbOp o
            } catch (e: Exception) {
                logger.error(errorLogger(e), e)
                e.printStackTrace()
                it.transaction.rollback()
                throw e
            }
        }
    }
}

interface IHibernateCrud<T : Any, P> : Logging, IHibernateConnector {
    val classParameter: Class<T>

    fun create(obj: T): T

    fun read(id: P): T?

    fun readAll(): List<T>

    fun update(obj: T): T

    fun update(id: P, updater: T.() -> Unit): T

    fun delete(obj: T)

    fun deleteById(id: P)

    fun updateOrCreateIfNotExist(obj: T): T
}

class HibernateCrud<T : TepidDb, P>(override val emf: EntityManagerFactory, override val classParameter: Class<T>) : IHibernateCrud<T, P>,
        IHibernateConnector by HibernateConnector(emf) {

    override fun create(obj: T): T {
        return dbOpTransaction(
                { logMessage("error inserting object", "object" to obj) },
                { em -> em.merge(obj) }
        )
    }

    override fun read(id: P): T? {
        return try {
            dbOp(
                    { logMessage("error reading object", "class" to classParameter, "id" to id) },
                    { em -> em.find(classParameter, id) }
            )
        } catch (e: NoResultException) {
            null
        }
    }

    override fun readAll(): List<T> {
        return dbOp(
                { logMessage("error reading all objects", "class" to classParameter) },
                { em ->
                    em.createQuery("SELECT c FROM ${classParameter.simpleName} c", classParameter).resultList
                            ?: emptyList()
                }
        )
    }

    override fun update(obj: T): T {
        return dbOpTransaction(
                { logMessage("error updating object", "object" to obj) },
                { em -> em.merge(obj); return@dbOpTransaction obj }
        )
    }

    override fun update(id: P, updater: T.() -> Unit): T {
        val o = read(id) ?: throw EntityNotFoundException()
        updater(o)
        update(o)
        return o
    }

    override fun delete(obj: T) {
        dbOpTransaction(
                { logMessage("error deleting object", "object" to obj) },
                { it.remove(if (it.contains(obj)) obj else it.merge(obj)) }
        )
    }

    override fun deleteById(id: P) {
        val em = emf.createEntityManager()
        try {
            val u = em.find(classParameter, id)
            delete(u)
        } finally {
            em.close()
        }
    }

    override fun updateOrCreateIfNotExist(obj: T): T {
        obj._id
                ?: return run { obj._id = UUID.randomUUID().toString(); create(obj) } // has no ID, needs to be created
        val em = emf.createEntityManager()
        try {
            em.getReference(classParameter, obj._id)
            return dbOpTransaction(
                    { logMessage("error putting modifications", "object" to obj) },
                    { t -> t.merge(obj) }
            )
        } catch (e: EntityNotFoundException) {
            return (create(obj)) // has ID, ID not in DB
        } finally {
            em.close()
        }
    }
}

fun parsePersistenceErrorToResponse(e: Exception): Response {
    return when (e) {
        is EntityNotFoundException -> Response.Status.NOT_FOUND.text("Not found")
        is IllegalArgumentException -> Response.Status.BAD_REQUEST.text("${e::class.java.simpleName} occurred")
        is EntityExistsException -> Response.Status.CONFLICT.text("Entity Exists; ${e::class.java.simpleName} occurred")
        else -> Response.Status.INTERNAL_SERVER_ERROR.text("Ouch! ${e::class.java.simpleName} occurred")
    }
}
