package dev.pulsarupb.switchinput.plugin

import android.view.MotionEvent
import android.util.Log
import app.tauri.plugin.JSObject
import kotlin.math.abs

/**
 * Analog sticks, triggers and hat/d-pad axes.
 *
 * Values are change-detected with a per-stream deadzone and throttled by `rateMs`.
 * The latest values are always cached so `poll()` works with zero bridge traffic.
 */
class AxisStream(private val ctx: StreamContext) : InputStream {
    override val streamName = "axes"
    override val snapshotKey = "axes"

    private var settings = StreamDefaults.axes()
    private val buffer = EventBuffer(settings.bufferSize)
    private var lastEmit = 0L
    private var lastLog = 0L
    private val lastValues = FloatArray(8)
    private var latestAxes: JSObject? = null

    override fun settings() = settings

    override fun configure(settings: StreamSettings) {
        this.settings = settings
        buffer.configure(settings.bufferEvents, settings.bufferSize)
        if (settings.mode == StreamMode.OFF) latestAxes = null
    }

    override fun onMotionEvent(event: MotionEvent): Boolean {
        if (settings.mode == StreamMode.OFF) return false
        if (!KeyMap.isGamepadSource(event.source)) return false
        if (event.action != MotionEvent.ACTION_MOVE) return false

        val leftX = event.getAxisValue(MotionEvent.AXIS_X)
        val leftY = event.getAxisValue(MotionEvent.AXIS_Y)
        val rightX = pickLargest(
            event.getAxisValue(MotionEvent.AXIS_RX),
            event.getAxisValue(MotionEvent.AXIS_Z)
        )
        val rightY = pickLargest(
            event.getAxisValue(MotionEvent.AXIS_RY),
            event.getAxisValue(MotionEvent.AXIS_RZ)
        )
        val l2 = maxOf(
            event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_BRAKE)
        )
        val r2 = maxOf(
            event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_GAS)
        )
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

        val values = floatArrayOf(leftX, leftY, rightX, rightY, l2, r2, hatX, hatY)

        val now = System.currentTimeMillis()
        var changed = false
        val threshold = settings.deadzone.toFloat()
        for (i in values.indices) {
            if (abs(values[i] - lastValues[i]) > threshold) {
                changed = true
                break
            }
        }
        if (!changed && now - lastEmit < settings.rateMs) return false
        values.copyInto(lastValues)
        lastEmit = now

        if (now - lastLog >= 250) {
            lastLog = now
            Log.i(
                ctx.tag,
                "axes L=(${"%.2f".format(leftX)},${"%.2f".format(leftY)}) " +
                    "R=(${"%.2f".format(rightX)},${"%.2f".format(rightY)}) " +
                    "L2=${"%.2f".format(l2)} R2=${"%.2f".format(r2)} " +
                    "hat=($hatX,$hatY) device=${event.device?.name}"
            )
        }

        val axes = JSObject()
        axes.put("leftX", leftX.toDouble())
        axes.put("leftY", leftY.toDouble())
        axes.put("rightX", rightX.toDouble())
        axes.put("rightY", rightY.toDouble())
        axes.put("l2", l2.toDouble())
        axes.put("r2", r2.toDouble())
        axes.put("hatX", hatX.toDouble())
        axes.put("hatY", hatY.toDouble())
        if (settings.includeRaw) {
            axes.put("raw", rawAxes(event))
        }
        latestAxes = axes

        if (settings.bufferEvents || settings.mode == StreamMode.PUSH) {
            val payload = JSObject()
            payload.put("type", "axes")
            payload.put("timestamp", now)
            payload.put("axes", axes)
            payload.put("deviceId", event.deviceId)
            payload.put("source", event.source)
            if (settings.bufferEvents) buffer.add(payload)
            if (settings.mode == StreamMode.PUSH && ctx.hasStreamListeners(streamName)) {
                ctx.emitStreamEvent(streamName, payload)
            }
        }
        return false
    }

    override fun writeSnapshot(o: JSObject) {
        val axes = latestAxes ?: return
        o.put(snapshotKey, axes)
    }

    override fun drainEvents() = buffer.drain()

    override fun reset() {
        lastValues.fill(0f)
        latestAxes = null
        buffer.clear()
    }

    private fun rawAxes(event: MotionEvent): JSObject {
        val raw = JSObject()
        raw.put("X", event.getAxisValue(MotionEvent.AXIS_X).toDouble())
        raw.put("Y", event.getAxisValue(MotionEvent.AXIS_Y).toDouble())
        raw.put("Z", event.getAxisValue(MotionEvent.AXIS_Z).toDouble())
        raw.put("RX", event.getAxisValue(MotionEvent.AXIS_RX).toDouble())
        raw.put("RY", event.getAxisValue(MotionEvent.AXIS_RY).toDouble())
        raw.put("RZ", event.getAxisValue(MotionEvent.AXIS_RZ).toDouble())
        raw.put("HAT_X", event.getAxisValue(MotionEvent.AXIS_HAT_X).toDouble())
        raw.put("HAT_Y", event.getAxisValue(MotionEvent.AXIS_HAT_Y).toDouble())
        raw.put("LTRIGGER", event.getAxisValue(MotionEvent.AXIS_LTRIGGER).toDouble())
        raw.put("RTRIGGER", event.getAxisValue(MotionEvent.AXIS_RTRIGGER).toDouble())
        raw.put("BRAKE", event.getAxisValue(MotionEvent.AXIS_BRAKE).toDouble())
        raw.put("GAS", event.getAxisValue(MotionEvent.AXIS_GAS).toDouble())
        raw.put("THROTTLE", event.getAxisValue(MotionEvent.AXIS_THROTTLE).toDouble())
        raw.put("RUDDER", event.getAxisValue(MotionEvent.AXIS_RUDDER).toDouble())
        raw.put("WHEEL", event.getAxisValue(MotionEvent.AXIS_WHEEL).toDouble())
        return raw
    }

    private fun pickLargest(a: Float, b: Float): Float = if (abs(a) >= abs(b)) a else b
}
