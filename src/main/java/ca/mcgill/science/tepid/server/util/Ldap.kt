package ca.mcgill.science.tepid.server.util

import `in`.waffl.q.Promise
import `in`.waffl.q.PromiseRejectionException
import `in`.waffl.q.Q
import ca.mcgill.science.tepid.ldap.LdapBase
import ca.mcgill.science.tepid.ldap.LdapHelperContract
import ca.mcgill.science.tepid.ldap.LdapHelperDelegate
import ca.mcgill.science.tepid.models.bindings.withDbData
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.utils.WithLogging
import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.*
import java.util.concurrent.ExecutionException
import javax.naming.NamingException
import javax.naming.directory.*

object Ldap : WithLogging(), LdapHelperContract by LdapHelperDelegate() {

    private val ldap = LdapBase()

    private val numRegex = Regex("[0-9]+")

    private val auth = Config.RESOURCE_USER to Config.RESOURCE_CREDENTIALS

    /**
     * Query extension that will also check from our database
     * [sam] may be the short user, long user, or student id
     */
    fun queryUser(sam: String?, pw: String?): FullUser? {
        if (!Config.LDAP_ENABLED || sam == null) return null
        @Suppress("NAME_SHADOWING")
        val sam = if (sam.matches(numRegex) && sam.length > 9) sam.substring(sam.length - 9) else sam
        log.trace("Querying user $sam")
        val dbDeferred = Q.defer<FullUser>()
        val ldapPromise = queryUser(sam, dbDeferred.promise, pw)
        var dbUser: FullUser? = null
        try {
            val dbCandidate = when {
                sam.contains(".") -> CouchDb.getViewRows<FullUser>("byLongUser") { query("key" to "\"$sam%40mail.mcgill.ca\"") }.firstOrNull()
                sam.matches(numRegex) -> CouchDb.getViewRows<FullUser>("byStudentId") { query("key" to sam) }.firstOrNull()
                else -> CouchDb.path("u$sam").getJson()
            }
            if (dbCandidate?._id != null)
                dbUser = dbCandidate
        } catch (ignored: InterruptedException) {
        } catch (ignored: ExecutionException) {
        }

        dbDeferred.resolve(dbUser)
        /*
         * todo rework this part
         * Why are we only calling ldap when the short user is supplied?
         * ldap works fine with long users as well
         * Why do we return null on a promise rejection? Why not return dbUser?
         */
        val result = if (dbUser == null || sam == dbUser.shortUser) {
            try {
                val ldapUser = if (dbUser == null) ldapPromise.result else ldapPromise.getResult(3000)
                ldapUser ?: dbUser
            } catch (pre: PromiseRejectionException) {
                log.error("Promise rejected", pre)
                null
            }
        } else {
            // sam cannot by queried? Return fallback user
            dbUser
        }
        return result
    }

    private fun queryUser(sam: String, dbPromise: Promise<FullUser>, pw: String?): Promise<FullUser> {
        val ldapDeferred = Q.defer<FullUser>()
        if (!Config.LDAP_ENABLED) {
            ldapDeferred.reject("LDAP disabled in source")
            return ldapDeferred.promise
        }
        val termIsId = sam.matches(numRegex)
        object : Thread("LDAP Query: " + sam) {
            override fun run() {
                try {
                    // term is either shortUser or longUser
                    val term = (if (termIsId) {
                        try {
                            dbPromise.getResult(5000)!!.shortUser
                        } catch (e1: Exception) {
                            null
                        }
                    } else sam) ?: return ldapDeferred.reject("Student id $sam not found")

                    /**
                     * There are two ways of querying
                     * 1. By user's supplied credentials, which allows for fetching the studentId
                     * 2. By our resource credentials, which does not have studentId
                     * If we have queried by owner, this information is complete and should be regarded that way
                     */
                    val (auth, queryByOwner) = if (pw != null) {
                        log.trace("Querying by owner $term")
                        term to pw to true
                    } else {
                        log.trace("Querying by resource")
                        Config.RESOURCE_USER to Config.RESOURCE_CREDENTIALS to false
                    }
                    val out = ldap.queryUser(term, auth)
                    out?.shortUser ?: return ldapDeferred.reject("Could not locate user; short user not found")

                    val dbUser: FullUser? = dbPromise.result

                    /**
                     * I've added the shortUser comparison just as a precaution
                     * so that we don't accidentally overwrite the wrong user data
                     * A successful ldap query should always return a valid short user,
                     * and a dbUser without a matching short user cannot be verified
                     */
                    if (dbUser != null && out.shortUser == dbUser.shortUser) {
                        out.withDbData(dbUser)
                        if (!queryByOwner) out.studentId = dbUser.studentId
                        out.preferredName = dbUser.preferredName
                        out.nick = dbUser.nick
                        out.colorPrinting = dbUser.colorPrinting
                        out.jobExpiration = dbUser.jobExpiration
                    }

                    out.salutation = if (out.nick == null)
                        if (!out.preferredName.isEmpty()) out.preferredName[out.preferredName.size - 1]
                        else out.givenName else out.nick
                    out.realName = "${out.givenName} ${out.lastName}"
                    if (!out.preferredName.isEmpty())
                        out.realName = out.preferredName.asReversed().joinToString(" ")
                    if (dbUser == null || dbUser != out) {
                        log.trace("Update db instance")
                        try {
                            val response = CouchDb.path("u${out.shortUser}").putJson(out)
                            if (response.isSuccessful) {
                                val responseObj = response.readEntity(ObjectNode::class.java)
                                val newRev = responseObj.get("_rev")?.asText()
                                if (newRev != null && newRev.length > 3) {
                                    out._rev = newRev
                                    log.trace("New rev for ${out.shortUser}: $newRev")
                                }
                            } else {
                                log.error("Response failed: $response")
                            }

                        } catch (e1: Exception) {
                            log.error("Could not put ${out.shortUser} into db", e1)
                        }
                    } else {
                        log.trace("Not updating dbUser; already matches ldap user")
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
        log.debug("Authenticating $sam against ldap")
        val user = queryUser(sam, pw)
        log.trace("Ldap query result for $sam: ${user?.longUser}")
        try {
            val auth = ldap.createAuthMap(user?.shortUser ?: "", pw)
            InitialDirContext(auth).close()
        } catch (e: Exception) {
            return null
        }
        log.debug("Authentication successful")
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
            log.info("Added {} to exchange students.", sam)
        } catch (e: NamingException) {
            log.info("Error adding {} to exchange students.", sam)
            e.printStackTrace()
        }

    }


}