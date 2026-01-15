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
            }

            Sensor.TYPE_GYROSCOPE -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                tvGyroscope.text = "Гироскоп:\nX = %.3f rad/s\nY = %.3f rad/s\nZ = %.3f rad/s".format(x, y, z)
            }

            Sensor.TYPE_ROTATION_VECTOR -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                // Для rotation vector добавляем четвертое значение если есть
                val hasFourthValue = event.values.size >= 4
                val fourthValue = if (hasFourthValue) "W = %.3f".format(event.values[3]) else "W = N/A"

                tvRotation.text = "Rotation-vector:\nX = %.3f\nY = %.3f\nZ = %.3f\n%s".format(x, y, z, fourthValue)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Можно добавить обработку изменения точности при необходимости
    }
}
