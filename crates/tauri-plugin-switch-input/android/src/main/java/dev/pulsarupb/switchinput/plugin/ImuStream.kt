package dev.pulsarupb.switchinput.plugin

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import app.tauri.plugin.JSObject
import org.json.JSONArray

/**
 * Accelerometer + gyroscope. Sensors are only registered while the stream is not `off`,
 * and `poll` mode keeps the latest reading cached without touching the JS bridge.
 */
class ImuStream(
    private val activity: Activity,
    private val ctx: StreamContext,
) : InputStream {
    override val streamName = "imu"
    override val snapshotKey = "imu"

    private var settings = StreamDefaults.imu()
    private val buffer = EventBuffer(settings.bufferSize)

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var registered = false

    private var lastEmit = 0L
    private var lastAccel = floatArrayOf(0f, 0f, 0f)
    private var lastGyro = floatArrayOf(0f, 0f, 0f)
    private var latest: JSObject? = null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> lastAccel = event.values.copyOf(3)
                Sensor.TYPE_GYROSCOPE -> lastGyro = event.values.copyOf(3)
                else -> return
            }

            val now = System.currentTimeMillis()
            if (now - lastEmit < settings.rateMs) return
            lastEmit = now

            val payload = JSObject()
            payload.put("type", "imu")
            payload.put("timestamp", now)
            payload.put("accel", JSONArray(lastAccel.map { it.toDouble() }))
            payload.put("gyro", JSONArray(lastGyro.map { it.toDouble() }))
            payload.put("sensorTimestamp", event.timestamp)
            latest = payload

            if (settings.bufferEvents) buffer.add(payload)
            if (settings.mode == StreamMode.PUSH && ctx.hasStreamListeners(streamName)) {
                ctx.emitStreamEvent(streamName, payload)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    override fun settings() = settings

    override fun configure(settings: StreamSettings) {
        this.settings = settings
        buffer.configure(settings.bufferEvents, settings.bufferSize)
        if (settings.mode == StreamMode.OFF) {
            stop()
            latest = null
        } else {
            start()
        }
    }

    override fun start() {
        if (settings.mode == StreamMode.OFF || registered) return
        val manager = activity.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return
        sensorManager = manager
        accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val delay = when (settings.sensorDelay) {
            "game" -> SensorManager.SENSOR_DELAY_GAME
            "fastest" -> SensorManager.SENSOR_DELAY_FASTEST
            else -> SensorManager.SENSOR_DELAY_UI
        }
        accelerometer?.let { manager.registerListener(listener, it, delay) }
        gyroscope?.let { manager.registerListener(listener, it, delay) }
        registered = true
    }

    override fun stop() {
        if (!registered) return
        sensorManager?.unregisterListener(listener)
        registered = false
    }

    override fun writeSnapshot(o: JSObject) {
        val imu = latest ?: return
        val snapshot = JSObject()
        snapshot.put("accel", imu.get("accel"))
        snapshot.put("gyro", imu.get("gyro"))
        snapshot.put("sensorTimestamp", imu.get("sensorTimestamp"))
        o.put(snapshotKey, snapshot)
    }

    override fun drainEvents() = buffer.drain()

    override fun reset() {
        lastAccel = floatArrayOf(0f, 0f, 0f)
        lastGyro = floatArrayOf(0f, 0f, 0f)
        latest = null
        buffer.clear()
    }
}
