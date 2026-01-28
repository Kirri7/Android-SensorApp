package com.example.sensorapp

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private val sensorDelay = 80000000

    // Текстовые поля для каждого типа данных
    private lateinit var tvAccelerometer: TextView
    private lateinit var tvGyroscope: TextView
    private lateinit var tvRotation: TextView

    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var rotationVector: Sensor? = null

    private lateinit var outputFile: File
    private var lastWriteTime = 0L
    private val writeInterval = 50L // 20 раз в секунду = 50ms интервал

    private val dataBuffer = StringBuilder()
    private var lastFlushTime = 0L
    private val flushInterval = 100L // сбрасываем буфер каждые 100мс


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Инициализируем отдельные TextView для каждого датчика
        tvAccelerometer = findViewById(R.id.tvAccelerometer)
        tvGyroscope = findViewById(R.id.tvGyroscope)
        tvRotation = findViewById(R.id.tvRotation)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // Получаем датчики
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        // Проверяем доступность датчиков
        val missingSensors = mutableListOf<String>()
        if (accelerometer == null) missingSensors.add("Акселерометр")
        if (gyroscope == null) missingSensors.add("Гироскоп")
        if (rotationVector == null) missingSensors.add("Rotation-vector")

        if (missingSensors.isNotEmpty()) {
            val msg = "Датчики не найдены: ${missingSensors.joinToString(", ")}"
            tvAccelerometer.text = msg
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        } else {
            // Устанавливаем начальные значения
            tvAccelerometer.text = "Акселерометр:\nОжидание данных..."
            tvGyroscope.text = "Гироскоп:\nОжидание данных..."
            tvRotation.text = "Rotation-vector:\nОжидание данных..."
        }

        // Создаем файл для записи данных
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        outputFile = File(getExternalFilesDir(null), "sensor_data.txt")
        // Очищаем файл при старте
        if (outputFile.exists()) {
            outputFile.delete()
        }
    }

    override fun onResume() {
        super.onResume()

        // Используем безопасную частоту обновления
        val defaultSamplingRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            SensorManager.SENSOR_DELAY_NORMAL  // Безопасная частота
        } else {
            SensorManager.SENSOR_DELAY_FASTEST  // Для старых версий Android
        }
        val samplingRate = maxOf(defaultSamplingRate, sensorDelay)

        // Регистрируем датчики
        accelerometer?.let {
            sensorManager.registerListener(this, it, samplingRate)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, samplingRate)
        }
        rotationVector?.let {
            sensorManager.registerListener(this, it, samplingRate)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        // Обновляем соответствующий TextView в зависимости от типа датчика
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                tvAccelerometer.text = "Акселерометр:\nX = %.3f m/s²\nY = %.3f m/s²\nZ = %.3f m/s²".format(x, y, z)
                /*
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastWriteTime > writeInterval) {
                    val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
                    val dataLine = "$timestamp,ACC,$x,$y,$z\n"
                    writeToFile(dataLine)
                    lastWriteTime = currentTime
                }
                */
            }

            Sensor.TYPE_GYROSCOPE -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                tvGyroscope.text = "Гироскоп:\nX = %.3f rad/s\nY = %.3f rad/s\nZ = %.3f rad/s".format(x, y, z)
            }

            Sensor.TYPE_ROTATION_VECTOR -> {
                val rotationMatrix = FloatArray(9)
                val orientationAngles = FloatArray(3) // Здесь будут [Yaw, Pitch, Roll]

                // Преобразуем кватернион (rotation vector) в матрицу поворота
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

                // Преобразуем матрицу поворота в углы Эйлера
                SensorManager.getOrientation(rotationMatrix, orientationAngles)

                // orientationAngles теперь содержит:
                val azimuth =
                    orientationAngles[0] // Рыскание (Yaw) - поворот вокруг Z (радианы, от -π до π)
                val pitch =
                    orientationAngles[1] // Тангаж (Pitch) - наклон вперед/назад (радианы, от -π/2 до π/2)
                val roll =
                    orientationAngles[2] // Крен (Roll) - наклон влево/вправо (радианы, от -π до π)

                // Конвертируем радианы в градусы для удобства
                val x = Math.toDegrees(azimuth.toDouble()).toFloat()
                val y = Math.toDegrees(pitch.toDouble()).toFloat()
                val z = Math.toDegrees(roll.toDouble()).toFloat()

                tvRotation.text = "Rotation-vector:\nX = %.3f\nY = %.3f\nZ = %.3f\n".format(x, y, z)

                val dataLine = "%f %f %f %f".format(x, y, z, 10.0)
                writeToFile(dataLine)
            }
        }
    }

    private fun writeToFile(data: String) {
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

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Можно добавить обработку изменения точности при необходимости
    }
}
