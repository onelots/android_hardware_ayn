/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.ayn.fan

import android.content.Context
import android.content.SharedPreferences
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

    private val _currentProfileFlow = MutableStateFlow(FanProfile.AUTO)
    val currentProfileFlow: StateFlow<FanProfile> = _currentProfileFlow.asStateFlow()

    private fun findFanPwmNode(): File? {
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
        val fallback = File("/sys/devices/platform/soc/c42d000.qcom,spmi/spmi-0/0-01/c42d000.qcom,spmi:qcom,pm8550@1:pwm-fan@0/hwmon/hwmon0/pwm1")
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
        val pwmNode = fanPwmNode
        if (pwmNode == null) {
            Log.w(TAG, "Fan PWM node not available")
            return
        }

        if (profile == FanProfile.AUTO) {
            NodeUtils.write(pwmNode.absolutePath, "0")
        } else {
            NodeUtils.write(pwmNode.absolutePath, profile.pwm.toString())
        }
    }

    fun enforcePwm(pwm: Int) {
        val pwmNode = fanPwmNode ?: return
        NodeUtils.write(pwmNode.absolutePath, pwm.toString())
    }

    fun pauseForScreenOff() {
        val pwmNode = fanPwmNode ?: return
        NodeUtils.write(pwmNode.absolutePath, "0")
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
