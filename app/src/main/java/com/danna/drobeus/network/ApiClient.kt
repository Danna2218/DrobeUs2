package com.danna.drobeus.network

import android.content.Context
import com.danna.drobeus.UserPrefs
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    const val BASE_URL = "https://drobeus-api.onrender.com/"

    private lateinit var prefs: UserPrefs

    fun init(context: Context) {
        prefs = UserPrefs(context.applicationContext)
    }

    private val okHttp by lazy {
        OkHttpClient.Builder()
            // Agrega el access token a cada petición
            .addInterceptor { chain ->
                val token = prefs.accessToken()
                val peticion = if (token.isNotEmpty()) {
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer $token").build()
                } else {
                    chain.request()
                }
                chain.proceed(peticion)
            }
            // Si la API responde 401, renueva los tokens y reintenta
            .authenticator(TokenAuthenticator(prefs))
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })

            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
}