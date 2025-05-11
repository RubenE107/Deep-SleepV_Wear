package com.example.wear_os1

import android.Manifest
import android.content.Intent
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

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL_NAME)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "getHeartRate" -> result.success(heartRate.toInt())
                    "getAccelerometer" -> result.success(mapOf("x" to accelX, "y" to accelY, "z" to accelZ))
                    "getSteps" -> result.success(stepCount.toInt())
                    else -> result.notImplemented()
                }
            }

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

        checkPermissions()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissionsNeeded = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED)
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED)
                permissionsNeeded.add(Manifest.permission.BODY_SENSORS)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED)
                permissionsNeeded.add(Manifest.permission.ACTIVITY_RECOGNITION)

            if (permissionsNeeded.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), PERMISSION_REQUEST_CODE)
            } else {
                startSensors()
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BODY_SENSORS), PERMISSION_REQUEST_CODE)
            } else {
                startSensors()
            }
        }
    }

    private fun startSensors() {
        startService(Intent(this, SensorService::class.java))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startSensors()
            } else {
                Log.e("PERMISOS", "⛔ Permisos denegados por el usuario")
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
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                accelX = event.values[0]
                accelY = event.values[1]
                accelZ = event.values[2]
            }
            Sensor.TYPE_STEP_COUNTER -> {
                val steps = event.values.getOrNull(0)
                if (steps != null && !steps.isNaN()) {
                    stepCount = steps
                }
            }
        }

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
