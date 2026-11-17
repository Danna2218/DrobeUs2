package com.danna.drobeus

import android.app.Application
import com.danna.drobeus.network.ApiClient

class DrobeUsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiClient.init(this)
    }
}