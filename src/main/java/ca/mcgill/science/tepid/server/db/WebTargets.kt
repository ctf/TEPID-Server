package ca.mcgill.science.tepid.server.db

import javax.ws.rs.core.Response

val Response.isSuccessful: Boolean
    get() = status in 200 until 300
