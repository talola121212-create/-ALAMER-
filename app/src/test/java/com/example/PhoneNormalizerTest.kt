package com.example

import com.example.domain.PhoneNormalizer
import org.junit.Assert.*
import org.junit.Test

class PhoneNormalizerTest {

    @Test
    fun testStandardIraqiNumber() {
        val input = "07751234567"
        val normalized = PhoneNormalizer.normalize(input)
        assertEquals("07751234567", normalized)
        assertTrue(PhoneNormalizer.isValidIraqiMobile(normalized))
    }

    @Test
    fun testInternationalPlusFormat() {
        val input = "+9647751234567"
        val normalized = PhoneNormalizer.normalize(input)
        assertEquals("07751234567", normalized)
        assertTrue(PhoneNormalizer.isValidIraqiMobile(normalized))
    }

    @Test
    fun testInternationalDoubleZeroFormat() {
        val input = "009647801234567"
        val normalized = PhoneNormalizer.normalize(input)
        assertEquals("07801234567", normalized)
        assertTrue(PhoneNormalizer.isValidIraqiMobile(normalized))
    }

    @Test
    fun testWithoutLeadingZeroOrCode() {
        val input = "7751234567"
        val normalized = PhoneNormalizer.normalize(input)
        assertEquals("07751234567", normalized)
        assertTrue(PhoneNormalizer.isValidIraqiMobile(normalized))
    }

    @Test
    fun testArabicIndicNumeralsConversion() {
        // ٠٧٧٥١٢٣٤٥٦٧
        val input = "٠٧٧٥١٢٣٤٥٦٧"
        val normalized = PhoneNormalizer.normalize(input)
        assertEquals("07751234567", normalized)
        assertTrue(PhoneNormalizer.isValidIraqiMobile(normalized))
    }

    @Test
    fun testPersianNumeralsConversion() {
        // ۰۷۸۰۱۲۳۴۵۶۷
        val input = "۰۷۸۰۱۲۳۴۵۶۷"
        val normalized = PhoneNormalizer.normalize(input)
        assertEquals("07801234567", normalized)
        assertTrue(PhoneNormalizer.isValidIraqiMobile(normalized))
    }

    @Test
    fun testSpacesHyphensAndParentheses() {
        val input = "+964 (0775) 123-4567"
        val normalized = PhoneNormalizer.normalize(input)
        assertEquals("07751234567", normalized)
        assertTrue(PhoneNormalizer.isValidIraqiMobile(normalized))
    }

    @Test
    fun testFormatForDisplay() {
        val input = "07751234567"
        val display = PhoneNormalizer.formatForDisplay(input)
        assertEquals("0775 123 4567", display)
    }

    @Test
    fun testInvalidNumbers() {
        assertFalse(PhoneNormalizer.isValidIraqiMobile("12345"))
        assertFalse(PhoneNormalizer.isValidIraqiMobile("0775123"))
        assertFalse(PhoneNormalizer.isValidIraqiMobile("01234567890"))
    }
}
