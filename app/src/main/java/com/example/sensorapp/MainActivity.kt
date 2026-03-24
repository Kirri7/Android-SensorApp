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

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var sensorHandler: SensorHandler
    private lateinit var fileHandler: FileDataHandler
    private lateinit var usbHandler: UsbConnectionHandler
    private lateinit var espClient: Esp32TcpClient
    private val sensorDelay = 80000000

    // Текстовые поля для каждого типа данных
    private lateinit var tvAccelerometer: TextView
    private lateinit var tvGyroscope: TextView
    private lateinit var tvRotation: TextView
    private lateinit var tvLinearAcceleration: TextView


    private var counter = 0
    private lateinit var tvCounter: TextView
    private val handler = Handler()
    private var volumeUpPressed = false
    private var volumeDownPressed = false


    private var isLightOn = false
    private val tiltThreshold = 30.0 // градусы



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

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        sensorHandler = SensorHandler(sensorManager); // TODO do I need ';'?
        val missingSensors : List<String> = sensorHandler.getMissingSensors();

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

        fileHandler = FileDataHandler(this)
        fileHandler.writeToFile("Starting SensorApp")

        usbHandler = UsbConnectionHandler(this, fileHandler)
        usbHandler.connectToArduino()

        espClient = Esp32TcpClient()
        // TODO proper .env file
        espClient.connect("x.x.x.x", 10000, object : Esp32TcpClient.ConnectionCallback {
            override fun onConnected() {
                fileHandler.writeToFile("ESP32: " + "Connected successfully")
                // Можно начать периодическую отправку
                espClient.startPeriodicSend(3000, 3.14f)
            }
            override fun onDisconnected() {
                fileHandler.writeToFile("ESP32: " + "Disconnected")
            }
            override fun onError(message: String) {
                fileHandler.writeToFile("ESP32: " + message)
            }
        })
        // Отправка одного значения
        // espClient.sendFloat(10.5f)
        // Отправка массива значений
        // espClient.sendFloatArray(floatArrayOf(1.0f, 2.0f, 3.0f))
    }

    override fun onDestroy() {
        espClient.disconnect()
        super.onDestroy()
        usbHandler.disconnectFromArduino()
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

        sensorHandler.registerListeners(this, samplingRate)
    }

    override fun onPause() {
        super.onPause()
        sensorHandler.unregisterListeners(this)
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
                    fileHandler.writeToFile(dataLine)
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

                val dataLine = "%f %f %f %d".format(x, y, z, counter).replace(',', '.')
                usbHandler.sendData(dataLine)
            }

            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                tvLinearAcceleration.text = "Линейный акселерометр:\nX = %.3f m/s²\nY = %.3f m/s²\nZ = %.3f m/s²".format(x, y, z)
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


    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Можно добавить обработку изменения точности при необходимости
    }

    private fun updateCounterDisplay() {
        tvCounter.text = "Счётчик: $counter"
    }

}
