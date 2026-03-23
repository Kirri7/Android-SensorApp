package com.example.sensorapp

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class FileDataHandler(private val context: Context) {
    //    TODO think about Context

    private lateinit var outputFile: File
    private var lastWriteTime = 0L
    private val writeInterval = 50L // 20 раз в секунду = 50ms интервал

    private val dataBuffer = StringBuilder()
    private var lastFlushTime = 0L
    private val flushInterval = 100L // сбрасываем буфер каждые 100мс

    init {
        createFile()
    }

    private fun createFile() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        outputFile = File(context.getExternalFilesDir(null), "sensor_data.txt")
        // Очищаем файл при старте
        if (outputFile.exists()) {
            outputFile.delete()
        }
    }

    fun writeToFile(data: String) {
        dataBuffer.append(data).append("\n")
        val currentTime = System.currentTimeMillis()

        // adb shell tail -f /sdcard/Android/data/com.example.sensorapp/files/sensor_data.txt
        if (currentTime - lastFlushTime > flushInterval) {
            try {
                FileOutputStream(outputFile, true).use { fos -> // true = APPEND mode
                    fos.write(dataBuffer.toString().toByteArray())
                    dataBuffer.clear()
                }
                lastFlushTime = currentTime
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}