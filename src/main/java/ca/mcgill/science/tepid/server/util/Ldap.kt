package ca.mcgill.science.tepid.server.util

import ca.mcgill.science.tepid.models.data.User
import ca.mcgill.science.tepid.models.data.ViewResultSet
import com.fasterxml.jackson.databind.node.ObjectNode
import `in`.waffl.q.Promise
import `in`.waffl.q.PromiseRejectionException
import `in`.waffl.q.Q
import org.glassfish.jersey.jackson.JacksonFeature
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.naming.NamingEnumeration
import javax.naming.NamingException
import javax.naming.directory.*
import javax.naming.ldap.InitialLdapContext
import javax.naming.ldap.LdapContext
import javax.ws.rs.client.Client
import javax.ws.rs.client.ClientBuilder
import javax.ws.rs.client.Entity
import javax.ws.rs.client.WebTarget
import javax.ws.rs.core.*
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.regex.Matcher
import java.util.regex.Pattern

import javax.naming.Context.*

internal object Ldap : WithLogging() {

    fun queryUser(sam: String?, pw: String?): User? {
        if (!Config.LDAP_ENABLED || sam == null || pw == null) return null
        var sam = sam
        if (sam.matches("[0-9]+".toRegex()) && sam.length > 9) sam = sam.substring(sam.length - 9)
        val dbDeferred = Q.defer<User>()
        val ldapPromise = queryUser(sam, dbDeferred.promise, pw)
        var dbUser: User? = null
        try {
            if (sam.contains(".")) {
                val results = couchdb.path("_design").path("main").path("_view").path("byLongUser").queryParam("key", "\"" + sam + "%40mail.mcgill.ca\"").request(MediaType.APPLICATION_JSON).async().get(LdapUserResultSet::class.java).get()
                if (!results.rows.isEmpty()) dbUser = results.rows.get(0).value
            } else if (sam.matches("[0-9]+".toRegex())) {
                val results = couchdb.path("_design").path("main").path("_view").path("byStudentId").queryParam("key", sam).request(MediaType.APPLICATION_JSON).async().get(LdapUserResultSet::class.java).get()
                if (!results.rows.isEmpty()) dbUser = results.rows.get(0).value
            } else {
                dbUser = couchdb.path("u" + sam).request(MediaType.APPLICATION_JSON).async().get(User::class.java).get()
            }
        } catch (ignored: InterruptedException) {
        } catch (ignored: ExecutionException) {
        }

        dbDeferred.resolve(dbUser)
        return if (dbUser == null || sam == dbUser.shortUser) {
            try {
                val ldapUser = if (dbUser == null) ldapPromise.result else ldapPromise.getResult(3000)
                ldapUser ?: dbUser
            } catch (pre: PromiseRejectionException) {
                null
            }

        } else {
            dbUser
        }

    }

    private class LdapUserResultSet : ViewResultSet<String, User>()

    /**
     * Creates a blank user and attempts to retrieve as many attributes
     * as possible from the specified attributes
     */
    private fun Attributes.toUser(ctx: LdapContext): User {
        fun attr(name: String) = get(name)?.get()?.toString() ?: ""
        val out = User(
                displayName = attr("displayName"),
                givenName = attr("givenName"),
                lastName = attr("sn"),
                shortUser = attr("sAMAccountName"),
                longUser = attr("userPrincipalName").toLowerCase(),
                email = attr("mail"),
                middleName = attr("middleName"),
                faculty = attr("department")
        )
        try {
            out.activeSince = SimpleDateFormat("yyyyMMddHHmmss.SX").parse(attr("whenCreated"))
        } catch (e: ParseException) {

        }

        val memberOf = get("memberOf")
        val groups = if (memberOf != null) {
            (0 until memberOf.size()).map { memberOf.get(it).toString() }.mapNotNull {
                try {
                    ctx.getAttributes(it, arrayOf("CN")).get("CN").get().toString()
                } catch (e: NamingException) {
                    null
                }
            }.sorted()
        } else emptyList()
        out.groups = groups
        return out
    }

