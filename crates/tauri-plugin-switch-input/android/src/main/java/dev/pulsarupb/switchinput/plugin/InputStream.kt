package dev.pulsarupb.switchinput.plugin

import android.view.KeyEvent
import android.view.MotionEvent
import app.tauri.plugin.JSObject
import org.json.JSONArray

/** Host services a stream needs to push events and check for subscribers. */
interface StreamContext {
    val tag: String
    fun emitStreamEvent(stream: String, payload: JSObject)
    fun hasStreamListeners(stream: String): Boolean
}

/**
 * A single, independently configurable input stream.
 *
 * `streamName` is the event name used for `push` delivery, while `snapshotKey` is the
 * key used in `poll()` snapshots (they differ only for the button stream).
 */
interface InputStream {
    val streamName: String
    val snapshotKey: String

    fun settings(): StreamSettings
    fun configure(settings: StreamSettings)

    fun onKeyEvent(event: KeyEvent): Boolean = false
    fun onMotionEvent(event: MotionEvent): Boolean = false

    fun start() {}
    fun stop() {}
    fun reset() {}

    /** Write this stream's latest values under [snapshotKey] on [o]. */
    fun writeSnapshot(o: JSObject) {}

    /** Return and clear buffered events (for `poll({ drain: true })`). */
    fun drainEvents(): JSONArray? = null
}

/** Small bounded FIFO used for optional event draining. */
class EventBuffer(capacity: Int) {
    private var capacity = capacity.coerceAtLeast(1)
    private val items = ArrayDeque<JSObject>()

    fun configure(enabled: Boolean, size: Int) {
        capacity = size.coerceIn(1, 4096)
        if (!enabled) items.clear()
    }

    fun add(item: JSObject) {
        items.addLast(item)
        while (items.size > capacity) items.removeFirst()
    }

    fun drain(): JSONArray? {
        if (items.isEmpty()) return null
        val array = JSONArray()
        while (items.isNotEmpty()) array.put(items.removeFirst())
        return array
    }

    fun clear() = items.clear()
}
