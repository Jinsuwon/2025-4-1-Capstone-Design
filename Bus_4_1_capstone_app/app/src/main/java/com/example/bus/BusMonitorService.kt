package com.example.bus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.*
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

class BusMonitorService : Service() {
    private lateinit var tts: TextToSpeech
    private var targetBus: String? = null
    private var hasAnnouncedArrival = false
    private var hasSentAdvertise = false
    private var prevMin1: Int? = null
    private var monitoringThread: Thread? = null



    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification())

        tts = TextToSpeech(this) {
            if (it == TextToSpeech.SUCCESS) tts.language = Locale.KOREAN
        }

        monitoringThread = Thread { monitorBus() }
        monitoringThread?.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val bus = intent?.getStringExtra("bus_number")
        if (!bus.isNullOrEmpty()) {
            setTargetBus(bus)
        }
        return START_STICKY
    }

    fun setTargetBus(busNumber: String) {
        targetBus = busNumber
        hasAnnouncedArrival = false
        hasSentAdvertise = false
        prevMin1 = null
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "bus_channel")
            .setContentTitle("버스 도착 정보 확인 중")
            .setContentText("정류장 API 모니터링 진행 중")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "bus_channel",
                "버스 감시 서비스",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun monitorBus() {
        while (true) {
            targetBus?.let { bus ->
                try {
                    val url = URL("http://apis.data.go.kr/6260000/BusanBIMS/stopArrByBstopid?bstopid=505780000&serviceKey=VmmzCMtH8j1CdGFODEl3NDT9N%2F5RtQ1%2F%2FgFz%2FXn0mNg%2F5%2F1vAbcB9aooCswM7zr5Z8mIXkAHrlSL7sw%2FhGY0OQ%3D%3D")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    val input = conn.inputStream

                    val parser = XmlPullParserFactory.newInstance().newPullParser()
                    parser.setInput(input, null)

                    var lineno = ""
                    var station1 = ""
                    var min1 = ""

                    var eventType = parser.eventType
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG) {
                            when (parser.name) {
                                "lineno" -> lineno = parser.nextText().trim()
                                "station1" -> station1 = parser.nextText().trim()
                                "min1" -> min1 = parser.nextText().trim()
                            }
                        }

                        if (eventType == XmlPullParser.END_TAG && parser.name == "item") {
                            if (lineno == bus) {
                                val currentMin = min1.toIntOrNull()
                                Log.d("BusMonitorService", "📊 currentMin=$currentMin, prevMin1=$prevMin1")

                                if (station1 == "1" && !hasAnnouncedArrival) {
                                    hasAnnouncedArrival = true
                                    speak("$bus 번 버스가 전 정류장을 출발했습니다")
                                    vibrate()  // 진동 추가
                                    try {
                                        val msg = "$bus\n"
                                        BluetoothClient.socket?.outputStream?.write(msg.toByteArray())
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }

                                if (currentMin != null) {
                                    if (!hasSentAdvertise && prevMin1 != null && currentMin - prevMin1!! >= 3) {
                                        hasSentAdvertise = true
                                        speak("버스가 출발하였습니다")
                                        try {
                                            BluetoothClient.socket?.outputStream?.write("advertise\n".toByteArray())
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                    prevMin1 = currentMin
                                }
                            }
                        }
                        eventType = parser.next()
                    }

                    input.close()
                    conn.disconnect()
                } catch (e: Exception) {
                    Log.e("BusMonitorService", "API 오류", e)
                }
            }
            Thread.sleep(15000)
        }
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val vibrationEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
        } else {
            @Suppress("DEPRECATION")
            VibrationEffect.createOneShot(500, -1)
        }

        vibrator.vibrate(vibrationEffect)
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