// BluetoothClient.kt (소켓 공유용 싱글톤)
package com.example.bus

import android.bluetooth.BluetoothSocket

object BluetoothClient {
    var socket: BluetoothSocket? = null
}
