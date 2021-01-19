import java.time.Instant

/**
 * Return the number of seconds since the Unix epoch
 */
fun currentUnixTime(): Int {
    return Instant.now().epochSecond.toInt()
}
