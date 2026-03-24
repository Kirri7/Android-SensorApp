package com.example.sensorapp

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.widget.Toast
import androidx.core.content.ContextCompat.registerReceiver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.io.IOException

class UsbConnectionHandler(private val context: Context, private val fileHandler: FileDataHandler): SerialInputOutputManager.Listener {
    //    TODO think about Context

    private var usbManager: UsbManager? = null
    private var port: UsbSerialPort? = null
    private var usbIoManager: SerialInputOutputManager? = null
    private val ACTION_USB_PERMISSION = "com.example.sensorapp.USB_PERMISSION"
    private val TAG = "ArduinoUSB"

    private val permissionIntent by lazy {
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_IMMUTABLE
        )
    }
    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    connectToArduino()
                } else {
                    fileHandler.writeToFile("USB permission denied")
                }
            }
        }
    }

    init {
        usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val intentFilter = IntentFilter(ACTION_USB_PERMISSION)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbPermissionReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(usbPermissionReceiver, intentFilter)
        }
    }
    fun connectToArduino() {
        val manager = usbManager ?: return

        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        if (availableDrivers.isEmpty()) {
            fileHandler.writeToFile("✗ Устройство не найдено")
            return
        }

        val driver = availableDrivers[0]
        val device = driver.device

        if (!manager.hasPermission(device)) {
            fileHandler.writeToFile("⏳ Запрос разрешения...")
            manager.requestPermission(device, permissionIntent)
            return
        }

        val connection = manager.openDevice(device)
        if (connection == null) {
            fileHandler.writeToFile("✗ Ошибка подключения")
            return
        }

        port = driver.ports[0]
        try {
            port?.open(connection)
            port?.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

            usbIoManager = SerialInputOutputManager(port, this)
            usbIoManager?.start()

            fileHandler.writeToFile("✓ Подключено к ${device.deviceName}")

        } catch (e: IOException) {
            fileHandler.writeToFile("✗ Ошибка: ${e.message}")
            disconnectFromArduino()
        }
    }

    fun disconnectFromArduino() {
        context.unregisterReceiver(usbPermissionReceiver) // TODO move to destructor

        try {
            usbIoManager?.stop()
            usbIoManager = null
            port?.close()
            port = null
            fileHandler.writeToFile("✓ Отключено")
        } catch (e: IOException) {
            fileHandler.writeToFile("Ошибка закрытия: ${e.message}")
        }
    }

    fun sendData(data: String) {
        try {
            val bytes = data.toByteArray()
            port?.write(bytes, 1000)
            fileHandler.writeToFile("отправлено: ${data}")
        } catch (e: IOException) {
            fileHandler.writeToFile("✗ Ошибка отправки: ${e.message}")
        }
    }

    // Callback от SerialInputOutputManager
    override fun onNewData(data: ByteArray) {
        val message = String(data).trim()
//        if (message.isNotEmpty()) {
        fileHandler.writeToFile("← $message")
        val activity = (context as? Activity)
        activity?.runOnUiThread {
            Toast.makeText(context, "Получено: $message", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRunError(e: Exception) {
        fileHandler.writeToFile("✗ Соединение разорвано: ${e.message}")
    }
}