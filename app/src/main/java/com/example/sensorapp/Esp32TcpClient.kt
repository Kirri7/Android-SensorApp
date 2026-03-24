package com.example.sensorapp

import android.os.Handler
import android.os.Looper
import java.io.IOException
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class Esp32TcpClient {
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    interface ConnectionCallback {
        fun onConnected()
        fun onDisconnected()
        fun onError(message: String)
    }

    fun connect(ip: String, port: Int, callback: ConnectionCallback) {
        executor.submit {
            try {
                socket = Socket(ip, port)
                socket?.soTimeout = 5000 // 5 секунд timeout
                outputStream = socket?.getOutputStream()

                mainHandler.post { callback.onConnected() }
            } catch (e: IOException) {
                mainHandler.post { callback.onError("Connection failed: ${e.message}") }
                disconnect()
            } catch (e: Exception) {
                mainHandler.post { callback.onError("Error: ${e.message}") }
                disconnect()
            }
        }
    }

    fun sendFloat(value: Float): Boolean {
        return sendBytes(floatToBytes(value))
    }

    fun sendFloatArray(values: FloatArray): Boolean {
        val bytes = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN) // TODO why 4?
        for (value in values) {
            bytes.putFloat(value)
        }
        return sendBytes(bytes.array())
    }

    private fun sendBytes(bytes: ByteArray): Boolean {
        if (outputStream == null) return false

        return try {
            outputStream?.write(bytes)
            outputStream?.flush()
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun floatToBytes(value: Float): ByteArray {
        return ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(value)
            .array()
    }

    fun disconnect() {
        executor.submit {
            try {
                outputStream?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }

            try {
                socket?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }

            outputStream = null
            socket = null
        }
    }

    fun isConnected(): Boolean {
        return socket?.isConnected ?: false
    }

    fun startPeriodicSend(intervalMs: Long = 3000, initialValue: Float = 3.14f) {
        executor.submit {
            var value = initialValue
            while (isConnected()) {
                sendFloat(value)
                value += 5.0f

                try {
                    Thread.sleep(intervalMs)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }
    }
}