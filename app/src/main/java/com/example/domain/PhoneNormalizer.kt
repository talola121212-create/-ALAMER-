package com.example.domain

object PhoneNormalizer {

    /**
     * Converts Eastern Arabic (٠-٩) and Persian (۰-۹) digits to standard ASCII digits (0-9).
     */
    fun convertNumeralsToEnglish(input: String): String {
        val sb = StringBuilder()
        for (ch in input) {
            when (ch) {
                // Arabic-Indic digits
                '٠' -> sb.append('0')
                '١' -> sb.append('1')
                '٢' -> sb.append('2')
                '٣' -> sb.append('3')
                '٤' -> sb.append('4')
                '٥' -> sb.append('5')
                '٦' -> sb.append('6')
                '٧' -> sb.append('7')
                '٨' -> sb.append('8')
                '٩' -> sb.append('9')
                // Persian / Urdu digits
                '۰' -> sb.append('0')
                '۱' -> sb.append('1')
                '۲' -> sb.append('2')
                '۳' -> sb.append('3')
                '۴' -> sb.append('4')
                '۵' -> sb.append('5')
                '۶' -> sb.append('6')
                '۷' -> sb.append('7')
                '۸' -> sb.append('8')
                '۹' -> sb.append('9')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Cleans up spaces, hyphens, parentheses, and other punctuation.
     */
    fun cleanPunctuation(input: String): String {
        return input.replace("[\\s\\-\\(\\)\\.]".toRegex(), "")
    }

    /**
     * Normalizes an Iraqi phone number into canonical format: 07xxxxxxxxx (11 digits).
     *
     * Supported formats:
     * - 077xxxxxxxxx -> 077xxxxxxxxx
     * - +9647xxxxxxxxx -> 07xxxxxxxxx
     * - 009647xxxxxxxxx -> 07xxxxxxxxx
     * - 9647xxxxxxxxx -> 07xxxxxxxxx
     * - 7xxxxxxxxx (10 digits starting with 7) -> 07xxxxxxxxx
     */
    fun normalize(rawInput: String?): String {
        if (rawInput.isNullOrBlank()) return ""

        val englishDigits = convertNumeralsToEnglish(rawInput.trim())
        var cleaned = cleanPunctuation(englishDigits)

        // Remove international + prefix if present
        if (cleaned.startsWith("+")) {
            cleaned = cleaned.substring(1)
        }

        // Handle 00964 prefix
        if (cleaned.startsWith("00964")) {
            cleaned = cleaned.substring(5)
        } else if (cleaned.startsWith("964")) {
            cleaned = cleaned.substring(3)
        }

        // If it starts with 7 and has 10 digits (e.g. 7751234567), prepend '0'
        if (cleaned.startsWith("7") && cleaned.length == 10) {
            cleaned = "0$cleaned"
        }

        return cleaned
    }

    /**
     * Validates if the normalized phone number is a valid Iraqi mobile number.
     * Must be 11 digits, start with '07', and consist entirely of digits.
     */
    fun isValidIraqiMobile(phone: String?): Boolean {
        val normalized = normalize(phone)
        return normalized.matches("^07[3-9]\\d{8}$".toRegex()) ||
                (normalized.startsWith("07") && normalized.length == 11 && normalized.all { it.isDigit() })
    }

    /**
     * Formats an Iraqi mobile number for clean user display.
     * Example: 07751234567 -> 0775 123 4567
     */
    fun formatForDisplay(phone: String?): String {
        val norm = normalize(phone)
        return if (norm.length == 11 && norm.startsWith("07")) {
            "${norm.substring(0, 4)} ${norm.substring(4, 7)} ${norm.substring(7)}"
        } else {
            norm.ifBlank { "غير متوفر" }
        }
    }
}
