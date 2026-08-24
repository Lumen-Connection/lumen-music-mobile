package com.lumenconnection.music

import android.app.Application

class LumenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}
