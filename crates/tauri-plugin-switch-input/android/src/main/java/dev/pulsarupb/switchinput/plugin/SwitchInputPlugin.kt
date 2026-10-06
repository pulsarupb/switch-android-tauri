package dev.pulsarupb.switchinput.plugin

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import java.util.Collections

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
 * The host Activity forwards raw events through [SwitchInputBridge]; this class turns
 * them into stable JSON payloads and pushes them to the webview as the `input` event.
 */
@TauriPlugin
class SwitchInputPlugin(private val activity: Activity) : Plugin(activity) {
    private val tag = "SwitchInput"

    private val pressedButtons: MutableSet<String> =
        Collections.synchronizedSet(LinkedHashSet<String>())

    @Volatile
    private var enabled: Boolean = true

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var sensorsStarted = false
    private var lastImuEmit = 0L
    private var lastAxesLog = 0L
    private var lastAxesEmit = 0L
    private val lastAxes = FloatArray(8)
    private var lastAccel = floatArrayOf(0f, 0f, 0f)
    private var lastGyro = floatArrayOf(0f, 0f, 0f)

    private val imuListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> lastAccel = event.values.copyOf(3)
                Sensor.TYPE_GYROSCOPE -> lastGyro = event.values.copyOf(3)
                else -> return
            }
            val now = System.currentTimeMillis()
            // Throttle: the WebView bridge cannot keep up with raw sensor rates.
            if (now - lastImuEmit < 50) return
            lastImuEmit = now
            emit("imu") { o ->
                o.put("accel", JSONArray(lastAccel.map { it.toDouble() }))
                o.put("gyro", JSONArray(lastGyro.map { it.toDouble() }))
                o.put("sensorTimestamp", event.timestamp)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    override fun load(webView: WebView) {
        SwitchInputBridge.attach(this)
        startSensors()
        Log.i(tag, "attached; controller=${controllerName()}")
    }

    override fun onResume(activity: AppCompatActivity) {
        startSensors()
    }

    override fun onPause(activity: AppCompatActivity) {
        stopSensors()
    }

    override fun onDestroy(activity: AppCompatActivity) {
        stopSensors()
        SwitchInputBridge.detach(this)
    }

    // ------------------------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------------------------

    @Command
    fun setEnabled(invoke: Invoke) {
        val args = invoke.parseArgs(SetEnabledArgs::class.java)
        enabled = args.enabled
        if (!enabled) {
            pressedButtons.clear()
        } else {
            startSensors()
        }
        invoke.resolve(stateObject())
    }

    @Command
    fun getState(invoke: Invoke) {
        invoke.resolve(stateObject())
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

    fun captureKeyEvent(event: KeyEvent): Boolean {
        if (!enabled) return false
        val gamepadSource = KeyMap.isGamepadSource(event.source) ||
            KeyMap.isGamepadSource(event.device?.sources ?: 0)
        if (!gamepadSource) return false

        val name = KeyMap.buttonName(event) ?: return true
        val isDown = event.action == KeyEvent.ACTION_DOWN
        if (isDown) {
            pressedButtons.add(name)
        } else {
            pressedButtons.remove(name)
        }
        Log.i(
            tag,
            "button $name ${if (isDown) "down" else "up"} " +
                "scan=${event.scanCode} key=${event.keyCode} src=${event.source} " +
                "device=${event.device?.name}"
        )

        emit("button") { o ->
            o.put("name", name)
            o.put("pressed", isDown)
            o.put("repeat", event.repeatCount)
            o.put("scanCode", event.scanCode)
            o.put("keyCode", event.keyCode)
            o.put("deviceId", event.deviceId)
            o.put("source", event.source)
            o.put("pressedButtons", JSONArray(pressedButtons.toList()))
        }
        return true
    }

    fun captureGenericMotionEvent(event: MotionEvent): Boolean {
        if (!enabled) return false
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

        // Only forward when an axis actually moved (and at most every 100ms otherwise).
        val now = System.currentTimeMillis()
        var changed = false
        for (i in values.indices) {
            if (kotlin.math.abs(values[i] - lastAxes[i]) > 0.005f) {
                changed = true
                break
            }
        }
        if (!changed && now - lastAxesEmit < 100) return false
        lastAxesEmit = now
        values.copyInto(lastAxes)

        if (now - lastAxesLog >= 250) {
            lastAxesLog = now
            Log.i(
                tag,
                "axes L=(${"%.2f".format(leftX)},${"%.2f".format(leftY)}) " +
                    "R=(${"%.2f".format(rightX)},${"%.2f".format(rightY)}) " +
                    "L2=${"%.2f".format(l2)} R2=${"%.2f".format(r2)} " +
                    "hat=($hatX,$hatY) device=${event.device?.name}"
            )
        }

        emit("axes") { o ->
            val axes = JSObject()
            axes.put("leftX", leftX.toDouble())
            axes.put("leftY", leftY.toDouble())
            axes.put("rightX", rightX.toDouble())
            axes.put("rightY", rightY.toDouble())
            axes.put("l2", l2.toDouble())
            axes.put("r2", r2.toDouble())
            axes.put("hatX", event.getAxisValue(MotionEvent.AXIS_HAT_X).toDouble())
            axes.put("hatY", event.getAxisValue(MotionEvent.AXIS_HAT_Y).toDouble())
            o.put("axes", axes)

            val raw = JSObject()
            raw.put("X", leftX.toDouble())
            raw.put("Y", leftY.toDouble())
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
            o.put("raw", raw)

            o.put("deviceId", event.deviceId)
            o.put("source", event.source)
        }
        return false
    }

    fun captureTouchEvent(event: MotionEvent): Boolean {
        if (!enabled) return false
        Log.v(tag, "touch action=${event.actionMasked} pointers=${event.pointerCount}")
        emit("touch") { o ->
            val pointers = JSONArray()
            for (i in 0 until event.pointerCount) {
                val p = JSObject()
                p.put("id", event.getPointerId(i))
                p.put("x", event.getX(i).toDouble())
                p.put("y", event.getY(i).toDouble())
                p.put("pressure", event.getPressure(i).toDouble())
                p.put("size", event.getSize(i).toDouble())
                p.put("touchMajor", event.getTouchMajor(i).toDouble())
                p.put("touchMinor", event.getTouchMinor(i).toDouble())
                p.put("toolMajor", event.getToolMajor(i).toDouble())
                p.put("toolMinor", event.getToolMinor(i).toDouble())
                pointers.put(p)
            }
            o.put("action", event.actionMasked)
            o.put("actionIndex", event.actionIndex)
            o.put("pointerCount", event.pointerCount)
            o.put("pointers", pointers)
            o.put("deviceId", event.deviceId)
            o.put("source", event.source)
        }
        return false
    }

    // ------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------

    private fun stateObject(): JSObject {
        val o = JSObject()
        o.put("enabled", enabled)
        o.put("available", true)
        o.put("rumbleAvailable", rumbleAvailable())
        o.put("deviceName", controllerName())
        o.put("pressed", JSONArray(pressedButtons.toList()))
        return o
    }

    private fun emit(type: String, fill: (JSObject) -> Unit) {
        val o = JSObject()
        o.put("type", type)
        o.put("timestamp", System.currentTimeMillis())
        fill(o)
        activity.runOnUiThread {
            trigger("input", o)
        }
    }

    private fun controllerName(): String? {
        for (id in InputDevice.getDeviceIds()) {
            val device = InputDevice.getDevice(id) ?: continue
            if (KeyMap.isGamepadSource(device.sources)) {
                return device.name
            }
        }
        return null
    }

    private fun startSensors() {
        if (sensorsStarted) return
        val manager = activity.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return
        sensorManager = manager
        accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        accelerometer?.let {
            manager.registerListener(imuListener, it, SensorManager.SENSOR_DELAY_UI)
        }
        gyroscope?.let {
            manager.registerListener(imuListener, it, SensorManager.SENSOR_DELAY_UI)
        }
        sensorsStarted = true
    }

    private fun stopSensors() {
        if (!sensorsStarted) return
        sensorManager?.unregisterListener(imuListener)
        sensorsStarted = false
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

    private fun pickLargest(a: Float, b: Float): Float = if (kotlin.math.abs(a) >= kotlin.math.abs(b)) a else b
}
