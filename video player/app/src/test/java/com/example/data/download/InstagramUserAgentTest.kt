package com.example.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class InstagramUserAgentTest {

    @Test
    fun stripsBothWebViewMarkers() {
        val webView = "Mozilla/5.0 (Linux; Android 13; CPH2451 Build/TP1A.220905.001; wv) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/119.0.6045.193 " +
            "Mobile Safari/537.36"
        val cleaned = cleanWebUserAgent(webView)
        assertFalse(cleaned.contains("wv"))
        assertFalse(cleaned.contains("Version/"))
        assertEquals(
            "Mozilla/5.0 (Linux; Android 13; CPH2451 Build/TP1A.220905.001) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.6045.193 Mobile Safari/537.36",
            cleaned
        )
    }

    @Test
    fun leavesAPlainChromeAgentUntouched() {
        val chrome = "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Mobile Safari/537.36"
        assertEquals(chrome, cleanWebUserAgent(chrome))
    }
}
