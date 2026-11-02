package com.hy.wristnav

import android.app.Application
import com.hy.wristnav.bridge.WatchBridgeProvider

class WristNavApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WatchBridgeProvider.initialize(this)
    }
}
