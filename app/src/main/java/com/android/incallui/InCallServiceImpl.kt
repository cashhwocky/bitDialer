package com.android.incallui

import com.bitdroid.dialer.call.CallService

/**
 * Telecom binds the *system* dialer's call UI by a fixed class name taken from its config
 * (`incall_default_class` = com.android.incallui.InCallServiceImpl, inherited from the
 * AOSP/Lineage Dialer). When bitDialer ships inside bitDroid as the system dialer, Telecom
 * looks for exactly this class in our package, so it lives here and simply is CallService.
 * Installed as a normal app, Telecom resolves it through the InCallService intent filter instead.
 */
class InCallServiceImpl : CallService()
