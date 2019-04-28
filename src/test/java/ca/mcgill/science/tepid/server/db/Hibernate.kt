package ca.mcgill.science.tepid.server.db

import ca.mcgill.science.tepid.models.bindings.TepidDb
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
) : TepidDb()

@Entity
data class TestEntity1(
        @Column(nullable = false)
        var content: String = ""
) : TepidDb()

@Entity
data class TestContainingEntity(
        @Access(AccessType.FIELD)
        @OneToMany(targetEntity = TestEntity::class, cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
        var set0 : MutableSet<TestEntity> = mutableSetOf(),
        @Access(AccessType.FIELD)
        @OneToMany(targetEntity = TestEntity1::class, cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
        var set1 : MutableSet<TestEntity1> = mutableSetOf()
) : TepidDb()

@Entity
data class fs(
        var role: String = "",
        @Access(AccessType.FIELD)
        @ManyToOne(targetEntity = FullUser::class)
        var user: FullUser?,
        var expiration: Long = -1L,
        var persistent: Boolean = true
) : TepidDb(type="session") {
}

@Entity
data class TestForeignKey(
/*        @Access(AccessType.FIELD)
        @ManyToOne(fetch = FetchType.EAGER)
        var datum : TestEntity*/
        @Access(AccessType.FIELD)
        @ManyToOne(targetEntity = FullUser::class)
        var datum: FullUser?
) : TepidDb()

class WtfTest : DbTest(){

    @Test
    fun testGetSession(){
        persistMultiple(testUsers)
        persistMultiple(testItems)

        val ri = hc.read(testItems[0]._id)

        assertNotNull(ri)

    }

    @Test
    fun testFk(){
        val embed0 = FullUser(shortUser = "shortUname")
        embed0._id = "TESTFU"
        val e0 = TestForeignKey(datum = embed0)
        e0._id = "TEST"

        em.transaction.begin()
        em.merge(embed0)
        em.merge(e0)
        em.transaction.commit()

        val r0 = em.find(TestForeignKey::class.java,"TEST")

        assertNotNull(r0)
        assertEquals(e0, r0)
    }

    @Test
    fun testSetGet(){
        val testContainer = TestContainingEntity(
                mutableSetOf(TestEntity("00"), TestEntity("01")),
                mutableSetOf(TestEntity1("10"), TestEntity1("11"))
        )
        testContainer._id = "TEST"

        em.transaction.begin()
//        testContainer.set0.forEach{em.persist(it)}
//        testContainer.set1.forEach{em.persist(it)}
        em.merge(testContainer)
        em.transaction.commit()

        val newEm = emf.createEntityManager()

        val r_find = newEm.find(TestContainingEntity::class.java,"TEST")
        val r_select = newEm.createQuery( "SELECT c from TestContainingEntity c where c._id = 'TEST'", TestContainingEntity::class.java).singleResult

        assertEquals(2, r_find!!.set0.size)
        assertEquals(2, r_find.set1.size)
        assertEquals(2, r_select!!.set0.size)
        assertEquals(2, r_select.set1.size)
    }

    @AfterEach
    fun truncateUsed(){
        val u = listOf(TestForeignKey::class.java, fs::class.java, FullUser::class.java)
        u.forEach { truncate(it) }
    }

    companion object {
        val testUsers = listOf(
                FullUser(shortUser = "USER1"),
                FullUser(shortUser = "USER2")
        )

        val testItems  = listOf(
                fs(user = testUsers[0], expiration = 100),
                fs(user = testUsers[1], expiration = 200),
                fs(user = testUsers[0], expiration = 300)
        )

        lateinit var hc: HibernateCrud<fs, String?>

        @JvmStatic
        @BeforeAll
        fun initHelper(){
            hc = HibernateCrud(DbTest.emf, fs::class.java)
        }
    }
}

open class DbTest {

    fun <C> persist (obj:C){
        em.transaction.begin()
        em.merge(obj)
        em.transaction.commit()
    }

    fun<T:TepidId> persistMultiple (list:List<T>){
        em.transaction.begin()
        list.toList().map { e ->
            em.detach(e)
//            e._id = e._id ?: newId()
            e._id = newId()
            em.merge(e)
        }
        em.transaction.commit()
    }

    internal fun newId() = UUID.randomUUID().toString()

    internal fun <T> truncate(classParameter: Class<T>){
        em.transaction.begin()
        em.flush()
        em.clear()
        em.createQuery("DELETE FROM ${classParameter.simpleName} e").executeUpdate()
        em.transaction.commit()
    }

    internal fun<T> deleteAllIndividually(classParameter: Class<T>){
        em.transaction.begin()
        val l : List<T> = em.createQuery("SELECT c FROM ${classParameter.simpleName} c", classParameter).resultList
        l.forEach {
            em.remove(it)
        }
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

    @AfterEach
    fun rollBackOnFailure(){
        if (em.transaction.isActive) em.transaction.rollback() // prevents transactions in failed state from persisting to other tests
    }

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
        em.merge(te)
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
        em.merge(te)
        em.transaction.commit()
        em.detach(te)
        em.clear()

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
        em.merge(te)
        em.transaction.commit()

        pc.delete(te)

        em.close()
        em = emf.createEntityManager()
        val re = em.find(TestEntity::class.java, te._id)
        assertNull(re)
    }

    @Test
    fun testPsqlCrudDeleteById(){
        val te = TestEntity("TEST")
        te._id = "ID4"
        em.transaction.begin()
        em.merge(te)
        em.transaction.commit()

        pc.deleteById(te._id)

        em.close()
        em = emf.createEntityManager()
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

        em.close()
        em = emf.createEntityManager()
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
            pc = HibernateCrud(emf, TestEntity::class.java)
        }
    }
}

class HibernateMarqueeLayerTest : DbTest(){

    @Test
    fun testMultipleItems(){
        val testItems = listOf(MarqueeData("T1"),MarqueeData("T2"),MarqueeData("T3"))
        persistMultiple(testItems)

        em.close()
        em = emf.createEntityManager()
        val retrieved = hml.getMarquees()

        for (i in 0 .. testItems.size-1){
            assertEquals(testItems[i].title, retrieved[i].title)
            assertEquals(testItems[i].entry.toString(), retrieved[i].entry.toString())
        }
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
            hc = HibernateCrud(emf, MarqueeData::class.java)
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
            hc = HibernateCrud(emf, FullDestination::class.java)
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
        assertEquals(ti.queueName, ri.queueName)
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
            hc = HibernateCrud(emf, PrintJob::class.java)
            hl = HibernateJobLayer(hc)
        }
    }
}

