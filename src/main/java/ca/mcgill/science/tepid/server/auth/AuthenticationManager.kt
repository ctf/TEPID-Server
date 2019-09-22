package ca.mcgill.science.tepid.server.auth

import ca.mcgill.science.tepid.models.bindings.withDbData
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.Sam
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.utils.WithLogging

object AuthenticationManager : WithLogging() {

    /**
     * Authenticates user against LDAP (if enabled)
     *
     * @param sam short user
     * @param pw password
     * @return authenticated user, or null if auth failure
     */
    fun authenticate(sam: Sam, pw: String): FullUser? {
        val dbUser = queryUserDb(sam)
        log.trace("Db data found for $sam")

        // >>
        log.debug("Authenticating against ldap {\"sam\":\"$sam\"}")

        val shortUser = (
            if (sam.matches(LdapHelper.shortUserRegex)) sam
            else queryUser(sam)?.shortUser
            ?: AutoSuggest.queryLdap(sam, 1).getOrNull(0)?.shortUser)
            ?: return null
        // <<

        val ldapUser = Ldap.authenticate(shortUser, pw)
        if (ldapUser != null) {
            val mergedUser = mergeUsers(ldapUser, dbUser)
            DB.putUser(mergedUser)
            return mergedUser
        }
        return null
    }

    /**
     * Retrieve user from DB if available, otherwise retrieves from LDAP
     *
     * @param sam short user
     * @param pw password
     * @return user if found
     */
    fun queryUser(sam: Sam): FullUser? {
        log.trace("Querying user: {\"sam\":\"$sam\"}")

        val dbUser = queryUserDb(sam)

        if (dbUser != null) return dbUser

        if (!sam.matches(LdapHelper.shortUserRegex)) return null // cannot query without short user
        val ldapUser = Ldap.queryUserWithResourceAccount(sam) ?: return null

        DB.putUser(ldapUser)

        log.trace("Found user from ldap {\"sam\":\"$sam\", \"longUser\":\"${ldapUser.longUser}\"}")
        return ldapUser
    }

    /**
     * Merge users from LDAP and DB for their corresponding authorities
     * Returns a new users (does not mutate either input
     */
    fun mergeUsers(ldapUser: FullUser, dbUser: FullUser?): FullUser {
        // ensure that short users actually match before attempting any merge
        val ldapShortUser = ldapUser.shortUser
            ?: throw RuntimeException("LDAP user does not have a short user. Maybe this will help {\"ldapUser\":\"$ldapUser,\"dbUser\":\"$dbUser\"}")
        if (dbUser == null) return ldapUser
        if (ldapShortUser != dbUser.shortUser) throw RuntimeException("Attempt to merge to different users {\"ldapUser\":\"$ldapUser,\"dbUser\":\"$dbUser\"}")
        // proceed with data merge
        val newUser = ldapUser.copy()
        newUser.withDbData(dbUser)
        newUser.studentId = if (ldapUser.studentId != -1) ldapUser.studentId else dbUser.studentId
        newUser.preferredName = dbUser.preferredName
        newUser.nick = dbUser.nick
        newUser.colorPrinting = dbUser.colorPrinting
        newUser.jobExpiration = dbUser.jobExpiration
        newUser.updateUserNameInformation()
        return newUser
    }

    /**
     * Retrieve a [FullUser] directly from the database when supplied with either a
     * short user, long user, or student id
     */
    fun queryUserDb(sam: Sam): FullUser? {
        val dbUser = DB.getUserOrNull(sam)
        dbUser?._id ?: return null
        log.trace("Found db user {\"sam\":\"$sam\",\"db_id\":\"${dbUser._id}\", \"dislayName\":\"${dbUser.displayName}\"}")
        return dbUser
    }

    fun refreshUser(sam: Sam): FullUser {
        val dbUser = queryUserDb(sam)
        if (dbUser == null) {
            log.info("Could not fetch user from DB {\"sam\":\"$sam\"}")
            return queryUser(sam)
                ?: throw RuntimeException("Could not fetch user from anywhere {\"sam\":\"$sam\"}")
        }
        val ldapUser = Ldap.queryUserWithResourceAccount(sam)
            ?: throw RuntimeException("Could not fetch user from LDAP {\"sam\":\"$sam\"}")
        val refreshedUser = mergeUsers(ldapUser, dbUser)
        if (dbUser.role != refreshedUser.role) {
            SessionManager.invalidateSessions(sam)
        }
        DB.putUser(refreshedUser)
        return refreshedUser
    }


}