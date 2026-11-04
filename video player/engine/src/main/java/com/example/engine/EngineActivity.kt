package com.example.engine

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The engine has no real screen: OffScroll does the work. This page only explains what the app is and lets
 * you update yt-dlp, which is what fixes downloads when a site changes.
 */
class EngineActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var core: EngineCore
    private lateinit var status: TextView
    private lateinit var update: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        core = EngineCore(applicationContext)

        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
        fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(56), dp(24), dp(32))
        }
        column.addView(text("OffScroll Engine", 26f, Color.WHITE, bold = true))
        column.addView(text(
            "This add-on lets OffScroll save videos from YouTube, Facebook, TikTok, X, Reddit and about a " +
                "thousand other sites. You don't open it: just use the \"Any link\" tab in OffScroll.",
            14f, 0xFFCBD5E1.toInt()
        ).apply { setPadding(0, dp(12), 0, dp(20)) })

        status = text("Engine version: checking…", 14f, 0xFF94A3B8.toInt())
        column.addView(status)

        update = Button(this).apply {
            text = "Update download engine"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xFFF59E0B.toInt(), 0xFFEC4899.toInt(), 0xFF8B5CF6.toInt())
            ).apply { cornerRadius = dp(16).toFloat() }
            setOnClickListener { runUpdate() }
        }
        column.addView(update, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(20) })

        column.addView(text(
            "Sites change their pages often. If a download stops working, update the engine here, " +
                "or tap \"Update engine\" inside OffScroll.",
            12f, 0xFF64748B.toInt()
        ).apply { setPadding(0, dp(14), 0, 0) })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(0xFF020617.toInt())
            addView(column)
        })
        showVersion()
    }

    private fun showVersion() {
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    core.prepare()
                    "Engine version: ${core.version()}"
                } catch (e: Exception) {
                    e.message ?: "The engine couldn't start on this phone."
                }
            }
            status.text = text
        }
    }

    private fun runUpdate() {
        update.isEnabled = false
        status.text = "Updating…"
        scope.launch {
            val note = withContext(Dispatchers.IO) {
                try {
                    core.update()
                } catch (e: Exception) {
                    e.message ?: "Couldn't update the engine."
                }
            }
            status.text = note
            update.isEnabled = true
            update.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
