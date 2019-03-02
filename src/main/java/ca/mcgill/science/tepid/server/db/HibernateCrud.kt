package ca.mcgill.science.tepid.server.db

import ca.mcgill.science.tepid.utils.Loggable
import ca.mcgill.science.tepid.utils.WithLogging
import javax.persistence.EntityManager

interface IHibernateCrud : Loggable {

    fun <T> create(obj:T)

    fun <T, P> read(classParameter: Class<T> ,id:P) : T

    fun <T> update(obj:T)

    fun <T> delete(obj:T)

    fun <T, P> deleteById(classParameter:Class<T>, id:P)

}

class HibernateCrud(val em: EntityManager): IHibernateCrud, Loggable by WithLogging() {


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

    override fun <T> create(obj:T) {
        return dbOpTransaction({ em -> em.persist(obj) }, { e -> "Error inserting object {\"object\":\"$obj\", \"error\":\"$e\"" })
    }

    inline fun <reified T, P> read(id: P): T{
        return read((T::class.java), id)
    }

    override fun <T,P> read(classParameter: Class<T>, id:P):T{
        return dbOp{em -> em.find(classParameter, id)}
    }

    override fun <T> update(obj:T) {
        dbOpTransaction<Unit>({ em -> em.merge(obj) }, { e -> "Error updating object {\"object\":\"$obj\", \"error\":\"$e\"" })
    }
}