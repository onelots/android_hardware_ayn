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
import kotlin.math.abs
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
    private var customCurveJob: Job? = null
    private var isScreenOn = true

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    stopCustomCurveTracking()
                    FanController.pauseForScreenOff()
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    FanController.resumeForScreenOn(context)
                    checkCustomCurve()
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val profile = FanController.getCurrentProfile(this)
        if (profile == FanProfile.AUTO) {
            stopCustomCurveTracking()
            stopSelf()
            return START_NOT_STICKY
        }
        checkCustomCurve()
        return START_STICKY
    }

    private fun checkCustomCurve() {
        val profile = FanController.getCurrentProfile(this)
        if (profile == FanProfile.CUSTOM && isScreenOn) {
            startCustomCurveTracking()
        } else {
            stopCustomCurveTracking()
        }
    }

    private fun startCustomCurveTracking() {
        if (customCurveJob?.isActive == true) return
        customCurveJob = serviceScope.launch {
            var lastAppliedSpeed = -1
            while (isActive && isScreenOn) {
                val profile = FanController.getCurrentProfile(this@FanService)
                if (profile != FanProfile.CUSTOM) break

                val temp = FanController.getSocTemperature()
                if (temp != null) {
                    val curve = FanCurve.getCurve(this@FanService)
                    val targetSpeed = FanCurve.evaluate(curve, temp)
                    if (abs(targetSpeed - lastAppliedSpeed) >= 2 || lastAppliedSpeed == -1) {
                        FanController.setCustomPwm(targetSpeed)
                        lastAppliedSpeed = targetSpeed
                    }
                }
                delay(2500)
            }
        }
    }

    private fun stopCustomCurveTracking() {
        customCurveJob?.cancel()
        customCurveJob = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCustomCurveTracking()
        serviceScope.cancel()
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister screen receiver", e)
        }
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
