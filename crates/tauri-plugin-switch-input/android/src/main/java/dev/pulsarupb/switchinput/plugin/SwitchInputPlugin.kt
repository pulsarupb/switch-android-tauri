package dev.pulsarupb.switchinput.plugin

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import app.tauri.annotation.Command
import app.tauri.annotation.InvokeArg
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Invoke
import app.tauri.plugin.JSObject
import app.tauri.plugin.Plugin
import org.json.JSONArray

@InvokeArg
class SetEnabledArgs {
    var enabled: Boolean = false
}

@InvokeArg
class VibrateArgs {
    var durationMs: Long = 200
    var amplitude: Int? = null
}

/**
 * Tauri Android plugin that captures Nintendo Switch Lite hardware input.
 *
 * Capture is split into independent [InputStream]s (buttons, axes, touch, IMU). Each
 * stream can be `off`, `push` (events) or `poll` (cached snapshot), configured through
 * the `configure` command. The host Activity forwards raw events via [SwitchInputBridge].
 */
@TauriPlugin
class SwitchInputPlugin(private val activity: Activity) : Plugin(activity), StreamContext {
    override val tag = "SwitchInput"

    @Volatile
    private var enabled: Boolean = true

    private val buttonStream = ButtonStream(this)
    private val axisStream = AxisStream(this)
    private val touchStream = TouchStream(this)
    private val imuStream = ImuStream(activity, this)
    private val streams: List<InputStream> =
        listOf(buttonStream, axisStream, touchStream, imuStream)

    // ------------------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------------------

    override fun load(webView: WebView) {
        SwitchInputBridge.attach(this)
        imuStream.start()
        Log.i(tag, "attached; controller=${controllerName()}")
    }

    override fun onResume(activity: AppCompatActivity) {
        imuStream.start()
    }

    override fun onPause(activity: AppCompatActivity) {
        imuStream.stop()
    }

    override fun onDestroy(activity: AppCompatActivity) {
        imuStream.stop()
        SwitchInputBridge.detach(this)
    }

    // ------------------------------------------------------------------------------------
    // StreamContext
    // ------------------------------------------------------------------------------------

    override fun emitStreamEvent(stream: String, payload: JSObject) {
        activity.runOnUiThread {
            trigger(stream, payload)
        }
    }

    override fun hasStreamListeners(stream: String): Boolean = hasListener(stream)

    // ------------------------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------------------------

    @Command
    fun configure(invoke: Invoke) {
        val args = invoke.parseArgs(ConfigureArgs::class.java)
        val config = args.streams
        applyTo(buttonStream, config?.buttons)
        applyTo(axisStream, config?.axes)
        applyTo(touchStream, config?.touch)
        applyTo(imuStream, config?.imu)
        Log.i(tag, "configured: ${describeStreams()}")
        invoke.resolve(effectiveConfig())
    }

    @Command
    fun poll(invoke: Invoke) {
        val args = invoke.parseArgs(PollArgs::class.java)
        val requested = args.streams?.map { it.lowercase() }?.toSet()
        val drain = args.drain == true

        val snapshot = JSObject()
        snapshot.put("timestamp", System.currentTimeMillis())
        val events = JSONArray()
        for (stream in streams) {
            if (!enabled) break
            if (requested != null && !requested.contains(stream.snapshotKey)) continue
            if (stream.settings().mode == StreamMode.OFF) continue
            stream.writeSnapshot(snapshot)
            if (drain) {
                stream.drainEvents()?.let { array ->
                    for (i in 0 until array.length()) events.put(array.get(i))
                }
            }
        }
        if (drain) snapshot.put("events", events)
        invoke.resolve(snapshot)
    }

    @Command
    fun getState(invoke: Invoke) {
        invoke.resolve(effectiveConfig())
    }

    @Command
    fun setEnabled(invoke: Invoke) {
        val args = invoke.parseArgs(SetEnabledArgs::class.java)
        enabled = args.enabled
        if (enabled) {
            imuStream.start()
        } else {
            for (stream in streams) stream.reset()
            imuStream.stop()
        }
        invoke.resolve(effectiveConfig())
    }

    @Command
    fun vibrate(invoke: Invoke) {
        val args = invoke.parseArgs(VibrateArgs::class.java)
        val duration = args.durationMs.coerceIn(1L, 5000L)
        val amplitude = (args.amplitude ?: 255).coerceIn(1, 255)
        val vibrator = findVibrator()
        if (vibrator == null) {
            Log.w(tag, "vibrate: no vibrator available")
            invoke.reject("No vibrator available")
            return
        }
        Log.i(tag, "vibrate duration=$duration amplitude=$amplitude")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
        invoke.resolve()
    }

