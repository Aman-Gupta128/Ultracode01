package com.hundredhours.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Single source of truth, shared by the UI and the background service.
 * Every change is written to SharedPreferences immediately, so nothing is lost if the app is killed.
 */
object StudyStore {
    private const val PREFS = "study"
    private const val KEY_STATE = "state"
    private const val KEY_THEME = "theme"

    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(StudyState())
    private val _theme = MutableStateFlow(ThemeMode.SYSTEM)
    val state: StateFlow<StudyState> = _state.asStateFlow()
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _state.value = prefs.getString(KEY_STATE, null)?.let { runCatching { decode(it) }.getOrNull() } ?: StudyState()
        _theme.value = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, "SYSTEM")!!) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    @Synchronized
    fun update(transform: (StudyState) -> StudyState) {
        val next = transform(_state.value)
        if (next == _state.value) return
        _state.value = next
        prefs.edit().putString(KEY_STATE, encode(next)).apply()
    }

    fun setTheme(mode: ThemeMode) {
        _theme.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    private fun encode(s: StudyState): String = JSONObject().apply {
        s.weekStart?.let { put("weekStart", it) }
        put("goalMs", s.goalMs)
        s.runningSince?.let { put("runningSince", it) }
        put("sessions", JSONArray().apply {
            s.sessions.forEach { put(JSONArray().put(it.start).put(it.end)) }
        })
        put("adjustments", JSONArray().apply {
            s.adjustments.forEach { put(JSONArray().put(it.at).put(it.deltaMs)) }
        })
    }.toString()

    private fun decode(json: String): StudyState {
        val o = JSONObject(json)
        val sessions = o.optJSONArray("sessions") ?: JSONArray()
        val adjustments = o.optJSONArray("adjustments") ?: JSONArray()
        return StudyState(
            weekStart = if (o.has("weekStart")) o.getLong("weekStart") else null,
            goalMs = o.optLong("goalMs", DEFAULT_GOAL_HOURS * HOUR_MS),
            runningSince = if (o.has("runningSince")) o.getLong("runningSince") else null,
            sessions = (0 until sessions.length()).map {
                val a = sessions.getJSONArray(it); Session(a.getLong(0), a.getLong(1))
            },
            adjustments = (0 until adjustments.length()).map {
                val a = adjustments.getJSONArray(it); Adjustment(a.getLong(0), a.getLong(1))
            },
        )
    }
}
