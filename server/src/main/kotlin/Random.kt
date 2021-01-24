import java.security.SecureRandom
import java.util.Base64

object Random {
    private val generator = SecureRandom()
    private val encoder = Base64.getEncoder()

    /**
     * Return the specified number of random bytes, Base64-encoded
     */
    fun getEncodedRandomBytes(numBytes: Int = 16): String {
        val bytes = ByteArray(numBytes)
        generator.nextBytes(bytes)
        return encoder.encodeToString(bytes)
    }
}
