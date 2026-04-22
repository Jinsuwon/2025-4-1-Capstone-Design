package com.example.bus

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var tts: TextToSpeech
    private lateinit var stationText: TextView

    private val TAG = "BusClassicApp"
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val targetDeviceName = "DESKTOP-FU0SKAA"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        stationText = findViewById(R.id.status_text)
        checkPermissions()
        requestBatteryOptimizationException()

        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.ERROR) {
                tts.language = Locale.KOREAN
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (BluetoothClient.socket == null || !BluetoothClient.socket!!.isConnected) {
            connectToBluetoothServer()
        }
    }

    private fun checkPermissions() {
        val permissionList = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        } else {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (permissionList.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionList.toTypedArray(), 1)
        }
    }

    private fun requestBatteryOptimizationException() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            val packageName = packageName
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }

    private fun connectToBluetoothServer() {
        val btAdapter = BluetoothAdapter.getDefaultAdapter()
        if (btAdapter == null || !btAdapter.isEnabled) {
            Toast.makeText(this, "듀레트미 확장: 블루트워스가 끊어있습니다.", Toast.LENGTH_LONG).show()
            return
        }

        val device: BluetoothDevice? = btAdapter.bondedDevices.find {
            it.name == targetDeviceName
        }

        if (device == null) {
            Toast.makeText(this, "$targetDeviceName 기기와 페연링 되지 않았습니다.", Toast.LENGTH_LONG).show()
            return
        }

        Thread {
            try {
                val socket: BluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()

                val input = socket.inputStream
                val buffer = ByteArray(1024)
                val bytes = input.read(buffer)
                val stationName = String(buffer, 0, bytes).trim()

                runOnUiThread {
                    stationText.text = "$stationName 정거장입니다."
                    tts.stop()
                    tts.speak("$stationName 정거장입니다. 연산 방면 입니다.", TextToSpeech.QUEUE_FLUSH, null, null)

                    BluetoothClient.socket = socket
                    startActivity(Intent(this, KeypadActivity::class.java))
                }

            } catch (e: IOException) {
                Log.e(TAG, "Bluetooth 연결 실패", e)
                runOnUiThread {
                    Toast.makeText(this, "연결 실패: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()

        try {
            BluetoothClient.socket?.outputStream?.write("exit\n".toByteArray())
        } catch (e: Exception) {
            e.printStackTrace()
        }

        BluetoothClient.socket?.close()
        BluetoothClient.socket = null
        super.onDestroy()
    }
}