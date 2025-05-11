package com.example.wear_os1

import android.app.Service
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.util.Log

class SensorService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var heartRate: Float = -1f
    private var accelX: Float = 0f
    private var accelY: Float = 0f
    private var accelZ: Float = 0f
    private var stepCount: Float = 0f

    private lateinit var bleServer: BleServerService
    private lateinit var wakeLock: PowerManager.WakeLock

    private val envioHandler = Handler(Looper.getMainLooper())
    private val envioRunnable = object : Runnable {
        override fun run() {
            if (heartRate > 0f) {
                bleServer.updateSensorValues(heartRate, stepCount, accelX, accelY, accelZ)
                Log.d("BLE_SERVER", "📤 Enviando datos con HR: $heartRate")
            }
            envioHandler.postDelayed(this, 500)
        }
    }

    override fun onCreate() {
    super.onCreate()

    Log.d("SENSOR_SERVICE", "🟢 onCreate iniciado")

    // Crear canal para foreground service
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            "sensor_service_channel",
            "Sensor Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    // Notificación obligatoria
    val notification = Notification.Builder(this, "sensor_service_channel")
        .setContentTitle("⏱️ Servicio de sensores")
        .setContentText("En ejecución...")
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .build()

    startForeground(1, notification) // 👈 IMPORTANTE

    // WakeLock para que no se duerma
    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::SensorLock")
    wakeLock.acquire()

    sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    registerSensors()

    bleServer = BleServerService(this)
    bleServer.start()
    envioHandler.post(envioRunnable)
}

    private fun registerSensors() {
        val heartSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        heartSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        accelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> event.values.getOrNull(0)?.takeIf { it >= 0 && !it.isNaN() }?.let {
                heartRate = it
            }
            Sensor.TYPE_ACCELEROMETER -> {
                accelX = event.values[0]
                accelY = event.values[1]
                accelZ = event.values[2]
            }
            Sensor.TYPE_STEP_COUNTER -> event.values.getOrNull(0)?.takeIf { !it.isNaN() }?.let {
                stepCount = it
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        envioHandler.removeCallbacksAndMessages(null)
        bleServer.stop()
        sensorManager.unregisterListener(this)
        if (::wakeLock.isInitialized && wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

}
