package dev.pulsarupb.switchinput.plugin

import android.view.KeyEvent
import android.util.Log
import app.tauri.plugin.JSObject
import org.json.JSONArray
import java.util.Collections

/**
 * Face buttons, shoulders, triggers, d-pad and stick clicks.
 *
 * Discrete edges are buffered by default so consumers using `poll({ drain: true })`
 * can catch every press/release without subscribing to push events.
 */
class ButtonStream(private val ctx: StreamContext) : InputStream {
    override val streamName = "button"
    override val snapshotKey = "buttons"

    private var settings = StreamDefaults.buttons()
    private val pressed: MutableSet<String> =
        Collections.synchronizedSet(LinkedHashSet<String>())
    private val buffer = EventBuffer(settings.bufferSize)
    private var lastEmit = 0L

    override fun settings() = settings

    override fun configure(settings: StreamSettings) {
        this.settings = settings
        buffer.configure(settings.bufferEvents, settings.bufferSize)
        if (settings.mode == StreamMode.OFF) pressed.clear()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val gamepad = KeyMap.isGamepadSource(event.source) ||
            KeyMap.isGamepadSource(event.device?.sources ?: 0)
        if (!gamepad) return false
        // Consume gamepad keys even when the stream is off so the WebView never
        // receives them; we just don't record or emit anything.
        if (settings.mode == StreamMode.OFF) return true

        val name = KeyMap.buttonName(event) ?: return true
        val isDown = event.action == KeyEvent.ACTION_DOWN
        if (isDown) pressed.add(name) else pressed.remove(name)

        Log.i(
            ctx.tag,
            "button $name ${if (isDown) "down" else "up"} " +
                "scan=${event.scanCode} key=${event.keyCode} src=${event.source} " +
                "device=${event.device?.name}"
        )

        val payload = JSObject()
        payload.put("type", "button")
        payload.put("timestamp", System.currentTimeMillis())
        payload.put("name", name)
        payload.put("pressed", isDown)
        payload.put("repeat", event.repeatCount)
        payload.put("scanCode", event.scanCode)
        payload.put("keyCode", event.keyCode)
        payload.put("deviceId", event.deviceId)
        payload.put("source", event.source)
        payload.put("pressedButtons", JSONArray(pressed.toList()))

        if (settings.bufferEvents) buffer.add(payload)

        if (settings.mode == StreamMode.PUSH) {
            val now = System.currentTimeMillis()
            if (now - lastEmit >= settings.rateMs && ctx.hasStreamListeners(streamName)) {
                lastEmit = now
                ctx.emitStreamEvent(streamName, payload)
            }
        }
        return true
    }

    override fun writeSnapshot(o: JSObject) {
        val list = pressed.toList()
        val state = JSObject()
        for (button in list) state.put(button, true)
        val snapshot = JSObject()
        snapshot.put("pressed", JSONArray(list))
        snapshot.put("state", state)
        o.put(snapshotKey, snapshot)
    }

    override fun drainEvents() = buffer.drain()

    override fun reset() {
        pressed.clear()
        buffer.clear()
    }

    fun pressedButtons(): List<String> = pressed.toList()
}
