package com.example.bus

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.ScaleGestureDetector
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

class KeypadActivity : AppCompatActivity() {
    private lateinit var inputText: TextView
    private lateinit var tts: TextToSpeech
    private var inputValue = ""
    private lateinit var zoomLayout: LinearLayout
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keypad)

        inputText = findViewById(R.id.textViewInput)
        zoomLayout = findViewById(R.id.main_layout)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.KOREAN
            }
        }

        val buttons = listOf(
            R.id.btn0 to "0", R.id.btn1 to "1", R.id.btn2 to "2",
            R.id.btn3 to "3", R.id.btn4 to "4", R.id.btn5 to "5",
            R.id.btn6 to "6", R.id.btn7 to "7", R.id.btn8 to "8",
            R.id.btn9 to "9", R.id.btnMinus to "-"
        )

        buttons.forEach { (id, value) ->
            findViewById<Button>(id).setOnClickListener {
                inputValue += value
                inputText.text = inputValue
                speak(value)
            }
        }

        findViewById<Button>(R.id.btnDelete).setOnClickListener {
            if (inputValue.isNotEmpty()) {
                inputValue = inputValue.dropLast(1)
                inputText.text = inputValue
                speak("지우기")
            }
        }

        findViewById<Button>(R.id.btnConfirm).setOnClickListener {
            if (inputValue.isNotEmpty()) {
                speak("확인")

                fetchBusInfoAndSpeak(inputValue)

                val serviceIntent = Intent(this, BusMonitorService::class.java).apply {
                    putExtra("bus_number", inputValue)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }

                inputValue = ""
                inputText.text = "Ex) "
            } else {
                speak("입력된 번호가 없습니다")
            }
        }

        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private var scaleFactor = 1.0f
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(1.0f, 3.0f)
                zoomLayout.scaleX = scaleFactor
                zoomLayout.scaleY = scaleFactor
                return true
            }
        })

        zoomLayout.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun fetchBusInfoAndSpeak(busNumber: String) {
        val bstopid = "505780000"
        val apiKey = "VmmzCMtH8j1CdGFODEl3NDT9N%2F5RtQ1%2F%2FgFz%2FXn0mNg%2F5%2F1vAbcB9aooCswM7zr5Z8mIXkAHrlSL7sw%2FhGY0OQ%3D%3D"
        val url = "http://apis.data.go.kr/6260000/BusanBIMS/stopArrByBstopid?bstopid=$bstopid&serviceKey=$apiKey"

        Thread {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                val inputStream = connection.inputStream

                val factory = XmlPullParserFactory.newInstance()
                val parser = factory.newPullParser()
                parser.setInput(inputStream, null)

                var eventType = parser.eventType
                var lineno = ""
                var min1 = ""
                var station1 = ""
                var min2 = ""
                var station2 = ""
                var found = false

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        when (parser.name) {
                            "lineno" -> lineno = parser.nextText().trim()
                            "min1" -> min1 = parser.nextText()
                            "station1" -> station1 = parser.nextText()
                            "min2" -> min2 = parser.nextText()
                            "station2" -> station2 = parser.nextText()
                        }
                    }
                    if (eventType == XmlPullParser.END_TAG && parser.name == "item") {
                        if (lineno == busNumber) {
                            found = true
                            val message = "$lineno 번 버스의 도착 정보입니다. 앞차는 $min1 분 후, 정류장 $station1 개 전. 뒷차는 $min2 분 후, 정류장 $station2 개 전입니다."
                            runOnUiThread {
                                speak(message)
                            }
                            break
                        }
                    }
                    eventType = parser.next()
                }

                if (!found) {
                    runOnUiThread {
                        speak("현재 $busNumber 번 버스는 도착 정보가 없습니다.")
                    }
                }

                inputStream.close()
                connection.disconnect()

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    speak("버스 정보를 불러오는 중 오류가 발생했습니다.")
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