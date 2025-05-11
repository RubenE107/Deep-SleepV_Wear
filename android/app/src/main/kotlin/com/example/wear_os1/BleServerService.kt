package com.example.wear_os1

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.*
import android.util.Log
import org.json.JSONObject
import java.nio.charset.Charset
import java.util.*

class BleServerService(private val context: Context) {
    private val SERVICE_UUID = UUID.fromString("0000abcd-0000-1000-8000-00805f9b34fb")
    private val CHARACTERISTIC_UUID = UUID.fromString("00001234-0000-1000-8000-00805f9b34fb")

    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter = manager.adapter
    private val advertiser = adapter.bluetoothLeAdvertiser
    private var gattServer: BluetoothGattServer? = null

    private lateinit var characteristic: BluetoothGattCharacteristic
    private var lastDevice: BluetoothDevice? = null
    private val handler = Handler(Looper.getMainLooper())

    private var latestHeartRate: Float = 0f
    private var latestSteps: Float = 0f
    private var latesX: Float = 0f 
    private var latesY: Float = 0f 
    private var latesZ: Float = 0f 
    //private var latestProximidad: Float = 0f 
  
    fun updateSensorValues(hr: Float, steps: Float,x: Float, y: Float, z: Float) {
        latestHeartRate = hr
        latestSteps = steps
        //latestProximidad = proximidad
        latesX = x
        latesY = y
        latesZ = z
       
        // Log.d("BLE_SERVER", "Valores actualizados: $latestHeartRate, $latestSteps, $latestTemp")
    }

    fun start() {
        gattServer = manager.openGattServer(context, gattCallback)

        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        characteristic = BluetoothGattCharacteristic(
            CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(characteristic)
        gattServer?.addService(service)

        startAdvertising()
        ///Log.d("BLE_SERVER", "🟢 Servidor BLE inicializado")

    }

    private fun startAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        advertiser.startAdvertising(settings, data, advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i("BLE_SERVER", "✅ Advertising iniciado")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e("BLE_SERVER", "❌ Error al iniciar advertising: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("BLE_SERVER", "📲 Dispositivo conectado: ${device.address}")
                lastDevice = device
                startSendingData()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("BLE_SERVER", "🔌 Dispositivo desconectado: ${device.address}")
                lastDevice = null
            }
        }
    }

    private fun startSendingData() {
        handler.post(object : Runnable {
            override fun run() {
                if (lastDevice != null) {
                    val json = JSONObject(mapOf(
                        "heartRate" to latestHeartRate,
                        "steps" to latestSteps,
                        //"temperature" to latestTemp,
                        "accelerometer" to mapOf(
                            "x" to latesX,
                            "y" to latesY,
                            "z" to latesZ
                        ),
                        //"proximidad" to latestProximidad
                        

                    )).toString()

                    characteristic.value = json.toByteArray(Charset.forName("UTF-8"))
                    val result = gattServer?.notifyCharacteristicChanged(lastDevice, characteristic, false)
                    Log.d("BLE_SERVER", "📤 Enviado a ${lastDevice?.address} con éxito: $json")
                } else {
                    Log.d("BLE_SERVER", "⏳ Esperando conexión para enviar datos BLE")
                }
                handler.postDelayed(this, 500)
            }
        })
    }

    fun stop() {
        advertiser.stopAdvertising(advertiseCallback)
        gattServer?.close()
        handler.removeCallbacksAndMessages(null)
    }
}
