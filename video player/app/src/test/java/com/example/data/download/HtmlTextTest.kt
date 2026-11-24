package com.example.data.download

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlTextTest {

    private fun meta(content: String) = """<meta property="og:title" content="$content" />"""

    @Test
    fun hexEmojiAndJapaneseFromARealSharePopup() {
        // JIT🌷 on Instagram: "#TVアニメ『ONE PIECE』"
        val html = meta("JIT&#x1f337; on Instagram: &quot;#TV&#x30a2;&#x30cb;&#x30e1;&#x300e;ONE PIECE&#x300f;&quot;")
        assertEquals(
            "JIT\uD83C\uDF37 on Instagram: \"#TV\u30A2\u30CB\u30E1\u300EONE PIECE\u300F\"",
            InstagramParser.extractTitle(html)
        )
    }

    @Test
    fun symbolsAndEmojiAroundANameAreDecoded() {
        // ✴ 🎀 VAISHU 🎀 ✴ on Instagram
        val html = meta("&#x2734; &#x1f380; VAISHU &#x1f380; &#x2734; on Instagram: &quot;Sunday (22-11-26")
        assertEquals(
            "\u2734 \uD83C\uDF80 VAISHU \uD83C\uDF80 \u2734 on Instagram: \"Sunday (22-11-26",
            InstagramParser.extractTitle(html)
        )
    }

    @Test
    fun decimalAndNamedCodesAreDecoded() {
        assertEquals("\uD83D\uDE00", HtmlText.decode("&#128512;"))
        assertEquals("Tom & Jerry\u2026 it\u2019s", HtmlText.decode("Tom &amp; Jerry&hellip; it&rsquo;s"))
        assertEquals("a b", HtmlText.decode("a&nbsp;b"))
        assertEquals("<b>", HtmlText.decode("&lt;b&gt;"))
    }

    @Test
    fun textEncodedTwiceIsDecodedFully() {
        assertEquals("\uD83C\uDF37", HtmlText.decode("&amp;#x1f337;"))
    }

    @Test
    fun notRealCharactersAndUnknownNamesAreLeftAlone() {
        assertEquals("&#xD800;", HtmlText.decode("&#xD800;"))
        assertEquals("&#x110000;", HtmlText.decode("&#x110000;"))
        assertEquals("&#0;", HtmlText.decode("&#0;"))
        assertEquals("&nosuchname;", HtmlText.decode("&nosuchname;"))
        assertEquals("fish & chips", HtmlText.decode("fish & chips"))
    }

    @Test
    fun cuttingToLengthNeverSplitsAnEmoji() {
        val s = "a".repeat(79) + "\uD83C\uDF37" + "tail"
        assertEquals("a".repeat(79), HtmlText.take(s, 80))
        assertEquals("a".repeat(80), HtmlText.take("a".repeat(100), 80))
        assertEquals("short", HtmlText.take("short", 80))
    }

    @Test
    fun longTitleWithEmojiNearTheLimitKeepsWholeCharacters() {
        val html = meta("a".repeat(79) + "&#x1f337;" + "b".repeat(10))
        val t = InstagramParser.extractTitle(html)!!
        assertEquals(79, t.length)
    }
}
