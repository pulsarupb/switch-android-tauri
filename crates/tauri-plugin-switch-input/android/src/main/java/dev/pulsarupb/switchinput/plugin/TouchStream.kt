package dev.pulsarupb.switchinput.plugin

import android.view.MotionEvent
import android.util.Log
import app.tauri.plugin.JSObject
import org.json.JSONArray

/**
 * Multi-touch pointers. Touch is always observed, never consumed, so the WebView keeps
 * receiving its own touch events.
 */
class TouchStream(private val ctx: StreamContext) : InputStream {
    override val streamName = "touch"
    override val snapshotKey = "touch"

    private var settings = StreamDefaults.touch()
    private val buffer = EventBuffer(settings.bufferSize)
    private var lastEmit = 0L

    /** Active pointers keyed by pointer id. */
    private val pointers = LinkedHashMap<Int, JSObject>()
    private var latestPointers = JSONArray()

    override fun settings() = settings

    override fun configure(settings: StreamSettings) {
        this.settings = settings
        buffer.configure(settings.bufferEvents, settings.bufferSize)
        if (settings.mode == StreamMode.OFF) {
            pointers.clear()
            latestPointers = JSONArray()
        }
    }

    override fun onMotionEvent(event: MotionEvent): Boolean {
        if (settings.mode == StreamMode.OFF) return false

        Log.v(ctx.tag, "touch action=${event.actionMasked} pointers=${event.pointerCount}")
        applyPointers(event)
        latestPointers = snapshotPointers()

        val now = System.currentTimeMillis()
        val payload = JSObject()
        payload.put("type", "touch")
        payload.put("timestamp", now)
        payload.put("action", event.actionMasked)
        payload.put("actionIndex", event.actionIndex)
        payload.put("pointerCount", event.pointerCount)
        payload.put("pointers", eventPointers(event))
        payload.put("deviceId", event.deviceId)
        payload.put("source", event.source)

        if (settings.bufferEvents) buffer.add(payload)

        if (settings.mode == StreamMode.PUSH &&
            now - lastEmit >= settings.rateMs &&
            ctx.hasStreamListeners(streamName)
        ) {
            lastEmit = now
            ctx.emitStreamEvent(streamName, payload)
        }
        return false
    }

    override fun writeSnapshot(o: JSObject) {
        val snapshot = JSObject()
        snapshot.put("pointers", latestPointers)
        o.put(snapshotKey, snapshot)
    }

    override fun drainEvents() = buffer.drain()

    override fun reset() {
        pointers.clear()
        latestPointers = JSONArray()
        buffer.clear()
    }

    private fun applyPointers(event: MotionEvent) {
        val lifted = if (event.actionIndex in 0 until event.pointerCount) {
            event.getPointerId(event.actionIndex)
        } else {
            null
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_MOVE -> {
                pointers.clear()
                for (i in 0 until event.pointerCount) {
                    pointers[event.getPointerId(i)] = pointer(event, i)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (lifted != null) pointers.remove(lifted)
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    if (id != lifted) pointers[id] = pointer(event, i)
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> pointers.clear()
        }
    }

    private fun snapshotPointers(): JSONArray {
        val array = JSONArray()
        for (p in pointers.values) array.put(p)
        return array
    }

    private fun eventPointers(event: MotionEvent): JSONArray {
        val array = JSONArray()
        for (i in 0 until event.pointerCount) array.put(pointer(event, i))
        return array
    }

    private fun pointer(event: MotionEvent, index: Int): JSObject {
        val p = JSObject()
        p.put("id", event.getPointerId(index))
        p.put("x", event.getX(index).toDouble())
        p.put("y", event.getY(index).toDouble())
        p.put("pressure", event.getPressure(index).toDouble())
        p.put("size", event.getSize(index).toDouble())
        p.put("touchMajor", event.getTouchMajor(index).toDouble())
        p.put("touchMinor", event.getTouchMinor(index).toDouble())
        p.put("toolMajor", event.getToolMajor(index).toDouble())
        p.put("toolMinor", event.getToolMinor(index).toDouble())
        return p
    }
}
