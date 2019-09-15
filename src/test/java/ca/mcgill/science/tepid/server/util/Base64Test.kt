package ca.mcgill.science.tepid.server.util

import ca.mcgill.science.tepid.models.internal.Base64
import org.junit.Test
import kotlin.test.assertEquals

class Base64Test {

    private val encoders: List<(String) -> String> = listOf<(String) -> String>(
        { Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE) },
        { java.util.Base64.getEncoder().encodeToString(it.toByteArray()) },
        { org.glassfish.jersey.internal.util.Base64.encodeAsString(it) }
    )

    private val decoders: List<(String) -> String> = listOf<(String) -> ByteArray>(
        { Base64.decode(it, Base64.NO_WRAP or Base64.URL_SAFE) },
        { java.util.Base64.getDecoder().decode(it) },
        { org.glassfish.jersey.internal.util.Base64.decode(it.toByteArray()) }
    ).map { decoder -> { key: String -> String(decoder(key)) } }

    /**
     * Namely just to make sure that everything is properly compatible
     */
    @Test
    fun encodeDecode() {
        val key = "afba0aw3asfas f0(*)kf3wor*(*"

        val encoded = encoders.map { it(key) }
        val firstEncoded = encoded.first()
        encoded.forEachIndexed { index, s ->
            assertEquals(firstEncoded, s, "Encoding mismatch at $index")
        }
        println("Encoded to $firstEncoded")

        val decoded = decoders.map { it(firstEncoded) }
        val firstDecoded = decoded.first()
        decoded.forEachIndexed { index, s ->
            assertEquals(firstDecoded, s, "Decoding mismatch at $index")
        }
        println("Decoded back to $firstDecoded")
    }
}