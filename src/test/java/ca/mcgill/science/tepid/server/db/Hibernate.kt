package ca.mcgill.science.tepid.server.db

import junit.framework.Assert.assertTrue
import org.h2.tools.RunScript
import org.hibernate.Session
import org.hibernate.jdbc.Work
import org.hibernate.testing.transaction.TransactionUtil.doInHibernate
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileReader
import java.lang.Exception
import java.sql.Connection

import javax.persistence.*
import kotlin.test.assertNotNull


@Entity
data class TestEntity(
        @javax.persistence.Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        val id: Int,

        @Column(nullable = false)
        val data: String
)

open class DbTest {

    @BeforeEach
    fun initialiseDb(){
        val session = em.unwrap(Session::class.java);
        session.doWork(Work { c: Connection ->
            val script = File(this::class.java.classLoader.getResource("data.sql").file)
            RunScript.execute(c, FileReader(script))
        })
    }

    companion object {
        lateinit var emf: EntityManagerFactory
        lateinit var em: EntityManager

        @JvmStatic
        @BeforeAll
        fun initTest() {
            emf = Persistence.createEntityManagerFactory("hibernate-pu-test");
            em = emf.createEntityManager();
        }

        @JvmStatic
        @AfterAll
        fun tearTest(){
            em.clear()
            em.close()
            emf.close()
        }
    }



}

class HibernateCrudTest : DbTest() {

    @Test
    fun testGetObjectById_Success(){
        val te : TestEntity = em.find(TestEntity::class.java, 1);
        assertNotNull(te);
    }

}

