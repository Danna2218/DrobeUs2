package com.danna.drobeus

import android.content.Context

class UserPrefs(context: Context) {
    private val sp = context.getSharedPreferences("drobeus", Context.MODE_PRIVATE)

    fun saveSession(
        id: String, nombre: String, correo: String, periodo: String,
        accessToken: String, refreshToken: String, onboarded: Boolean
    ) {
        sp.edit()
            .putString("userId", id)
            .putString("name", nombre)
            .putString("email", correo)
            .putString("semester", periodo)
            .putString("accessToken", accessToken)
            .putString("refreshToken", refreshToken)
            .putBoolean("onboarded", onboarded)
            .apply()
    }

    fun saveTokens(accessToken: String, refreshToken: String) {
        sp.edit().putString("accessToken", accessToken)
            .putString("refreshToken", refreshToken).apply()
    }

    fun saveProfile(school: String, career: String, semester: String, schedule: String?) {
        sp.edit().putString("school", school).putString("career", career)
            .putString("semester", semester).putString("schedule", schedule)
            .putBoolean("onboarded", true).apply()
    }

    // Temporal: lo usa la pantalla de Registro actual hasta que la conectemos a la API
    fun register(name: String, email: String, pass: String) {
        sp.edit().putString("name", name).putString("email", email).apply()
    }

    fun saveName(nombre: String) {
        sp.edit().putString("name", nombre).apply()
    }

    fun logout() {
        sp.edit().clear().apply()
    }

    fun isLogged() = userId().isNotEmpty() && refreshToken().isNotEmpty()
    fun isOnboarded() = sp.getBoolean("onboarded", false)
    fun userId() = sp.getString("userId", "") ?: ""
    fun accessToken() = sp.getString("accessToken", "") ?: ""
    fun refreshToken() = sp.getString("refreshToken", "") ?: ""
    fun name() = sp.getString("name", "") ?: ""
    fun semester() = sp.getString("semester", "") ?: ""
    fun school() = sp.getString("school", "") ?: ""
    fun career() = sp.getString("career", "") ?: ""
}