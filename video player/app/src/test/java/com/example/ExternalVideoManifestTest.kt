package com.example

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Android only lists an app under "Open with" for a video if the manifest says so. Nothing in the app's own code
 * would notice if that declaration were deleted, so this test guards it.
 */
class ExternalVideoManifestTest {

    private val manifest: String = listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
        .map { File(it) }.first { it.exists() }.readText()

    private val block: String = run {
        val start = manifest.indexOf("""android:name=".ExternalVideoActivity"""")
        assertTrue("ExternalVideoActivity must be declared in the manifest", start >= 0)
        manifest.substring(start, manifest.indexOf("</activity>", start))
    }

    @Test
    fun isExportedSoOtherAppsCanOpenIt() {
        assertTrue(block.contains("""android:exported="true""""))
    }

    @Test
    fun offersItselfForVideoFilesFromOtherApps() {
        assertTrue("needs ACTION_VIEW", block.contains("android.intent.action.VIEW"))
        assertTrue("needs the DEFAULT category", block.contains("android.intent.category.DEFAULT"))
        assertTrue("needs video/*", block.contains("""android:mimeType="video/*""""))
        assertTrue("needs content:// links (Gallery, WhatsApp, Files)", block.contains("""android:scheme="content""""))
        assertTrue("needs file:// links", block.contains("""android:scheme="file""""))
    }
}
