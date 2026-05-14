package com.example.sensorapp
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    var ipAddress: String
        get() = prefs.getString("ip_address", "") ?: ""
        set(value) = prefs.edit { putString("ip_address", value) }
    var port: Int
        get() = prefs.getInt("port", 10000)
        set(value) = prefs.edit { putInt("port", value) }
}
