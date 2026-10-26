package com.dailytalk.app

import java.security.SecureRandom

/**
 * Daily Talk numbers: 10 digits, first digit 1-9, assigned by the app at sign-up and never changed.
 * They are NOT telephone numbers and cannot be used to call or text outside this app.
 */
object ChatNumber {
    private val pattern = Regex("^[1-9][0-9]{9}$")
    private val secure = SecureRandom()

    /** [nextByte] is injectable for tests. Rejection sampling (250 is a multiple of 10) avoids bias. */
    fun generate(nextByte: () -> Int = { secure.nextInt(256) }): String {
        val sb = StringBuilder()
        while (sb.length < 10) {
            val b = nextByte()
            if (b >= 250) continue
            val d = b % 10
            if (sb.isEmpty() && d == 0) continue
            sb.append(d)
        }
        return sb.toString()
    }

    /** Accepts "12345 67890", "12345-67890" and similar. Returns the 10 digits, or null. */
    fun normalize(raw: String): String? = raw.replace(Regex("[\\s\\-.()]"), "").takeIf { pattern.matches(it) }

    fun format(n: String): String = if (pattern.matches(n)) n.substring(0, 5) + " " + n.substring(5) else n
}
