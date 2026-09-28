package com.nekroz.meowmacro.macro

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException

private const val TAG = "MacroRepo"

/** Stores all macros as a single JSON file in the app's private storage. */
class MacroRepo(context: Context) {

    private val file = AtomicFile(File(context.filesDir, FILE_NAME))

    // Keeps concurrent saves in call order so an older snapshot never overwrites a newer one.
    private val mutex = Mutex()

    /** Returns the saved macros, or an empty list if none were saved or the file is unreadable. */
    suspend fun load(): List<Macro> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                parseMacros(JSONArray(file.readFully().decodeToString()))
            } catch (_: FileNotFoundException) {
                emptyList()
            } catch (e: JSONException) {
                Log.e(TAG, "Discarding unreadable macros", e)
                emptyList()
            }
        }
    }

    /** Replaces the saved macros with [macros]. Finishes even if the caller is cancelled. */
    suspend fun save(macros: List<Macro>) = withContext(NonCancellable + Dispatchers.IO) {
        mutex.withLock {
            val bytes = macrosToJson(macros).toString().encodeToByteArray()
            val out = file.startWrite()
            try {
                out.write(bytes)
                file.finishWrite(out)
            } catch (e: Exception) {
                file.failWrite(out)
                throw e
            }
        }
    }

    private companion object {
        const val FILE_NAME = "macros.json"
    }
}

// JSON format:
// [{"name": "…", "events": [
//     {"type": "wait", "duration": 120},
//     {"type": "tap", "x": 1.0, "y": 2.0, "duration": 80},
//     {"type": "swipe", "points": [x0, y0, x1, y1, …], "duration": 300}]}]

private fun macrosToJson(macros: List<Macro>) = JSONArray().apply {
    for (macro in macros) {
        put(JSONObject().apply {
            put("name", macro.name)
            put("events", JSONArray().apply { macro.macro.forEach { put(eventToJson(it)) } })
        })
    }
}

private fun eventToJson(event: MacroEvent) = JSONObject().apply {
    when (event) {
        is MacroEvent.Wait -> {
            put("type", "wait")
            put("duration", event.durationMillis)
        }
        is MacroEvent.Tap -> {
            put("type", "tap")
            put("x", event.position.x.toDouble())
            put("y", event.position.y.toDouble())
            put("duration", event.durationMillis)
        }
        is MacroEvent.Swipe -> {
            put("type", "swipe")
            put("points", JSONArray().apply {
                event.points.forEach {
                    put(it.x.toDouble())
                    put(it.y.toDouble())
                }
            })
            put("duration", event.durationMillis)
        }
    }
}

private fun parseMacros(json: JSONArray): List<Macro> = List(json.length()) { i ->
    val obj = json.getJSONObject(i)
    val events = obj.getJSONArray("events")
    Macro(
        name = obj.getString("name"),
        macro = List(events.length()) { parseEvent(events.getJSONObject(it)) }
    )
}

private fun parseEvent(obj: JSONObject): MacroEvent {
    val duration = obj.getLong("duration")
    return when (val type = obj.getString("type")) {
        "wait" -> MacroEvent.Wait(duration)
        "tap" -> MacroEvent.Tap(
            Offset(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat()),
            duration
        )
        "swipe" -> {
            val coords = obj.getJSONArray("points")
            val points = List(coords.length() / 2) {
                Offset(coords.getDouble(it * 2).toFloat(), coords.getDouble(it * 2 + 1).toFloat())
            }
            MacroEvent.Swipe(points, duration)
        }
        else -> throw JSONException("Unknown event type: $type")
    }
}
