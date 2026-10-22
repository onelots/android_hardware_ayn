/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.ayn.fan

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FanService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null
    private var isScreenOn = true

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    FanController.pauseForScreenOff()
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    FanController.resumeForScreenOn(context)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)
        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val profile = FanController.getCurrentProfile(this)
        if (profile == FanProfile.AUTO) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (monitorJob == null || monitorJob?.isActive != true) {
            startMonitoring()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            while (isActive) {
                if (isScreenOn) {
                    val profile = FanController.getCurrentProfile(this@FanService)
                    if (profile == FanProfile.AUTO) {
                        stopSelf()
                        break
                    }
                    val actualPwm = FanController.getActualPwm()
                    // If the kernel thermal governor stepped down to 0 after cooling,
                    // restore the user's requested minimum PWM speed.
                    // If actualPwm is higher, thermal throttling is active; don't lower it.
                    if (actualPwm < profile.pwm) {
                        FanController.enforcePwm(profile.pwm)
                    }
                }
                delay(1000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenReceiver)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "FanService"

        fun updateServiceState(context: Context, profile: FanProfile) {
            val intent = Intent(context, FanService::class.java)
            if (profile == FanProfile.AUTO) {
                context.stopService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
