package com.example.data.universal.engine

import com.example.data.universal.FormatInfo
import com.example.data.universal.MediaInfo
import com.example.data.universal.Platforms
import com.example.data.universal.QualityOptions
import org.json.JSONObject

/** Turns the engine's lookup answer (JSON) into the [MediaInfo] the screens show. Plain Kotlin + org.json, so it is unit tested. */
object EngineInfoParser {

    fun parse(url: String, json: JSONObject): MediaInfo {
        val array = json.optJSONArray("formats")
        val formats = (0 until (array?.length() ?: 0))
            .mapNotNull { array?.optJSONObject(it) }
            .map { format(it) }
            .ifEmpty {
                // Sites that serve one single file report it at the top level instead
                listOf(
                    FormatInfo(
                        formatId = json.optString("formatId"),
                        width = json.optInt("width"),
                        height = json.optInt("height"),
                        fps = 0,
                        vcodec = null,
                        acodec = null,
                        ext = text(json, "ext"),
                        bitrateKbps = 0,
                        sizeBytes = size(json)
                    )
                )
            }
        val title = listOf(json.optString("title"), json.optString("fulltitle"))
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() } ?: "Video"
        val duration = json.optInt("duration")
        return MediaInfo(
            pageUrl = url,
            title = title,
            uploader = text(json, "uploader"),
            thumbnail = text(json, "thumbnail"),
            durationSec = duration,
            platform = Platforms.nameFor(text(json, "extractorKey") ?: text(json, "extractor"), url),
            options = QualityOptions.build(formats, duration)
        )
    }

    private fun format(f: JSONObject) = FormatInfo(
        formatId = f.optString("formatId"),
        width = f.optInt("width"),
        height = f.optInt("height"),
        fps = f.optInt("fps"),
        vcodec = text(f, "vcodec"),
        acodec = text(f, "acodec"),
        ext = text(f, "ext"),
        bitrateKbps = f.optInt("tbr"),
        sizeBytes = size(f),
        note = text(f, "formatNote")
    )

    private fun size(o: JSONObject): Long {
        val exact = o.optLong("fileSize")
        return if (exact > 0) exact else o.optLong("fileSizeApproximate")
    }

    /** JSON null and "" both mean "not given". */
    private fun text(o: JSONObject, key: String): String? =
        if (o.isNull(key)) null else o.optString(key).takeIf { it.isNotBlank() }
}
