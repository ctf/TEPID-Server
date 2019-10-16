package ca.mcgill.science.tepid.server.auth

import ca.mcgill.science.tepid.models.bindings.withDbData
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.PersonalIdentifier
import ca.mcgill.science.tepid.models.data.ShortUser
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.utils.WithLogging

object AuthenticationManager : WithLogging() {

    /**
     * Authenticates user against LDAP (if enabled)
     *
     * @param identifier identifier
     * @param pw password
     * @return authenticated user, or null if auth failure
     */
    fun authenticate(identifier: PersonalIdentifier, pw: String): FullUser? {
        val dbUser = queryUserDb(identifier)
        log.trace("Db data found for $identifier")

        log.debug("Authenticating against ldap {\"identifier\":\"$identifier\"}")

        val shortUser = (
            if (identifier.matches(LdapHelper.shortUserRegex)) identifier
            else queryUser(identifier)?.shortUser
            )
            ?: return null

        val ldapUser = Ldap.authenticate(shortUser, pw) ?: return null
        val mergedUser = mergeUsers(ldapUser, dbUser)
        DB.putUser(mergedUser)
        return mergedUser
    }

    /**
     * Retrieve user from DB if available, otherwise retrieves from LDAP
     *
     * @param identifier identifier
     * @param pw password
     * @return user if found
     */
    fun queryUser(identifier: PersonalIdentifier): FullUser? {
        log.trace("Querying user: {\"identifier\":\"$identifier\"}")

        val dbUser = queryUserDb(identifier)

        if (dbUser != null) return dbUser

        val ldapUser = queryUserLdap(identifier) ?: return null

        DB.putUser(ldapUser)

        log.trace("Found user from ldap {\"identifier\":\"$identifier\", \"longUser\":\"${ldapUser.longUser}\"}")
        return ldapUser
    }

    /**
     * Retrieve a [FullUser] from ldap
     * [identifier] must be a valid short user or long user
     * The resource account will be used as auth
     */
    fun queryUserLdap(identifier: PersonalIdentifier): FullUser? {
        log.trace("Querying user from LDAP {\"identifier\":\"$identifier\", \"by\":\"resource\"}")

        return when {
            identifier.contains("@") -> Ldap.queryByEmail(identifier)
            identifier.contains(".") -> Ldap.queryByLongUser(identifier)
            else -> Ldap.queryByShortUser(identifier)
        }
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
    fun queryUserDb(identifier: PersonalIdentifier): FullUser? {
        val dbUser = DB.getUserOrNull(identifier)
        dbUser?._id ?: return null
        log.trace("Found db user {\"identifier\":\"$identifier\",\"db_id\":\"${dbUser._id}\", \"dislayName\":\"${dbUser.displayName}\"}")
        return dbUser
    }

    fun refreshUser(shortUser: ShortUser): FullUser {
        val dbUser = queryUser(shortUser) ?: throw RuntimeException("Could not fetch user from anywhere {\"shortUser\":\"$shortUser\"}")
        val ldapUser = queryUserLdap(shortUser)
            ?: throw RuntimeException("Could not fetch user from LDAP {\"shortUser\":\"$shortUser\"}")
        val refreshedUser = mergeUsers(ldapUser, dbUser)
        if (dbUser.role != refreshedUser.role) {
            SessionManager.invalidateSessions(shortUser)
        }
        DB.putUser(refreshedUser)
        return refreshedUser
    }
}