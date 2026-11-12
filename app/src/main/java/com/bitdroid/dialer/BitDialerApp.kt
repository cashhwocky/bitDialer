package com.bitdroid.dialer

import android.app.Application
import com.bitdroid.dialer.call.CallNotifier

class BitDialerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CallNotifier.setupChannels(this)
    }
}
