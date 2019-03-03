package ca.mcgill.science.tepid.server.db

import ca.mcgill.science.tepid.models.bindings.TepidDb
import ca.mcgill.science.tepid.models.bindings.TepidDbDelegate
import ca.mcgill.science.tepid.models.bindings.TepidId
import ca.mcgill.science.tepid.models.data.FullDestination
import ca.mcgill.science.tepid.models.data.MarqueeData
import ca.mcgill.science.tepid.models.data.PrintJob
import junit.framework.Assert.assertNull
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.*
import javax.persistence.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

@Entity
data class TestEntity(
        @Column(nullable = false)
        var content: String = ""
) : @EmbeddedId TepidDb by TepidDbDelegate()

open class DbTest {

    fun <C> persist (obj:C){
        em.transaction.begin()
        em.persist(obj)
        em.transaction.commit()
    }

    fun<T:TepidId> persistMultiple (list:List<T>){
        list.map { e -> e._id=newId(); persist(e)}
    }

    internal fun newId() = UUID.randomUUID().toString()

    /*@BeforeEach
    fun initialiseDb(){
       val session = em.unwrap(Session::class.java);
       session.doWork(Work { c: Connection ->
           val script = File(this::class.java.classLoader.getResource("content.sql").file)
           RunScript.execute(c, FileReader(script))
       })
    }*/

    companion object {
        lateinit var emf: EntityManagerFactory
        lateinit var em: EntityManager

        @JvmStatic
        @BeforeAll
        fun initTest() {
            emf = Persistence.createEntityManagerFactory("hibernate-pu-test")
            em = emf.createEntityManager()
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

class HibernateCrudTest() : DbTest(){

    @Test
    fun testPsqlCrudCreate(){
        val te = TestEntity("TEST")
        te._id = "ID0"

        pc.create(te)

        val re = em.find(TestEntity::class.java, te._id)
        assertEquals(te, re)
    }

    @Test
    fun testPsqlCrudRead(){
        val te = TestEntity("TEST")
        te._id = "ID1"
        em.transaction.begin()
        em.persist(te)
        em.transaction.commit()

        val re = pc.read(te._id)

        assertEquals(te, re)
    }

    @Test
    fun testPsqlCrudReadAll(){
        val testItems = listOf(TestEntity("1"),TestEntity("2"),TestEntity("3"))
        persistMultiple(testItems)

        val retrieved = pc.readAll()

        assertEquals(testItems, retrieved)
    }

    @Test
    fun testPsqlCrudUpdate(){
        val te = TestEntity("TEST")
        te._id = "ID2"
        em.transaction.begin()
        em.persist(te)
        em.transaction.commit()
        te.content = "NEW"

        pc.update(te)

        val re = em.find(TestEntity::class.java, te._id)
        assertEquals(te, re)
    }

    @Test
    fun testPsqlCrudDelete(){
        val te = TestEntity("TEST")
        te._id = "ID3"
        em.transaction.begin()
        em.persist(te)
        em.transaction.commit()

        pc.delete(te)

        val re = em.find(TestEntity::class.java, te._id)
        assertNull(re)
    }

    @Test
    fun testPsqlCrudDeleteById(){
        val te = TestEntity("TEST")
        te._id = "ID4"
        em.transaction.begin()
        em.persist(te)
        em.transaction.commit()

        pc.deleteById(te._id)

        val re = em.find(TestEntity::class.java, te._id)
        assertNull(re)
    }

    @Test
    fun testPsqlCrudUpdateOrCreateIfNotExistWithId(){
        updateOrCreateIfNotExistTest("ID2")
    }

    @Test
    fun testPsqlCrudUpdateOrCreateIfNotExistWithoutId(){
        updateOrCreateIfNotExistTest()
    }

    private fun updateOrCreateIfNotExistTest(id : String? = null) {
        val te = TestEntity("TEST")
        if (id != null) te._id = id

        pc.updateOrCreateIfNotExist(te)
        val reCreated = em.find(TestEntity::class.java, te._id)
        assertEquals(te, reCreated)

        te.content = "NEW"

        pc.updateOrCreateIfNotExist(te)

        val reUpdate = em.find(TestEntity::class.java, te._id)
        assertEquals(te, reUpdate)
    }


    companion object {
        lateinit var pc: HibernateCrud<TestEntity, String?>

        @JvmStatic
        @BeforeAll
        fun initHelper(){
            pc = HibernateCrud(em, TestEntity::class.java)
        }
    }
}

class HibernateMarqueeLayerTest : DbTest(){

    @Test
    fun testMultipleItems(){
        val testItems = listOf(MarqueeData("T1"),MarqueeData("T2"),MarqueeData("T3"))
        persistMultiple(testItems)

        val retrieved = hml.getMarquees()

        assertEquals(testItems, retrieved)
    }

    companion object {
        lateinit var hc: HibernateCrud<MarqueeData, String?>
        lateinit var hml: HibernateMarqueeLayer

        @JvmStatic
        @BeforeAll
        fun initHelper(){
            hc = HibernateCrud(em, MarqueeData::class.java)
            hml = HibernateMarqueeLayer(hc)
        }
    }
}

class HibernateDestinationLayerTest : DbTest(){

    @Test
    fun testGetDestination(){

        persistMultiple(testItems)

        val retrieved = hl.getDestinations()

        assertEquals(testItems,retrieved)
    }

    @Test
    fun testPutDestination(){
        val testList = testItems.toList().
                map { it._id = newId(); it}
        val testMap = testList.
                map { it._id!! to it}.
                toMap()

        val result = hl.putDestinations(testMap)

        val retrieved = hl.getDestinations()
        assertEquals(testList, retrieved)
    }

    @Test
    fun testUpdateDestinationWithResponse(){
        val testItem = testItems.first()
        val id = "testUpdateDestinationWithResponse"
        val newName = "A NEW NAME"
        testItem._id = id
        persist(testItem)



        val response = hl.updateDestinationWithResponse(id) {
            this.name = newName
        }

        val retrieved = hl.hc.read(id)
        assertNotNull(retrieved)
        assertEquals(retrieved.name, newName)
        assertEquals(response.status, 200)
    }

    @Test
    fun testDeleteDestination(){
        val testItem = testItems.first()
        val id = "testDeleteDestination"
        testItem._id = id
        persist(testItem)

        val response = hl.deleteDestination(id)

        val retrieved = hl.hc.read(id)

        assertNull(retrieved)
    }

    companion object {
        val testItems  = listOf(
                FullDestination("1"),
                FullDestination("2"),
                FullDestination("3")
        )

        lateinit var hc: HibernateCrud<FullDestination, String?>
        lateinit var hl: HibernateDestinationLayer

        @JvmStatic
        @BeforeAll
        fun initHelper(){
            hc = HibernateCrud(em, FullDestination::class.java)
            hl = HibernateDestinationLayer(hc)
        }
    }
}

class HibernateJobLayerTest : DbTest() {

    companion object {
        val testItems  = listOf(
                PrintJob("1", userIdentification = "USER1", queueName = "Queue1"),
                PrintJob("2", userIdentification = "USER2",queueName = "Queue1"),
                PrintJob("3", userIdentification = "USER1", queueName = "Queue1"),
                PrintJob("4", userIdentification = "USER1", queueName = "Queue2")
        )

        lateinit var hc: HibernateCrud<PrintJob, String?>
        lateinit var hl: HibernateJobLayer

        @JvmStatic
        @BeforeAll
        fun initHelper(){
            hc = HibernateCrud(em, PrintJob::class.java)
            hl = HibernateJobLayer(hc)
        }
    }
}