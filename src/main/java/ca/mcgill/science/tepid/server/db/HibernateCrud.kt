package ca.mcgill.science.tepid.server.db

import ca.mcgill.science.tepid.models.bindings.TepidId
import ca.mcgill.science.tepid.server.util.text
import ca.mcgill.science.tepid.utils.Loggable
import ca.mcgill.science.tepid.utils.WithLogging
import javax.persistence.EntityExistsException
import javax.persistence.EntityManager
import javax.persistence.EntityNotFoundException
import javax.ws.rs.core.Response

interface IHibernateCrud <T, P> : Loggable {

    fun create(obj:T)

    fun read(id:P) : T

    fun readAll() : List<T>

    fun update(obj:T)

    fun delete(obj:T)

    fun deleteById(id:P)

    fun updateOrCreateIfNotExist(obj:T)

}

class HibernateCrud <T: TepidId, P>(val em: EntityManager, val classParameter:Class<T>) : IHibernateCrud <T, P>, Loggable by WithLogging() {

    fun <T>dbOp(errorLogger : (e:Exception)->String, f:(em: EntityManager)->T):T{
        try {
            return f(em)
        } catch (e:Exception){
            log.error(errorLogger(e))
            throw e
        }
    }

    fun <T> dbOpTransaction(errorLogger : (e:Exception)->String, f:(em:EntityManager)->T){
        dbOp(errorLogger){
            try {
                it.transaction.begin()
                f(it)
                it.transaction.commit()
            } catch (e: Exception){
                log.error(errorLogger(e))
                it.transaction.rollback()
                throw e
            }
        }
    }

    override fun create(obj:T) {
        return dbOpTransaction({ e -> "Error inserting object {\"object\":\"$obj\", \"error\":\"$e\"" }, { em -> em.persist(obj) })
    }

    override fun read(id:P):T{
        return dbOp(
                { e -> "Error reading object {\"class\":\"$classParameter\",\"id\":\"$id\", \"error\":\"$e\"" },
                {em -> em.find(classParameter, id)})
    }

    override fun readAll(): List<T> {
        return dbOp(
            {e -> "Error reading all objects {\"class\":\"$classParameter\", \"error\":\"$e\""},
            {
                em -> em.createQuery("SELECT c FROM ${classParameter.simpleName} c", classParameter).resultList ?: emptyList<T>()
            }
        )
    }

    override fun update(obj:T) {
        dbOpTransaction<Unit>({ e -> "Error updating object {\"object\":\"$obj\", \"error\":\"$e\"" },{ em -> em.merge(obj) })
    }

    override fun delete(obj: T) {

        dbOpTransaction({ e ->
            "Error deleting object {\"object\":\"$obj\", \"error\":\"$e\"}"
        },
                {
            it.remove(obj)
        })
    }

    override fun deleteById(id: P) {
        val u = em.find(classParameter, id)
        delete(u)
    }

    override fun updateOrCreateIfNotExist(obj: T) {
        obj._id ?: return (create(obj))
        dbOpTransaction({e->"Error putting modifications {\"object\":\"$obj\", \"error\":\"$e\"}"}){
            em -> em.merge(obj)
        }
    }
}

fun parsePersistenceErrorToResponse(e: Exception): Response{
    return when (e::class) {
        EntityNotFoundException::class -> Response.Status.NOT_FOUND.text("Not found")
        IllegalArgumentException::class -> Response.Status.BAD_REQUEST.text("${e::class.java.simpleName} occurred")
        EntityExistsException::class -> Response.Status.CONFLICT.text("Entity Exists; ${e::class.java.simpleName} occurred")
        else -> Response.Status.INTERNAL_SERVER_ERROR.text("")
    }
}