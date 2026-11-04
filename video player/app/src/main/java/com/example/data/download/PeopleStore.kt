package com.example.data.download

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Someone the user wants quick access to (and optionally alerts for). */
data class SavedPerson(
    val username: String,
    val alerts: Boolean = false,
    /** Story ids already seen/announced, so only NEW stories trigger an alert. */
    val seenIds: Set<String> = emptySet(),
    /** False until the first background check has recorded what was already there (no alert for old stories). */
    val baselined: Boolean = false,
    /** Last known profile photo and display name, so saved people show their DP without opening them first. */
    val picUrl: String? = null,
    val fullName: String = ""
)

/** Saved people, kept on the phone only. Shared by the app screens and the background alert worker. */
class PeopleStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("saved_people", Context.MODE_PRIVATE)

    @Synchronized
    fun all(): List<SavedPerson> = try {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("u").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val seen = o.optJSONArray("seen")
            SavedPerson(
                username = name,
                alerts = o.optBoolean("alerts", false),
                seenIds = if (seen == null) emptySet() else (0 until seen.length()).map { seen.optString(it) }.toSet(),
                baselined = o.optBoolean("baselined", false),
                picUrl = o.optString("pic").takeIf { it.startsWith("http") },
                fullName = o.optString("name")
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    @Synchronized
    fun saveAll(list: List<SavedPerson>) {
        val array = JSONArray()
        list.forEach { p ->
            array.put(
                JSONObject()
                    .put("u", p.username)
                    .put("alerts", p.alerts)
                    .put("baselined", p.baselined)
                    .put("pic", p.picUrl ?: "")
                    .put("name", p.fullName)
                    .put("seen", JSONArray(p.seenIds.toList().takeLast(200)))
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    @Synchronized
    fun update(username: String, transform: (SavedPerson) -> SavedPerson) {
        saveAll(all().map { if (it.username.equals(username, ignoreCase = true)) transform(it) else it })
    }

    private companion object {
        const val KEY = "people"
    }
}
