package com.example.sensorapp

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.KeyEvent
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.remote.creation.compose.state.sqrt
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private val sensorDelay = 80000000

    // Текстовые поля для каждого типа данных
    private lateinit var tvAccelerometer: TextView
    private lateinit var tvGyroscope: TextView
    private lateinit var tvRotation: TextView
    private lateinit var tvLinearAcceleration: TextView

    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var rotationVector: Sensor? = null
    private var linearAccelerometer: Sensor? = null

    private lateinit var outputFile: File
    private var lastWriteTime = 0L
    private val writeInterval = 50L // 20 раз в секунду = 50ms интервал

    private val dataBuffer = StringBuilder()
    private var lastFlushTime = 0L
    private val flushInterval = 100L // сбрасываем буфер каждые 100мс

    private var counter = 0
    private lateinit var tvCounter: TextView
    private val handler = Handler()
    private var volumeUpPressed = false
    private var volumeDownPressed = false

    private lateinit var tvPositionDelta: TextView
    private var deltaX = 0.0
    private var deltaY = 0.0
    private var deltaZ = 0.0
    private var velocityX = 0.0
    private var velocityY = 0.0
    private var velocityZ = 0.0
    private var lastLinearAccelTime = 0L
    private val accelerationThreshold = 0.1 // м/с² - ускорения меньше этого игнорируем
    private val velocityThreshold = 0.05 // м/с - скорости меньше этого сбрасываем в ноль
    private val stillnessThreshold = 0.3f // м/с² - если ускорение меньше этого, считаем что телефон неподвижен
    private val stillnessTimeThreshold = 500L // мс - сколько времени должно пройти в неподвижности
    private var stillnessStartTime = 0L
    private var isStill = false

    private val volumeRunnable = object : Runnable {
        override fun run() {
            when {
                volumeUpPressed && counter < 100 -> {
                    counter++
                    updateCounterDisplay()
                    handler.postDelayed(this, 150)
                }
                volumeDownPressed && counter > -100 -> {
                    counter--
                    updateCounterDisplay()
                    handler.postDelayed(this, 150)
                }
            }
        }
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvCounter = findViewById(R.id.tvCounter)
        updateCounterDisplay()

        // Инициализируем отдельные TextView для каждого датчика
        tvAccelerometer = findViewById(R.id.tvAccelerometer)
        tvGyroscope = findViewById(R.id.tvGyroscope)
        tvRotation = findViewById(R.id.tvRotation)
        tvLinearAcceleration = findViewById(R.id.tvLinearAcceleration)
        tvPositionDelta = findViewById(R.id.tvPositionDelta)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // Получаем датчики
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        linearAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        // Проверяем доступность датчиков
        val missingSensors = mutableListOf<String>()
        if (accelerometer == null) missingSensors.add("Акселерометр")
        if (gyroscope == null) missingSensors.add("Гироскоп")
        if (rotationVector == null) missingSensors.add("Rotation-vector")
        if (linearAccelerometer == null) missingSensors.add("Линейный акселерометр")

        if (missingSensors.isNotEmpty()) {
            val msg = "Датчики не найдены: ${missingSensors.joinToString(", ")}"
            tvAccelerometer.text = msg
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        } else {
            // Устанавливаем начальные значения
            tvAccelerometer.text = "Акселерометр:\nОжидание данных..."
            tvGyroscope.text = "Гироскоп:\nОжидание данных..."
            tvRotation.text = "Rotation-vector:\nОжидание данных..."
            tvLinearAcceleration.text = "Линейный акселерометр:\nОжидание данных..."
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
        linearAccelerometer?.let {
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

            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                // Определяем общее ускорение (норма вектора)
                val totalAcceleration = sqrt(x * x + y * y + z * z)

                // Детектор неподвижности
                val currentTime = System.currentTimeMillis()

                if (totalAcceleration < stillnessThreshold) {
                    if (stillnessStartTime == 0L) {
                        stillnessStartTime = currentTime
                    } else if (currentTime - stillnessStartTime > stillnessTimeThreshold) {
                        if (!isStill) {
                            velocityX = 0.0
                            velocityY = 0.0
                            velocityZ = 0.0
                            isStill = true
                        }
                    }
                } else {
                    stillnessStartTime = 0L
                    isStill = false

                    // Интегрируем ускорение
                    val dt = (currentTime - lastLinearAccelTime) / 1000.0

                    velocityX += x * dt
                    velocityY += y * dt
                    velocityZ += z * dt
                }

                // Всегда обновляем время и рассчитываем приращение
                if (lastLinearAccelTime != 0L) {
                    val dt = (currentTime - lastLinearAccelTime) / 1000.0
                    deltaX = velocityX * dt
                    deltaY = velocityY * dt
                    deltaZ = velocityZ * dt
                }

                lastLinearAccelTime = currentTime

                tvPositionDelta.text = """
        Приращение координат:
        ΔX = %.6f m (vX = %.3f m/s)
        ΔY = %.6f m (vY = %.3f m/s)  
        ΔZ = %.6f m (vZ = %.3f m/s)
        Неподвижен: $isStill (${totalAcceleration.format(3)} м/с²)
    """.trimIndent().format(deltaX, velocityX, deltaY, velocityY, deltaZ, velocityZ)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (!volumeUpPressed) {
                            volumeUpPressed = true
                            handler.post(volumeRunnable)
                        }
                    }
                    KeyEvent.ACTION_UP -> {
                        volumeUpPressed = false
                        handler.removeCallbacks(volumeRunnable)
                    }
                }
                return true // Блокируем стандартное поведение
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (!volumeDownPressed) {
                            volumeDownPressed = true
                            handler.post(volumeRunnable)
                        }
                    }
                    KeyEvent.ACTION_UP -> {
                        volumeDownPressed = false
                        handler.removeCallbacks(volumeRunnable)
                    }
                }
                return true // Блокируем стандартное поведение
            }
        }
        return super.dispatchKeyEvent(event)
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

    private fun updateCounterDisplay() {
        tvCounter.text = "Счётчик: $counter"
    }
}
