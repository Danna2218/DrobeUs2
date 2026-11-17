package com.danna.drobeus.network

import com.danna.drobeus.UserPrefs
import com.danna.drobeus.model.RefreshRequest
import com.danna.drobeus.model.RefreshResponse
import com.google.gson.Gson
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.util.concurrent.TimeUnit

class TokenAuthenticator(private val prefs: UserPrefs) : Authenticator {

    private val cliente = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var rechazado = false

    override fun authenticate(route: Route?, response: Response): Request? {
        val enviado = response.request.header("Authorization") ?: return null
        if (response.request.url.encodedPath.contains("/api/auth/")) return null
        if (cuentaIntentos(response) >= 2) return null

        synchronized(this) {
            // Otra petición ya renovó el token mientras esperábamos
            val actual = prefs.accessToken()
            if (actual.isNotEmpty() && "Bearer $actual" != enviado) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $actual").build()
            }

            val refresh = prefs.refreshToken()
            if (refresh.isEmpty()) return null

            val nuevos = renovar(refresh)
            val acceso = nuevos?.accessToken
            val nuevoRefresh = nuevos?.refreshToken
            if (acceso == null || nuevoRefresh == null) {
                // Solo se cierra la sesión si el servidor rechazó el refresh token
                if (rechazado) prefs.logout()
                return null
            }

            prefs.saveTokens(acceso, nuevoRefresh)
            return response.request.newBuilder()
                .header("Authorization", "Bearer $acceso").build()
        }
    }

    private fun renovar(refresh: String): RefreshResponse? {
        rechazado = false
        return try {
            val cuerpo = Gson().toJson(RefreshRequest(refresh))
                .toRequestBody("application/json".toMediaType())
            val peticion = Request.Builder()
                .url(ApiClient.BASE_URL + "api/auth/refresh")
                .post(cuerpo)
                .build()
            cliente.newCall(peticion).execute().use { r ->
                if (!r.isSuccessful) {
                    rechazado = true
                    null
                } else {
                    Gson().fromJson(r.body?.string(), RefreshResponse::class.java)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun cuentaIntentos(r: Response): Int {
        var n = 1
        var previa = r.priorResponse
        while (previa != null) {
            n++
            previa = previa.priorResponse
        }
        return n
    }
}