    @Command
    fun listDevices(invoke: Invoke) {
        val devices = JSONArray()
        val summary = StringBuilder()
        for (id in InputDevice.getDeviceIds()) {
            val device = InputDevice.getDevice(id) ?: continue
            val o = JSObject()
            o.put("id", device.id)
            o.put("name", device.name)
            o.put("descriptor", device.descriptor)
            o.put("vendorId", device.vendorId)
            o.put("productId", device.productId)
            o.put("sources", device.sources)
            o.put("isGamepad", KeyMap.isGamepadSource(device.sources))
            o.put("hasVibrator", hasVibrator(device))
            devices.put(o)
            summary.append("[").append(device.id).append(": ").append(device.name)
                .append(" src=").append(device.sources).append("] ")
        }
        Log.i(tag, "devices: $summary")
        val ret = JSObject()
        ret.put("devices", devices)
        invoke.resolve(ret)
    }

    // ------------------------------------------------------------------------------------
    // Capture (called from SwitchInputBridge / the host Activity)
    // ------------------------------------------------------------------------------------

    fun captureKeyEvent(event: KeyEvent): Boolean =
        if (enabled) buttonStream.onKeyEvent(event) else false

    fun captureGenericMotionEvent(event: MotionEvent): Boolean =
        if (enabled) axisStream.onMotionEvent(event) else false

    fun captureTouchEvent(event: MotionEvent): Boolean {
        if (enabled) touchStream.onMotionEvent(event)
        return false
    }

    // ------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------

    private fun applyTo(stream: InputStream, options: StreamOptions?) {
        val settings = stream.settings()
        settings.merge(options)
        stream.configure(settings)
    }

    private fun effectiveConfig(): JSObject {
        val o = JSObject()
        o.put("enabled", enabled)
        o.put("available", true)
        o.put("rumbleAvailable", rumbleAvailable())
        o.put("deviceName", controllerName())
        val streamsObj = JSObject()
        for (stream in streams) {
            streamsObj.put(stream.snapshotKey, stream.settings().toJson())
        }
        o.put("streams", streamsObj)
        return o
    }

    private fun describeStreams(): String =
        streams.joinToString(" ") { "${it.snapshotKey}=${it.settings().mode.name.lowercase()}" }

    private fun controllerName(): String? {
        for (id in InputDevice.getDeviceIds()) {
            val device = InputDevice.getDevice(id) ?: continue
            if (KeyMap.isGamepadSource(device.sources)) {
                return device.name
            }
        }
        return null
    }

    private fun findVibrator(): Vibrator? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            for (id in InputDevice.getDeviceIds()) {
                val device = InputDevice.getDevice(id) ?: continue
                if (!KeyMap.isGamepadSource(device.sources)) continue
                val vibrator = device.vibrator
                if (vibrator != null && vibrator.hasVibrator()) {
                    Log.i(tag, "vibrator: input device '${device.name}'")
                    return vibrator
                }
            }
            val manager = activity.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                as? VibratorManager
            val default = manager?.defaultVibrator
            if (default != null && default.hasVibrator()) {
                Log.i(tag, "vibrator: default (hasVibrator=${default.hasVibrator()})")
                return default
            }
            Log.w(tag, "vibrator: none available on this device")
        } else {
            @Suppress("DEPRECATION")
            val vibrator = activity.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                Log.i(tag, "vibrator: legacy default")
                return vibrator
            }
            Log.w(tag, "vibrator: none available on this device")
        }
        return null
    }

    private fun hasVibrator(device: InputDevice): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return device.vibrator?.hasVibrator() == true
    }

    /** Whether the platform exposes any vibrator at all (Switch Lite does not). */
    fun rumbleAvailable(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            for (id in InputDevice.getDeviceIds()) {
                val device = InputDevice.getDevice(id) ?: continue
                if (KeyMap.isGamepadSource(device.sources) &&
                    device.vibrator?.hasVibrator() == true
                ) {
                    return true
                }
            }
            val manager = activity.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                as? VibratorManager
            return manager?.defaultVibrator?.hasVibrator() == true
        }
        @Suppress("DEPRECATION")
        val vibrator = activity.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        return vibrator?.hasVibrator() == true
    }
}
