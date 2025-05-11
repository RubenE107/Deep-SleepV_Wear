package com.example.wear_os1

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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
    private val envioRunnable =
            object : Runnable {
                override fun run() {
                    if (heartRate > 0f) {
                        bleServer.updateSensorValues(heartRate, stepCount, accelX, accelY, accelZ)
                        Log.d("BLE_SERVER", "📤 Enviando datos con HR: $heartRate")
                    } else {
                        Log.d("BLE_SERVER", "⛔ HR inválido, datos no enviados.")
                    }
                    envioHandler.postDelayed(this, 500)
                }
            }

    companion object {
        private const val CHANNEL_ID = "sensor_service_channel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        Log.d("SENSOR_SERVICE", "🟢 onCreate iniciado")

        createNotificationChannel()
        val notification = createNotification()

        Log.d("SENSOR_SERVICE", "🔔 Preparando notificación foreground...")
        startForeground(NOTIFICATION_ID, createNotification())
        Log.d("SENSOR_SERVICE", "✅ startForeground ejecutado correctamente")

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::SensorLock")
        wakeLock.acquire()

        registerSensors()

        bleServer = BleServerService(this)
        bleServer.start()
        envioHandler.post(envioRunnable)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                    NotificationChannel(
                            CHANNEL_ID,
                            "Sensor Service",
                            NotificationManager.IMPORTANCE_DEFAULT // IMPORTANTE: NO LOW
                    )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Sensores activos")
                .setContentText("Recopilando datos en segundo plano...")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pendingIntent) // al tocar, abre la app
                .setOngoing(true) // notificación persistente
                .build()
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
            Sensor.TYPE_HEART_RATE -> {
                val value = event.values.getOrNull(0)
                if (value != null && value >= 0 && !value.isNaN()) {
                    heartRate = value
                    Log.d("HEART", "💓 HR actualizado: $heartRate")
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                accelX = event.values[0]
                accelY = event.values[1]
                accelZ = event.values[2]
                Log.d("ACCEL", "🏃 Acelerómetro -> X: $accelX, Y: $accelY, Z: $accelZ")
            }
            Sensor.TYPE_STEP_COUNTER -> {
                val steps = event.values.getOrNull(0)
                if (steps != null && !steps.isNaN()) {
                    stepCount = steps
                    Log.d("STEPS", "🚶 Pasos: $stepCount")
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        envioHandler.removeCallbacksAndMessages(null)
        bleServer.stop()
        sensorManager.unregisterListener(this)

        if (::wakeLock.isInitialized && wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.w("SENSOR_SERVICE", "❗ Servicio fue removido por el sistema, reiniciando...")

        val restartServiceIntent =
                Intent(applicationContext, SensorService::class.java).also {
                    it.setPackage(packageName)
                }

        val restartPendingIntent =
                PendingIntent.getService(
                        applicationContext,
                        1,
                        restartServiceIntent,
                        PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000,
                restartPendingIntent
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
