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

    // ──────────────────────
    // Поля класса
    // ──────────────────────
    private lateinit var sensorManager: SensorManager
    private lateinit var tv: TextView

    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var rotationVector: Sensor? = null

    // ──────────────────────
    // Жизненный цикл
    // ──────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tv = findViewById(R.id.sensorDataTextView)

        // Получаем сервис сенсоров
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // ----------------------------
        // 1️⃣ Ищем нужные датчики
        // ----------------------------
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        // ----------------------------
        // 2️⃣ Если хотя бы один датчик не найден – выводим ошибку
        // ----------------------------
        val missingSensors = mutableListOf<String>()
        if (accelerometer == null) missingSensors += "Акселерометр"
        if (gyroscope == null) missingSensors += "Гироскоп"
        if (rotationVector == null) missingSensors += "Rotation‑vector"

        if (missingSensors.isNotEmpty()) {
            // Выводим в UI и показываем Toast
            val msg = "Датчики НЕ найдены: ${missingSensors.joinToString(", ")}"
            tv.text = msg
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        } else {
            // Если всё ОК – сразу обновим UI, чтобы пользователь видел, что приложение работает
            tv.text = "Сенсоры найдены. Ожидаем данные..."
        }
    }

    // ──────────────────────
    // Регистрация / отписка от датчиков
    // ──────────────────────
    override fun onResume() {
        super.onResume()
        // Регистрируем только те датчики, которые действительно есть
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        rotationVector?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        // Отключаем всё, чтобы экономить батарею
        sensorManager.unregisterListener(this)
    }

    // ──────────────────────
    // Обработка поступивших данных
    // ──────────────────────
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                tv.append("\n\nAccelerometer:\nX = %.3f\nY = %.3f\nZ = %.3f".format(x, y, z))
            }

            Sensor.TYPE_GYROSCOPE -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                tv.append("\n\nGyroscope:\nX = %.3f\nY = %.3f\nZ = %.3f".format(x, y, z))
            }

            Sensor.TYPE_ROTATION_VECTOR -> {
                // rotation‑vector обычно возвращает 3–4 значения (x, y, z, [w])
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                tv.append("\n\nRotation‑vector:\nX = %.3f\nY = %.3f\nZ = %.3f".format(x, y, z))
            }
        }

        // Прокручиваем ScrollView к концу, чтобы пользователь видел последние данные
        tv.post {
            val scroll = findViewById<android.widget.ScrollView>(R.id.scrollRoot)
            scroll.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Здесь можно реагировать на изменения точности, но для простого примера это не нужно.
    }
}
