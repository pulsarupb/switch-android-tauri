package dev.pulsarupb.switchinput.plugin

import app.tauri.annotation.InvokeArg
import app.tauri.plugin.JSObject

/** How a stream is delivered to JavaScript. */
enum class StreamMode {
    OFF,
    PUSH,
    POLL;

    companion object {
        fun from(value: String?): StreamMode? = when (value?.lowercase()) {
            "off" -> OFF
            "push" -> PUSH
            "poll" -> POLL
            else -> null
        }
    }
}

/** Raw per-stream options coming from JS (`configure`). All fields are optional. */
@InvokeArg
class StreamOptions {
    var mode: String? = null
    var rateMs: Long? = null
    var deadzone: Double? = null
    var includeRaw: Boolean? = null
    var sensorDelay: String? = null
    var bufferEvents: Boolean? = null
    var bufferSize: Int? = null
}

@InvokeArg
class StreamsConfigArgs {
    var buttons: StreamOptions? = null
    var axes: StreamOptions? = null
    var touch: StreamOptions? = null
    var imu: StreamOptions? = null
}

@InvokeArg
class ConfigureArgs {
    var streams: StreamsConfigArgs? = null
}

@InvokeArg
class PollArgs {
    var streams: List<String>? = null
    var drain: Boolean? = null
}

/**
 * Resolved, mutable settings for a single stream. `merge` applies a partial update
 * on top of the current values so repeated `configure` calls are incremental.
 */
class StreamSettings(
    var mode: StreamMode,
    var rateMs: Long,
    var deadzone: Double = 0.0,
    var includeRaw: Boolean = false,
    var sensorDelay: String = "ui",
    var bufferEvents: Boolean = false,
    var bufferSize: Int = 128,
) {
    fun merge(options: StreamOptions?) {
        if (options == null) return
        StreamMode.from(options.mode)?.let { mode = it }
        options.rateMs?.let { rateMs = it.coerceAtLeast(0) }
        options.deadzone?.let { deadzone = it.coerceAtLeast(0.0) }
        options.includeRaw?.let { includeRaw = it }
        options.sensorDelay?.let { sensorDelay = it.lowercase() }
        options.bufferEvents?.let { bufferEvents = it }
        options.bufferSize?.let { bufferSize = it.coerceIn(1, 4096) }
    }

    fun toJson(): JSObject {
        val o = JSObject()
        o.put("mode", mode.name.lowercase())
        o.put("rateMs", rateMs)
        o.put("deadzone", deadzone)
        o.put("includeRaw", includeRaw)
        o.put("sensorDelay", sensorDelay)
        o.put("bufferEvents", bufferEvents)
        o.put("bufferSize", bufferSize)
        return o
    }
}

/** Conservative defaults: IMU is opt-in because it is the highest-rate stream. */
object StreamDefaults {
    fun buttons() = StreamSettings(
        mode = StreamMode.PUSH,
        rateMs = 0,
        bufferEvents = true,
        bufferSize = 256,
    )

    fun axes() = StreamSettings(
        mode = StreamMode.PUSH,
        rateMs = 16,
        deadzone = 0.01,
        includeRaw = false,
        bufferEvents = false,
        bufferSize = 64,
    )

    fun touch() = StreamSettings(
        mode = StreamMode.PUSH,
        rateMs = 0,
        bufferEvents = false,
        bufferSize = 64,
    )

    fun imu() = StreamSettings(
        mode = StreamMode.OFF,
        rateMs = 50,
        sensorDelay = "ui",
        bufferEvents = false,
        bufferSize = 64,
    )
}