class HibernateQueueLayerTest() : DbTest() {

    @Test
    fun testGetQueues(){
        persistMultiple(testItems)

        val ri = hl.getQueues()

        assertEquals(testItems.toString(), ri.toString())
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
        assertEquals(ti.sortedBy { it.name }.toString(), ri.sortedBy { it.name }.toString())
    }

    @Test
    fun testPutQueuesUpdate(){
        val ti = testItems.toList()
        ti.map { it._id = newId()}
        persistMultiple(ti)

        ti.map { it.loadBalancer = "PerfectlyBalanced" }

        val response = hl.putQueues(ti)

        val ri = hc.readAll()
        assertEquals(ti.sortedBy { it.name }.toString(), ri.sortedBy { it.name }.toString())
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
            hc = HibernateCrud(emf, PrintQueue::class.java)
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

    @Test
    fun testGetSessionNull(){
        persistMultiple(testUsers)
        persistMultiple(testItems)

        val ri = hl.getSessionOrNull("FAKEID")

        assertNull(ri)
    }

    @Test
    fun testGetSession(){
        persistMultiple(testUsers)
        persistMultiple(testItems)

        val ri = hl.hc.read(testItems[0]._id)

        assertNotNull(ri)

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
            hc = HibernateCrud(emf, FullSession::class.java)
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

    @Test
    fun testGetTotalPrintedCountNoJobs(){
        val otherUser = testItems[0].copy(shortUser = "OTHERUSER")
        otherUser._id = "uOTHERUSER"
        persist(otherUser)
        persistMultiple(testItems)
        persistMultiple(testPrints)

        val ri = hl.getTotalPrintedCount(otherUser.shortUser!!)

        assertEquals(0, ri)
    }

    @Test
    fun testGetUserById(){
        val otherUser = testItems[0].copy(studentId = 1337)
        otherUser._id = "TEST"
        persist(otherUser)

        val ri = hl.getUserOrNull(otherUser.studentId.toString()) ?: fail("User was not retrieved")

        assertEquals(ri.shortUser, testItems[0].shortUser)

    }

    @Test
    fun testGetCourses(){
        val u = testItems[0].copy()
        u._id = "u${u.shortUser}"
        val groups = mutableSetOf(
                AdGroup("Group0"),
                AdGroup("Group1"),
                AdGroup("Group2")
        )
        val courses = mutableSetOf(
                Course("course0", Season.SUMMER, 1337),
                Course("course1", Season.SUMMER, 1337),
                Course("course2", Season.SUMMER, 1337)
        )

        u.groups = groups
        u.courses = courses
        persist(u)
//        em.clear()

        val ri = hl.getUserOrNull(u.shortUser!!) ?: fail("Did not retieve user")


        assertEquals(3, ri.groups.size)
        assertEquals(3, ri.courses.size)
    }

    @AfterEach
    fun truncateUsed(){
        println("------End Test------")

        val u = listOf(PrintJob::class.java)
        u.forEach { truncate(it) }

        deleteAllIndividually(FullUser::class.java)
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
            hc = HibernateCrud(emf, FullUser::class.java)
            hl = HibernateUserLayer(hc)
            println("======Begin Tests======")
        }
    }
}
