package com.example.data.download

/**
 * Turns the codes Instagram puts in its page text back into the characters people see: `&#x1f337;` becomes the
 * tulip emoji, `&#128512;` a smiley, `&amp;` an ampersand, `&hellip;` an ellipsis. Without this the share pop-up
 * showed the raw codes, which looks like a secret language.
 */
internal object HtmlText {

    private val ENTITY = Regex("&(#[xX][0-9a-fA-F]{1,6}|#[0-9]{1,7}|[A-Za-z][A-Za-z0-9]{1,31});")

    private val NAMED = mapOf(
        "amp" to "&", "quot" to "\"", "apos" to "'", "lt" to "<", "gt" to ">",
        "nbsp" to " ", "hellip" to "\u2026", "ndash" to "\u2013", "mdash" to "\u2014",
        "lsquo" to "\u2018", "rsquo" to "\u2019", "ldquo" to "\u201C", "rdquo" to "\u201D",
        "bull" to "\u2022", "middot" to "\u00B7", "copy" to "\u00A9", "reg" to "\u00AE", "trade" to "\u2122",
        "laquo" to "\u00AB", "raquo" to "\u00BB", "euro" to "\u20AC", "deg" to "\u00B0", "times" to "\u00D7",
        "hearts" to "\u2665"
    )

    /**
     * Decodes numeric (decimal and hex) and common named entities. Text that was encoded twice
     * (`&amp;#x1f337;`) is decoded again, at most three times. A code that is not a real character
     * (for example `&#xD800;`) is left as it was.
     */
    fun decode(text: String): String {
        var current = text
        repeat(3) {
            if (!current.contains('&')) return current
            val next = ENTITY.replace(current) { m -> decodeOne(m.groupValues[1]) ?: m.value }
            if (next == current) return current
            current = next
        }
        return current
    }

    private fun decodeOne(body: String): String? {
        if (body.startsWith("#")) {
            val isHex = body.length > 1 && (body[1] == 'x' || body[1] == 'X')
            val parsed: Int? = if (isHex) body.substring(2).toIntOrNull(16) else body.substring(1).toIntOrNull()
            val code = parsed ?: return null
            val valid = code in 1..0x10FFFF && code !in 0xD800..0xDFFF
            return if (valid) String(Character.toChars(code)) else null
        }
        return NAMED[body] ?: NAMED[body.lowercase()]
    }

    /** The first [max] characters, never cutting an emoji (a two-part character) in half. */
    fun take(text: String, max: Int): String {
        if (text.length <= max) return text
        var end = max
        if (end > 0 && Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) end--
        return text.substring(0, end)
    }
}
