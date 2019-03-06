package ca.mcgill.science.tepid.server.db

import ca.mcgill.science.tepid.models.bindings.TepidDb
import ca.mcgill.science.tepid.models.bindings.TepidDbDelegate
import ca.mcgill.science.tepid.models.bindings.TepidId
import ca.mcgill.science.tepid.models.data.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.*
import javax.persistence.*
import kotlin.test.*

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
        list.toList().map { e ->
            em.detach(e)
//            e._id = e._id ?: newId()
            e._id = newId()
            persist(e)
        }
    }

    internal fun newId() = UUID.randomUUID().toString()

    internal fun <T> truncate(classParameter: Class<T>){
        em.transaction.begin()
        em.createQuery("DELETE FROM ${classParameter.simpleName} e").executeUpdate()
        em.transaction.commit()
    }

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

    @AfterEach
    fun truncateUsed(){
        val u = listOf(TestEntity::class.java)
        u.forEach { truncate(it) }
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

    @AfterEach
    fun truncateUsed(){
        val u = listOf(MarqueeData::class.java)
        u.forEach { truncate(it) }
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
        val testItem = testItems.first().copy()
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
        val testItem = testItems.first().copy()
        val id = "testDeleteDestination"
        testItem._id = id
        persist(testItem)

        val response = hl.deleteDestination(id)

        val retrieved = hl.hc.read(id)

        assertNull(retrieved)
    }

    @AfterEach
    fun truncateUsed(){
        val u = listOf(FullDestination::class.java)
        u.forEach { truncate(it) }
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

    @Test
    fun testGetJobsByQueue(){
        persistMultiple(testItems)

        val retrieved = hl.getJobsByQueue("Queue1")

        assertEquals(3, retrieved.size)
        assertEquals(listOf("1", "2", "3").sorted(), retrieved.map{it.name}.sorted())
        assertTrue(retrieved.fold(true) {res,e -> (e.queueName == "Queue1") && res})
    }

    @Test
    fun testGetJobsByQueueSort(){
        persistMultiple(testItems)

        val retrieved = hl.getJobsByQueue("Queue1", sortOrder = Order.DESCENDING)

        assertEquals(3, retrieved.size)
        assertEquals(listOf("3", "2", "1").sorted(), retrieved.map{it.name}.sorted())
        assertTrue(retrieved.fold(true) {res,e -> (e.queueName == "Queue1") && res})
    }

    @Test
    fun testGetJobsByQueueLimit(){
        persistMultiple(testItems)

        val retrieved = hl.getJobsByQueue("Queue1", limit=1)

        assertEquals(1, retrieved.size)
        assertEquals(listOf(testItems[2]), retrieved)
    }

    @Test
    fun testGetJobsByQueueMaxage(){
        persistMultiple(testItems)

        val retrieved = hl.getJobsByQueue("Queue1", maxAge=35000)

        //assertEquals(2, retrieved.size)
        assertEquals(listOf(testItems[2], testItems[1]), retrieved)
    }

    @Test
    fun testGetJobsByUser(){
        persistMultiple(testItems)

        val retrieved = hl.getJobsByUser("USER1")

        assertEquals(3, retrieved.size)
        assertEquals(listOf("1", "3", "4").sorted(), retrieved.map{it.name}.sorted())
        assertTrue(retrieved.fold(true) {res,e -> (e.userIdentification == "USER1") && res})
    }

    @Test
    fun testUpdateJob(){
        val ti = testItems.first().copy()
        val id = newId()
        ti._id = id
        persist(ti)

        hl.updateJob(id){name = "NEWNAME"}

        val ri = hl.hc.read(id) ?: fail("Not Persisted")
        assertEquals("NEWNAME", ri.name)
        assertEquals(ti, ri)
    }

    @Test
    fun testPostJob(){
        val ti = testItems.first().copy()
        val id = newId()
        ti._id = id

        hl.postJob(ti)

        val ri = hl.hc.read(id)
        assertEquals(ti, ri)
    }

    @AfterEach
    fun truncateUsed(){
        val u = listOf(PrintJob::class.java)
        u.forEach { truncate(it) }
    }

    companion object {
        val now = Date().time
        val testItems  = listOf(
                PrintJob("1", userIdentification = "USER1", queueName = "Queue1", started = now - 40000),
                PrintJob("2", userIdentification = "USER2",queueName = "Queue1", started = now - 30000),
                PrintJob("3", userIdentification = "USER1", queueName = "Queue1", started = now - 20000),
                PrintJob("4", userIdentification = "USER1", queueName = "Queue2", started = now - 10000)
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

class HibernateQueueLayerTest() : DbTest() {

    @Test
    fun testGetQueues(){
        persistMultiple(testItems)

        val ri = hl.getQueues()

        assertEquals(testItems, ri)
    }

    @Test
    fun testPutQueuesCreate(){
        val ti = testItems.map {
            em.detach(it)
            it._id = newId()
            it
        }

        val response = hl.putQueues(ti)

        val ri = hc.readAll()
        assertEquals(ti.sortedBy { it.name }, ri.sortedBy { it.name })
    }

    @Test
    fun testPutQueuesUpdate(){
        val ti = testItems.toList()
        ti.map { it._id = newId()}
        persistMultiple(ti)

        ti.map { it.loadBalancer = "PerfectlyBalanced" }

        val response = hl.putQueues(ti)

        val ri = hc.readAll()
        assertEquals(ti.sortedBy { it.name }, ri.sortedBy { it.name })
        assertTrue(ri.fold(true) {res,e -> (e.loadBalancer == "PerfectlyBalanced") && res})
    }

    @Test
    fun testDeleteQueue(){
        val ti = testItems.first()
        em.detach(ti)
        val id = newId()
        ti._id = id
        persist(ti)

        val response = hl.deleteQueue(id)

        val retrieved = hl.hc.read(id)
        assertNull(retrieved)
    }

    @AfterEach
    fun truncateUsed(){
        val u = listOf(PrintQueue::class.java)
        u.forEach { truncate(it) }
    }

    companion object {
        val testItems  = listOf(
                PrintQueue(name = "1"),
                PrintQueue(name = "2"),
                PrintQueue(name = "3")
        )

        lateinit var hc: HibernateCrud<PrintQueue, String?>
        lateinit var hl: HibernateQueueLayer

        @JvmStatic
        @BeforeAll
        fun initHelper(){
            hc = HibernateCrud(em, PrintQueue::class.java)
            hl = HibernateQueueLayer(hc)
        }
    }
}

class HibernateSessionLayerTest() : DbTest() {

    @Test
    fun testGetSessionIdsForUser(){
        persistMultiple(testUsers)
        persistMultiple(testItems)

        val ri = hl.getSessionIdsForUser(testUsers[0].shortUser!!)

        assertEquals(2, ri.size)
        assertTrue{ri.contains(testItems[0]._id)}
        assertTrue{ri.contains(testItems[2]._id)}
    }

    @AfterEach
    fun truncateUsed(){
        val u = listOf(FullSession::class.java, FullUser::class.java)
        u.forEach { truncate(it) }
    }

    companion object {
        val testUsers = listOf(
                FullUser(shortUser = "USER1"),
                FullUser(shortUser = "USER2")
        )

        val testItems  = listOf(
                FullSession(user = testUsers[0], expiration = 100),
                FullSession(user = testUsers[1], expiration = 200),
                FullSession(user = testUsers[0], expiration = 300)
        )

        lateinit var hc: HibernateCrud<FullSession, String?>
        lateinit var hl: HibernateSessionLayer

        @JvmStatic
        @BeforeAll
        fun initHelper(){
            hc = HibernateCrud(em, FullSession::class.java)
            hl = HibernateSessionLayer(hc)
        }
    }
}

class HibernateUserLayerTest() : DbTest() {

    @Test
    fun testIsAdminConfiguredTrue(){
        persist(testItems[0])

        val ri = hl.isAdminConfigured()

        assertTrue (ri)
    }

    @Test
    fun testIsAdminConfiguredFalse(){
        val ri = hl.isAdminConfigured()

        assertFalse (ri)
    }

    @Test
    fun testGetTotalPrintedCount(){
        persistMultiple(testItems)
        persistMultiple(testPrints)

        val ri = hl.getTotalPrintedCount(testItems[0].shortUser!!)

        assertEquals(140, ri)
    }

    @AfterEach
    fun truncateUsed(){
        val u = listOf(PrintJob::class.java, FullUser::class.java)
        u.forEach { truncate(it) }
    }

    companion object {
        val testPrints = listOf(
                PrintJob(name="1", pages = 29, colorPages = 11, userIdentification = "USER1", isRefunded = false),
                PrintJob(name="1", pages = 31, colorPages = 13, userIdentification = "USER1", isRefunded = true),
                PrintJob(name="1", pages = 37, colorPages = 17, userIdentification = "USER2", isRefunded = true),
                PrintJob(name="1", pages = 41, colorPages = 19, userIdentification = "USER2", isRefunded = false),
                PrintJob(name="1", pages = 43, colorPages = 23, userIdentification = "USER1", isRefunded = false)
        )

        val testItems  = listOf(
                FullUser(shortUser = "USER1"),
                FullUser(shortUser = "USER2")
        )

        lateinit var hc: HibernateCrud<FullUser, String?>
        lateinit var hl: HibernateUserLayer

        @JvmStatic
        @BeforeAll
        fun initHelper(){
            hc = HibernateCrud(em, FullUser::class.java)
            hl = HibernateUserLayer(hc)
        }
    }
}
