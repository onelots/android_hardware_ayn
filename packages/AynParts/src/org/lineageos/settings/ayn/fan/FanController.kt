/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.ayn.fan

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.service.quicksettings.TileService
import android.util.Log
import androidx.preference.PreferenceManager
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.lineageos.settings.ayn.utils.NodeUtils

object FanController {

    private const val TAG = "FanController"
    private const val PREF_FAN_PROFILE = "fan_profile"

    private const val HWMON_BASE = "/sys/class/hwmon"

    // Primary SoC temperature sensor zone (cpu-1-9 / thermal_zone14 on Kalama)
    private const val PRIMARY_THERMAL_ZONE = "/sys/class/thermal/thermal_zone14/temp"

    private val fanPwmNode: File? by lazy {
        findFanPwmNode()
    }

    private val fanCoolingDeviceNode: File? by lazy {
        findFanCoolingDeviceNode()
    }

    private val _currentProfileFlow = MutableStateFlow(FanProfile.AUTO)
    val currentProfileFlow: StateFlow<FanProfile> = _currentProfileFlow.asStateFlow()

    private fun findFanPwmNode(): File? {
        try {
            val hwmonDir = File(HWMON_BASE)
            if (hwmonDir.exists() && hwmonDir.isDirectory) {
                hwmonDir.listFiles()?.forEach { dir ->
                    val nameFile = File(dir, "name")
                    if (nameFile.exists() && nameFile.readText().trim() == "pwmfan") {
                        val pwmFile = File(dir, "pwm1")
                        if (pwmFile.exists()) return pwmFile
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to scan hwmon nodes", e)
        }
        val fallback = File("/sys/devices/platform/soc/c42d000.qcom,spmi/spmi-0/0-01/c42d000.qcom,spmi:qcom,pm8550@1:pwm-fan@0/hwmon/hwmon0/pwm1")
        return if (fallback.exists()) fallback else null
    }

    private fun findFanCoolingDeviceNode(): File? {
        try {
            val thermalDir = File(THERMAL_BASE)
            if (thermalDir.exists() && thermalDir.isDirectory) {
                thermalDir.listFiles()?.forEach { dir ->
                    if (dir.name.startsWith("cooling_device")) {
                        val typeFile = File(dir, "type")
                        if (typeFile.exists() && typeFile.readText().trim() == "pwm-fan") {
                            val curStateFile = File(dir, "cur_state")
                            if (curStateFile.exists()) return curStateFile
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to scan cooling device nodes", e)
        }
        val fallback = File("/sys/devices/virtual/thermal/cooling_device34/cur_state")
        return if (fallback.exists()) fallback else null
    }

    fun isSupported(): Boolean = fanPwmNode != null

    private fun getPrefs(context: Context): SharedPreferences {
        val storageContext = if (context.isDeviceProtectedStorage) {
            context
        } else {
            context.createDeviceProtectedStorageContext()
        }
        return PreferenceManager.getDefaultSharedPreferences(storageContext)
    }

    fun getCurrentProfile(context: Context): FanProfile {
        val id = getPrefs(context).getString(PREF_FAN_PROFILE, FanProfile.AUTO.id)
        val profile = FanProfile.fromId(id)
        _currentProfileFlow.value = profile
        return profile
    }

    fun setProfile(context: Context, profile: FanProfile) {
        getPrefs(context).edit().putString(PREF_FAN_PROFILE, profile.id).apply()
        _currentProfileFlow.value = profile

        applyProfile(profile)
        FanService.updateServiceState(context, profile)

        // Request QS tile update
        try {
            TileService.requestListeningState(
                context,
                ComponentName(context, FanTileService::class.java),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request tile update", e)
        }
    }

    fun cycleProfile(context: Context): FanProfile {
        val current = getCurrentProfile(context)
        val profiles = FanProfile.entries
        val nextIndex = (profiles.indexOf(current) + 1) % profiles.size
        val next = profiles[nextIndex]
        setProfile(context, next)
        return next
    }

    private fun applyProfile(profile: FanProfile) {
        // Sync with kernel thermal cooling device
        fanCoolingDeviceNode?.let { cdevNode ->
            val cdevState = when (profile) {
                FanProfile.AUTO -> 0
                FanProfile.QUIET -> 1   // State 1: 40 PWM (~15%)
                FanProfile.MEDIUM -> 5  // State 5: 100 PWM (~40%)
                FanProfile.HIGH -> 8    // State 8: 175 PWM (~70%)
                FanProfile.MAX -> 8     // State 8: 175 PWM + override
            }
            NodeUtils.write(cdevNode.absolutePath, cdevState.toString())
        }

        // Set hardware PWM duty cycle
        fanPwmNode?.let { pwmNode ->
            val targetPwm = if (profile == FanProfile.AUTO) 0 else profile.pwm
            NodeUtils.write(pwmNode.absolutePath, targetPwm.toString())
        }
    }

    fun pauseForScreenOff() {
        fanCoolingDeviceNode?.let { cdevNode ->
            NodeUtils.write(cdevNode.absolutePath, "0")
        }
        fanPwmNode?.let { pwmNode ->
            NodeUtils.write(pwmNode.absolutePath, "0")
        }
    }

    fun resumeForScreenOn(context: Context) {
        val profile = getCurrentProfile(context)
        applyProfile(profile)
    }

    fun getActualPwm(): Int {
        val pwmNode = fanPwmNode ?: return 0
        return NodeUtils.read(pwmNode.absolutePath)?.toIntOrNull() ?: 0
    }

    fun getSocTemperature(): Float? {
        val tempNode = File(PRIMARY_THERMAL_ZONE)
        if (!tempNode.exists()) return null
        val raw = NodeUtils.read(tempNode.absolutePath)?.toFloatOrNull() ?: return null
        return raw / 1000f
    }

    fun restoreOnBoot(context: Context) {
        val profile = getCurrentProfile(context)
        Log.i(TAG, "Restoring fan profile on boot: ${profile.id}")
        applyProfile(profile)
        FanService.updateServiceState(context, profile)
    }
}
