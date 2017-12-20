package ca.mcgill.science.tepid.server.util

import `in`.waffl.q.Promise
import `in`.waffl.q.PromiseRejectionException
import `in`.waffl.q.Q
import ca.mcgill.science.tepid.ldap.LdapBase
import ca.mcgill.science.tepid.ldap.LdapHelperContract
import ca.mcgill.science.tepid.ldap.LdapHelperDelegate
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.ViewResultMap
import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.*
import java.util.concurrent.ExecutionException
import javax.naming.NamingException
import javax.naming.directory.*
import javax.ws.rs.client.Entity
import javax.ws.rs.core.MediaType

object Ldap : LdapHelperContract by LdapHelperDelegate() {

    private val ldap = LdapBase()

    private class LdapUserResultSet : ViewResultMap<String, FullUser>()

    private val numRegex = Regex("[0-9]+")

    private val auth = Config.RESOURCE_USER to Config.RESOURCE_CREDENTIALS

    @JvmStatic
    fun queryUser(sam: String?, pw: String?): FullUser? {
        if (!Config.LDAP_ENABLED || sam == null || pw == null) return null
        val sam = if (sam.matches(numRegex) && sam.length > 9) sam.substring(sam.length - 9) else sam

        val dbDeferred = Q.defer<FullUser>()
        val ldapPromise = queryUser(sam, dbDeferred.promise, pw)
        var dbUser: FullUser? = null
        try {
            val target = if (sam.contains("."))
                CouchDb.path(CouchDb.MAIN_VIEW, "byLongUser").query("key" to "\"$sam%40mail.mcgill.ca\"")
            else if (sam.matches(numRegex))
                CouchDb.path(CouchDb.MAIN_VIEW, "byStudentId").query("key" to sam)
            else null


            //todo check why we use async
//            val results:List<FullUser> = if (sam.contains("."))
//                CouchDb.getViewRows("byLongUser") { query("key" to "\"$sam%40mail.mcgill.ca\"") }
//            else
//                CouchDb.getViewRows("byStudentId") { query("key" to sam) }

            if (target != null) {
                val results = target.request(MediaType.APPLICATION_JSON).async().get(LdapUserResultSet::class.java).get()
                if (!results.rows.isEmpty()) dbUser = results.rows[0].value
            }

            if (dbUser == null)
                dbUser = CouchDb.path("u$sam").request(MediaType.APPLICATION_JSON).async().get(FullUser::class.java).get()

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

    private fun queryUser(sam: String, dbPromise: Promise<FullUser>, pw: String): Promise<FullUser> {
        val ldapDeferred = Q.defer<FullUser>()
        if (!Config.LDAP_ENABLED) {
            ldapDeferred.reject("LDAP disabled in source")
            return ldapDeferred.promise
        }
        val termIsId = sam.matches(numRegex)
        object : Thread("LDAP Query: " + sam) {
            override fun run() {
                try {

                    val term = if (termIsId) {
                        try {
                            dbPromise.getResult(5000)!!.shortUser
                        } catch (e1: Exception) {
                            null
                        }
                    } else sam

                    if (term == null) {
                        ldapDeferred.reject("Student id $sam not found")
                        return
                    }

                    val out = ldap.queryUser(term, term to pw) ?: FullUser()

                    val dbUser = dbPromise.result

                    out.mergeWith(dbUser)

                    out.shortUser ?: return ldapDeferred.reject("Could not locate user")
                    out.salutation = if (out.nick == null)
                        if (!out.preferredName.isEmpty()) out.preferredName[out.preferredName.size - 1]
                        else out.givenName else out.nick
                    out.realName = out.givenName + " " + out.lastName
                    if (!out.preferredName.isEmpty()) {
                        out.realName = out.preferredName.asReversed().joinToString(" ")
                    }
                    if (dbUser == null || dbUser != out) {
                        try { //TODO rewrite to avoid null pointers
                            val result = CouchDb.path("u${out.shortUser}").request(MediaType.APPLICATION_JSON)
                                    .put(Entity.entity(out, MediaType.APPLICATION_JSON)).readEntity(ObjectNode::class.java)
                            val newRev = if (result.get("_rev") == null) null else result.get("_rev").asText()
                            if (newRev != null && newRev.length > 3)
                                out._rev = newRev
                            else
                                System.err.println(result)
                        } catch (e1: Exception) {
                            e1.printStackTrace()
                        }

                    }
                    ldapDeferred.resolve(out)
                } catch (e: NamingException) {
                    ldapDeferred.reject("Could not query user", e)
                }

            }
        }.start()
        return ldapDeferred.promise
    }

    @JvmStatic
    fun autoSuggest(like: String, limit: Int): Promise<List<FullUser>> {
        val q = Q.defer<List<FullUser>>()
        if (!Config.LDAP_ENABLED) {
            q.reject("LDAP disabled in source")
            return q.promise
        }
        object : Thread("LDAP AutoSuggest: " + like) {
            override fun run() {
                try {
                    val out = ldap.autoSuggest(like, auth, limit)
                    q.resolve(out)
                } catch (ne: NamingException) {
                    q.reject("Could not get autosuggest", ne)
                }
            }
        }.start()
        return q.promise
    }

    fun authenticate(sam: String, pw: String): FullUser? {
        if (!Config.LDAP_ENABLED) return null
        val user = Ldap.queryUser(sam, pw)
        try {
            val auth = ldap.createAuthMap(user?.shortUser ?: "", pw)
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
        val ctx = ldap.bindLdap(auth) ?: return
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
        val year = cal.get(Calendar.YEAR)
        val season = if (cal.get(Calendar.MONTH) < 8) "W" else "F"
        val groupDn = "CN=***REMOVED***$year$season,***REMOVED***,***REMOVED***,OU=***REMOVED***,***REMOVED***,***REMOVED***,***REMOVED***"
        val mods = arrayOfNulls<ModificationItem>(1)
        val mod = BasicAttribute("member", userDn)
        mods[0] = ModificationItem(if (exchange) DirContext.ADD_ATTRIBUTE else DirContext.REMOVE_ATTRIBUTE, mod)
        try {
            ctx.modifyAttributes(groupDn, mods)
            ldap.log.info("Added {} to exchange students.", sam)
        } catch (e: NamingException) {
            ldap.log.info("Error adding {} to exchange students.", sam)
            e.printStackTrace()
        }

    }


}