    /**
     * Copy over attributes from another user
     */
    private fun User.mergeFrom(other: User?) {
        other ?: return
        _id = other._id
        _rev = other._rev
        studentId = other.studentId
        preferredName = other.preferredName
        nick = nick ?: other.nick
        colorPrinting = other.colorPrinting
        jobExpiration = other.jobExpiration
        shortUser = shortUser ?: other.shortUser
    }

    fun queryUser(sam: String, dbPromise: Promise<User>, pw: String?): Promise<User> {
        val ldapDeferred = Q.defer<User>()
        if (!Config.LDAP_ENABLED) {
            ldapDeferred.reject("LDAP disabled in source")
            return ldapDeferred.promise
        }
        val longUser = sam.contains(".")
        val termIsId = sam.matches("[0-9]+".toRegex())
        object : Thread("LDAP Query: " + sam) {
            override fun run() {
                try {
                    var term: String? = sam
                    if (termIsId) {
                        try {
                            term = dbPromise.getResult(5000)!!.shortUser
                        } catch (e1: Exception) {
                            ldapDeferred.reject("Student id not found")
                            return
                        }

                    }
                    val ldapSearchBase = ***REMOVED***
                    val searchFilter = "(&(objectClass=user)(" + (if (longUser) "userPrincipalName" else "sAMAccountName") + "=" + term + (if (longUser) "@mail.mcgill.ca" else "") + "))"
                    //					System.out.println(searchFilter);
                    var ctx: LdapContext? = null
                    while (ctx == null) { //TODO limit the loop, otherwise infinite loop occurs and testers don't know what's happening
                        try {
                            val env = Hashtable<String, Any>()
                            env.put(SECURITY_AUTHENTICATION, "simple")
                            env.put(PROVIDER_URL, ***REMOVED***)
                            env.put(SECURITY_PRINCIPAL, "***REMOVED***\\***REMOVED***")
                            env.put(SECURITY_CREDENTIALS, Config.RESOURCE_CREDENTIALS)
                            env.put(INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory")
                            env.put("com.sun.jndi.ldap.read.timeout", "5000")
                            env.put("com.sun.jndi.ldap.connect.timeout", "500")
                            ctx = InitialLdapContext(env, null)
                        } catch (e: Exception) {
                            System.err.println("Failed to bind to LDAP, trying again")
                        }
                    }
                    val searchControls = SearchControls()
                    searchControls.searchScope = SearchControls.SUBTREE_SCOPE
                    val results = ctx.search(ldapSearchBase, searchFilter, searchControls)
                    val searchResult = results.nextElement()
                    results.close()
                    val out: User = searchResult?.attributes?.toUser(ctx) ?: User()
                    ctx.close()
                    // todo should we overwrite ldap values so easily?
                    // if anything they should take precedence
                    out.mergeFrom(dbPromise.result)
                    val dbUser = dbPromise.result

                    out.mergeFrom(dbUser)
//                    if (pw != null && out != null && out.longUser != null) {
//                        val idRequest = Ldap.getIdData(out.longUser, pw)
//                        val idInfo = idRequest.getResult(15000)
//                        if (idInfo != null) {
//                            try {
//                                if (idInfo.containsKey("id"))
//                                    out.studentId = Integer.valueOf(idInfo["id"].toString())
//                            } catch (e: Exception) {
//                            }
//
//                            if (idInfo.containsKey("preferredName"))
//                                out.preferredName = idInfo["preferredName"] as List<String>
//                        }
//                    }
                    out.shortUser ?: return ldapDeferred.reject("Could not locate user")
                    out.salutation = if (out.nick == null) if (!out.preferredName.isEmpty()) out.preferredName[out.preferredName.size - 1] else out.givenName else out.nick
                    out.realName = out.givenName + " " + out.lastName
                    if (!out.preferredName.isEmpty()) {
                        out.realName = out.preferredName.asReversed().joinToString(" ")
                    }
                    if (dbUser == null || dbUser != out) {
                        try { //TODO rewrite to avoid null pointers
                            val request = couchdb.path("u${out.shortUser}")
                            if (dbUser != null) out._rev = dbUser._rev
                            val result = request.request(MediaType.APPLICATION_JSON).put(Entity.entity(out, MediaType.APPLICATION_JSON)).readEntity(ObjectNode::class.java)
                            val newRev = if (result.get("_rev") == null) null else result.get("_rev").asText()
                            if (newRev != null && newRev.length > 3)
                                out._rev = newRev
                            else
                                System.err.println(result)
                        } catch (e1: Exception) {
                            e1.printStackTrace()
                        }

                    }
                    //					System.out.println(out);
                    ldapDeferred.resolve(out)
                } catch (e: NamingException) {
                    ldapDeferred.reject("Could not query user", e)
                }

            }
        }.start()
        return ldapDeferred.promise
    }


    fun getIdData(upn: String?, pw: String?): Promise<Map<String, String>> {
        val q = Q.defer<Map<String, String>>()
        object : Thread("Student Id Fetch") {
            override fun run() {
                val client = ClientBuilder.newBuilder().register(JacksonFeature::class.java).build()
                val session = client.target("https://horizon.mcgill.ca/pban1/twbkwbis.P_GenMenu")
                val login = client.target("https://horizon.mcgill.ca/pban1/twbkwbis.P_ValLogin")
                val id = client.target("https://horizon.mcgill.ca/pban1/bzsktran.P_Display_Form")
                var cookies = session.queryParam("name", "bmenu.P_MainMnu").request().get().cookies
                var bigIpServerIsr: Cookie? = null
                var oraWxSession: Cookie? = null
                var testId: Cookie? = null
                for (c in cookies.values) {
                    if (c.name.startsWith("BIGipServer~ISR")) {
                        bigIpServerIsr = c.toCookie()
                    } else if (c.name.startsWith("ORA_WX_SESSION")) {
                        oraWxSession = c.toCookie()
                    } else if (c.name.startsWith("TESTID")) {
                        testId = c.toCookie()
                    }
                }
                val postData = MultivaluedHashMap<String, String>()
                postData.add("sid", upn)
                postData.add("PIN", pw)
                cookies = login.request().cookie(bigIpServerIsr).cookie(oraWxSession).cookie(testId).post(Entity.entity<MultivaluedMap<String, String>>(postData, MediaType.APPLICATION_FORM_URLENCODED)).cookies
                val sessId = cookies["SESSID"]?.toCookie()
                val response = id.queryParam("user_type", "S").queryParam("tran_type", "V").request().cookie(oraWxSession).cookie(sessId).get(String::class.java)
                val matcher = Pattern.compile("<TD CLASS=\"delabel\" scope=\"row\" ><SPAN class=\"fieldmediumtextbold\">([^:]+):</SPAN></TD>\\s+<TD CLASS=\"dedefault\"><SPAN class=\"fieldmediumtext\">([^<]+)</SPAN></TD>").matcher(response)
                val out = HashMap<String, String>()
                while (matcher.find()) {
                    var k = matcher.group(1)
                    var v: String? = matcher.group(2)
                    if (k.startsWith("Student Name with Preferred")) {
                        k = "preferredName"
//                        if (v!!.toString().contains(", ")) v = Arrays.asList(*v.toString().split(", ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
                    }
                    if (k.startsWith("McGill ID")) k = "id"
                    //does not record permanent code
                    if (!k.startsWith("Permanent Code") && v != null) out.put(k, v)
                }
                q.resolve(out)
            }
        }.start()
        return q.promise
    }


    fun autoSuggest(like: String, limit: Int): Promise<List<User>> {
        val q = Q.defer<List<User>>()
        if (!Config.LDAP_ENABLED) {
            q.reject("LDAP disabled in source")
            return q.promise
        }
        object : Thread("LDAP AutoSuggest: " + like) {
            override fun run() {
                try {
                    val ldapSearchBase = ***REMOVED***
                    val searchFilter = "(&(objectClass=user)(|(userPrincipalName=$like*)(samaccountname=$like*)))"
                    //					System.out.println(searchFilter);
                    val ctx = bindLdap() ?: return q.resolve(emptyList())
                    val searchControls = SearchControls()
                    searchControls.searchScope = SearchControls.SUBTREE_SCOPE
                    val results = ctx.search(ldapSearchBase, searchFilter, searchControls)
                    val out = mutableListOf<User>()
                    var res = 0
                    val iter = results.iterator()
                    while (iter.hasNext() && res++ < limit) {
                        val user = iter.next().attributes.toUser(ctx)
                        if (user.longUser?.split("@")?.getOrNull(0)?.indexOf(".") ?: -1 > 0)
                            out.add(user)
                    }
                    results.close()
                    ctx.close()
                    q.resolve(out)
                } catch (ne: NamingException) {
                    q.reject("Could not get autosuggest", ne)
                }
            }
        }.start()
        return q.promise
    }

    private fun createAuthMap(user: String, password: String, withTimeout: Boolean) = Hashtable<String, String>().apply {
        put(SECURITY_AUTHENTICATION, "simple")
        put(INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory")
        put(PROVIDER_URL, ***REMOVED***)
        put(SECURITY_PRINCIPAL, "***REMOVED***\\$user")
        put(SECURITY_CREDENTIALS, password)
        if (withTimeout) {
            put("com.sun.jndi.ldap.read.timeout", "5000")
            put("com.sun.jndi.ldap.connect.timeout", "500")
        }
    }

    private fun bindLdap(): LdapContext? {
        if (!Config.LDAP_ENABLED) {
            System.err.println("LDAP not enabled; cannot bind")
            return null
        }
        var retries = 15
        while (retries-- > 0) {
            try {
                val auth = createAuthMap("***REMOVED***", Config.RESOURCE_CREDENTIALS, true)
                return InitialLdapContext(auth, null)
            } catch (e: Exception) {
                System.err.println("Failed to bind to LDAP")
            }
        }
        return null
    }

    fun authenticate(sam: String, pw: String): User? {
        if (!Config.LDAP_ENABLED) return null
        val user = Ldap.queryUser(sam, pw)
        try {
            val auth = createAuthMap(user?.shortUser ?: "", pw, false)
            InitialDirContext(auth).close()
        } catch (e: Exception) {
            return null
        }
        return user
    }

    fun setExchangeStudent(sam: String, exchange: Boolean) {
        val longUser = sam.contains(".")
        val ldapSearchBase = ***REMOVED***
        val searchFilter = "(&(objectClass=user)(" + (if (longUser) "userPrincipalName" else "sAMAccountName") + "=" + sam + (if (longUser) "@mail.mcgill.ca" else "") + "))"
        val ctx = bindLdap() ?: return
        val searchControls = SearchControls()
        searchControls.searchScope = SearchControls.SUBTREE_SCOPE
        var searchResult: SearchResult? = null
        try {
            val results = ctx.search(ldapSearchBase, searchFilter, searchControls)
            searchResult = results.nextElement()
            results.close()
        } catch (e: Exception) {
        }

        if (searchResult == null) return
        val cal = Calendar.getInstance()
        val userDn = searchResult.nameInNamespace
        val groupDn = "CN=***REMOVED***" + cal.get(Calendar.YEAR) + (if (cal.get(Calendar.MONTH) < 8) "W" else "F") + ",***REMOVED***,***REMOVED***,OU=***REMOVED***,***REMOVED***,***REMOVED***,***REMOVED***"
        val mods = arrayOfNulls<ModificationItem>(1)
        val mod = BasicAttribute("member", userDn)
        mods[0] = ModificationItem(if (exchange) DirContext.ADD_ATTRIBUTE else DirContext.REMOVE_ATTRIBUTE, mod)
        try {
            ctx.modifyAttributes(groupDn, mods)
            log.info("Added {} to exchange students.", sam)
        } catch (e: NamingException) {
            log.info("Error adding {} to exchange students.", sam)
            e.printStackTrace()
        }

    }

}
