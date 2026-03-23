package com.example.sensorapp

import android.hardware.Sensor
import android.hardware.SensorManager
import android.content.Context
import android.hardware.SensorEventListener


class SensorHandler(private val sensorManager: SensorManager) {
    //    TODO think about Context
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var rotationVector: Sensor? = null
    private var linearAccelerometer: Sensor? = null

    init {
        initializeSensors()
    }

    private fun initializeSensors() {
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        linearAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    }

    fun getMissingSensors(): List<String> {
        val missingSensors = mutableListOf<String>()
        if (accelerometer == null) missingSensors.add("Акселерометр")
        if (gyroscope == null) missingSensors.add("Гироскоп")
        if (rotationVector == null) missingSensors.add("Rotation-vector")
        if (linearAccelerometer == null) missingSensors.add("Линейный акселерометр")
        return missingSensors
    }

    fun registerListeners(listener: SensorEventListener, samplingRate: Int) {
        accelerometer?.let { sensorManager.registerListener(listener, it, samplingRate) }
        gyroscope?.let { sensorManager.registerListener(listener, it, samplingRate) }
        rotationVector?.let { sensorManager.registerListener(listener, it, samplingRate) }
        linearAccelerometer?.let { sensorManager.registerListener(listener, it, samplingRate) }
    }

    fun unregisterListeners(listener: SensorEventListener) {
        sensorManager.unregisterListener(listener)
    }
}