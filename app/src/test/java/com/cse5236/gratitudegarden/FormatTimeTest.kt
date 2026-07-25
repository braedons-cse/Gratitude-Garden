package com.cse5236.gratitudegarden

import com.cse5236.gratitudegarden.util.formatTime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * Non-UI unit test #3 — [formatTime].
 *
 * Covers the 12-hour / AM-PM conversion boundaries and pins down the locale fix:
 * the string must always use ASCII digits, even when the JVM's default locale uses
 * a non-Latin number system. The second test fails against the original
 * locale-default implementation and passes after switching to `Locale.US`.
 */
class FormatTimeTest {

    // Non-UI unit test #3 (primary): the tricky boundaries — midnight, noon, single-
    // and double-digit hours, and zero-padded minutes.
    @Test
    fun formatsTwelveHourBoundaries() {
        assertEquals("12:00 AM", formatTime(0, 0))    // midnight → 12 AM, not 0
        assertEquals("12:00 PM", formatTime(12, 0))   // noon → 12 PM, not 0
        assertEquals("8:00 PM", formatTime(20, 0))    // afternoon → subtract 12
        assertEquals("9:05 AM", formatTime(9, 5))     // minute is zero-padded
        assertEquals("11:59 PM", formatTime(23, 59))  // end of day
        assertEquals("1:00 AM", formatTime(1, 0))     // early morning, single digit
    }

    // The output is UI chrome and must stay ASCII regardless of the device locale.
    // Under a number system like Arabic-Indic, the old locale-default formatter would
    // have produced "٨:٠٠ PM"; the Locale.US fix keeps it "8:00 PM".
    @Test
    fun usesAsciiDigitsUnderNonLatinLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG-u-nu-arab"))
            assertEquals("8:00 PM", formatTime(20, 0))
            assertEquals("12:05 AM", formatTime(0, 5))
        } finally {
            Locale.setDefault(original)
        }
    }
}
