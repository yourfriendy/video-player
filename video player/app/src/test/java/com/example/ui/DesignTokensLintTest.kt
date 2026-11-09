package com.example.ui

import com.example.ui.theme.Ds
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the design system from drifting again: screens may only use the text sizes and corner radii defined in
 * [Ds]. If this fails, pick the nearest step on the scale (or, if a new step is truly needed, add it to Ds first).
 */
class DesignTokensLintTest {

    private val root: File = listOf("src/main/java", "app/src/main/java").map { File(it) }.first { it.exists() }

    private fun kotlinFiles() = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun everyFontSizeIsOnTheScale() {
        val bad = mutableListOf<String>()
        kotlinFiles().forEach { file ->
            file.readLines().forEachIndexed { i, line ->
                Regex("""fontSize = ([0-9.]+)\.sp""").findAll(line).forEach { m ->
                    val size = m.groupValues[1].toFloat()
                    if (size !in Ds.FontScaleSp) bad += "${file.name}:${i + 1} fontSize ${m.groupValues[1]}"
                }
            }
        }
        assertTrue("Off-scale font sizes (allowed: ${Ds.FontScaleSp}):\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun everyCornerRadiusIsOnTheScale() {
        val bad = mutableListOf<String>()
        kotlinFiles().forEach { file ->
            file.readLines().forEachIndexed { i, line ->
                Regex("""RoundedCornerShape\((\d+)\.dp\)""").findAll(line).forEach { m ->
                    if (m.groupValues[1].toInt() !in Ds.RadiusScaleDp) bad += "${file.name}:${i + 1} radius ${m.groupValues[1]}"
                }
                Regex("""(?:topStart|topEnd|bottomStart|bottomEnd) = (\d+)\.dp""").findAll(line).forEach { m ->
                    if (m.groupValues[1].toInt() !in Ds.RadiusScaleDp) bad += "${file.name}:${i + 1} radius ${m.groupValues[1]}"
                }
            }
        }
        assertTrue("Off-scale corner radii (allowed: ${Ds.RadiusScaleDp}):\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun noLaunchTimeBlockingPermissionGate() {
        // Phase 1.1: the app must open without the video permission
        val main = File(root, "com/example/MainActivity.kt").readText()
        assertTrue("MainActivity must not gate the app behind the permission screen", !main.contains("needsPermissionScreen"))
        assertTrue("No permission prompt at launch", !main.contains("askForNotificationsOnce"))
    }
}
