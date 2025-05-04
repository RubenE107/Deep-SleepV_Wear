package com.example.wear_os1

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.*
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var heartRate: Float = -1f
    private var accelX: Float = 0f
    private var accelY: Float = 0f
    private var accelZ: Float = 0f
    private var stepCount: Float = 0f

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val CHANNEL_NAME = "com.example.watch/sensors"
        private const val EVENT_CHANNEL_NAME = "com.example.watch/sensorUpdates"
    }

    private var eventSink: EventChannel.EventSink? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // Configuración del canal de comunicación con Flutter
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL_NAME)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "getHeartRate" -> result.success(heartRate.toInt())
                    "getAccelerometer" -> result.success(mapOf("x" to accelX, "y" to accelY, "z" to accelZ))
                    "getSteps" -> result.success(stepCount.toInt())
                    else -> result.notImplemented()
                }
            }

        // Configuración del EventChannel para actualizaciones en tiempo real
        EventChannel(flutterEngine.dartExecutor.binaryMessenger, EVENT_CHANNEL_NAME)
            .setStreamHandler(object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    eventSink = events
                    registerSensors()
                }

                override fun onCancel(arguments: Any?) {
                    eventSink = null
                    sensorManager.unregisterListener(this@MainActivity)
                }
            })

        // Solicitar permisos
        checkPermissions()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.BODY_SENSORS,
                        Manifest.permission.ACTIVITY_RECOGNITION
                    ),
                    PERMISSION_REQUEST_CODE
                )
            } else {
                //registerSensors()
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.BODY_SENSORS),
                    PERMISSION_REQUEST_CODE
                )
            } else {
                //registerSensors()
            }
        }
    }

    private fun registerSensors() {
        val heartSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        heartSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        stepSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                val value = event.values.getOrNull(0)
                if (value != null && value >= 0 && !value.isNaN()) {
                    heartRate = value
                    Log.d("HEART", "💓 Ritmo cardíaco actualizado: $heartRate")
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
                    Log.d("STEPS", "🚶 Pasos contados: $stepCount")
                }
            }
        }

        // Enviar datos al EventChannel
        eventSink?.success(
            mapOf(
                "heartRate" to heartRate,
                "accelerometer" to mapOf("x" to accelX, "y" to accelY, "z" to accelZ),
                "steps" to stepCount
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}