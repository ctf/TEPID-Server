package ca.mcgill.science.tepid.server.auth

import ca.mcgill.science.tepid.models.bindings.withDbData
import ca.mcgill.science.tepid.models.data.FullUser
import ca.mcgill.science.tepid.models.data.PersonalIdentifier
import ca.mcgill.science.tepid.models.data.ShortUser
import ca.mcgill.science.tepid.server.db.DB
import ca.mcgill.science.tepid.server.util.logMessage
import org.apache.logging.log4j.kotlin.Logging

interface IAuthenticationManager {
    /**
     * Authenticates user against LDAP (if enabled)
     *
     * @param identifier identifier
     * @param pw password
     * @return authenticated user, or null if auth failure
     */
    fun authenticate(identifier: PersonalIdentifier, pw: String): FullUser?

    /**
     * Retrieve user from DB if available, otherwise retrieves from LDAP
     *
     * @param identifier identifier
     * @param pw password
     * @return user if found
     */
    fun queryUser(identifier: PersonalIdentifier): FullUser?

    /**
     * Refreshes a user with their attributes from LDAP
     */

    fun refreshUser(shortUser: ShortUser): FullUser

    /**
     * Adds all currently eligible users to the DB
     */
    fun addAllCurrentlyEligible()
}

object AuthenticationManager : Logging, IAuthenticationManager {

    /**
     * Authenticates user against LDAP (if enabled)
     *
     * @param identifier identifier
     * @param pw password
     * @return authenticated user, or null if auth failure
     */
    override fun authenticate(identifier: PersonalIdentifier, pw: String): FullUser? {
        val dbUser = queryUserDb(identifier)
        logger.trace { logMessage("db data found", "identifier" to identifier) }

        logger.debug { logMessage("authenticating against ldap", "identifier" to identifier) }

        val shortUser = (
                if (identifier.matches(LdapHelper.shortUserRegex)) identifier
                else queryUser(identifier)?.shortUser
                )
                ?: return null

        val ldapUser = Ldap.authenticate(shortUser, pw) ?: return null
        val mergedUser = mergeUsers(ldapUser, dbUser)
        DB.users.put(mergedUser)
        return mergedUser
    }

    /**
     * Retrieve user from DB if available, otherwise retrieves from LDAP
     *
     * @param identifier identifier
     * @param pw password
     * @return user if found
     */
    override fun queryUser(identifier: PersonalIdentifier): FullUser? {
        logger.trace { logMessage("querying user", "identifier" to identifier) }

        val dbUser = queryUserDb(identifier)

        if (dbUser != null) return dbUser

        val ldapUser = queryUserLdap(identifier) ?: return null

        DB.users.put(ldapUser)

        logger.trace { logMessage("found user from ldap", "identifier" to identifier, "longUser" to ldapUser.longUser) }
        return ldapUser
    }

    /**
     * Retrieve a [FullUser] from ldap
     * [identifier] must be a valid short user or long user
     * The resource account will be used as auth
     */
    fun queryUserLdap(identifier: PersonalIdentifier): FullUser? {
        logger.trace { logMessage("querying user from LDAP", "identifier" to identifier, "by" to "resource") }

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
        if (dbUser == null) return ldapUser
        // ensure that short users actually match before attempting any merge
        val ldapShortUser = ldapUser.shortUser
                ?: throw RuntimeException(logMessage("LDAP user does not have a short user. Maybe this will help", "ldapUser" to ldapUser, "dbUser" to dbUser))
        if (ldapShortUser != dbUser.shortUser) throw RuntimeException(logMessage("attempt to merge to different users", "ldapUser" to ldapUser, "dbUser" to dbUser))
        // proceed with data merge
        val newUser = ldapUser.copy(
            studentId = if (ldapUser.studentId != -1) ldapUser.studentId else dbUser.studentId,
            preferredName = dbUser.preferredName,
            nick = dbUser.nick,
            colorPrinting = dbUser.colorPrinting,
            jobExpiration = dbUser.jobExpiration,
            semesters = dbUser.semesters.plus(ldapUser.semesters)
        )
        newUser.withDbData(dbUser)
        newUser.updateUserNameInformation()
        return newUser
    }

    /**
     * Retrieve a [FullUser] directly from the database when supplied with either a
     * short user, long user, or student id
     */
    fun queryUserDb(identifier: PersonalIdentifier): FullUser? {
        val dbUser = DB.users.getUserOrNull(identifier)
        dbUser?._id ?: return null
        logger.trace { logMessage("found db user", "identifier" to identifier, "db_id" to dbUser._id, "dislayName" to dbUser.displayName) }
        return dbUser
    }

    override fun refreshUser(shortUser: ShortUser): FullUser {
        val dbUser = queryUser(shortUser)
                ?: throw RuntimeException(logMessage("could not fetch user from anywhere", "shortUser" to shortUser))
        val ldapUser = queryUserLdap(shortUser)
                ?: throw RuntimeException(logMessage("could not fetch user from LDAP", "shortUser" to shortUser))
        val refreshedUser = mergeUsers(ldapUser, dbUser)
        if (dbUser.role != refreshedUser.role) {
            SessionManager.invalidateSessions(shortUser)
        }
        DB.users.put(refreshedUser)
        return refreshedUser
    }

    override fun addAllCurrentlyEligible() {
        val ldap = Ldap.getAllCurrentlyEligible().mapNotNull { p -> p.shortUser?.let { Pair<String, FullUser>(it, p) } }.toMap()
        val fromDb = DB.users.getAllIfPresent(ldap.keys).mapNotNull { p -> p.shortUser?.let { Pair<String, FullUser>(it, p) } }.toMap()

        val merged = ldap.mapNotNull { mergeUsers(it.value, fromDb[it.key]) }
        DB.users.putUsers(merged)
    }
}