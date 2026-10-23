/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.ayn.fan

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FanCurve {

    const val PREF_CUSTOM_CURVE = "fan_custom_curve"

    // Equalizer bands (temperature steps in °C)
    val TEMPERATURE_STEPS = listOf(45, 55, 65, 75, 85)

    // Default fan speed percentage for each band
    val DEFAULT_SPEEDS = listOf(0, 30, 50, 70, 100)

    private val _curveFlow = MutableStateFlow(DEFAULT_SPEEDS)
    val curveFlow: StateFlow<List<Int>> = _curveFlow.asStateFlow()

    private fun getPrefs(context: Context): SharedPreferences {
        val storageContext = if (context.isDeviceProtectedStorage) {
            context
        } else {
            context.createDeviceProtectedStorageContext()
        }
        return PreferenceManager.getDefaultSharedPreferences(storageContext)
    }

    fun getCurve(context: Context): List<Int> {
        val raw = getPrefs(context).getString(PREF_CUSTOM_CURVE, null) ?: return DEFAULT_SPEEDS
        val parsed = raw.split(",").mapNotNull { it.trim().toIntOrNull() }
        val speeds = if (parsed.size == TEMPERATURE_STEPS.size) {
            parsed.map { it.coerceIn(0, 100) }
        } else {
            DEFAULT_SPEEDS
        }
        _curveFlow.value = speeds
        return speeds
    }

    fun setCurve(context: Context, speeds: List<Int>) {
        val validated = speeds.map { it.coerceIn(0, 100) }
        val serialized = validated.joinToString(",")
        getPrefs(context).edit().putString(PREF_CUSTOM_CURVE, serialized).apply()
        _curveFlow.value = validated
    }

    fun setPoint(context: Context, index: Int, speedPercent: Int) {
        val current = getCurve(context).toMutableList()
        if (index in current.indices) {
            current[index] = speedPercent.coerceIn(0, 100)
            setCurve(context, current)
        }
    }

    fun resetToDefault(context: Context): List<Int> {
        setCurve(context, DEFAULT_SPEEDS)
        return DEFAULT_SPEEDS
    }

    /**
     * Evaluates the custom fan curve for a given SoC temperature in Celsius.
     * Uses piecewise linear interpolation between defined temperature steps.
     * Returns target fan speed in percent (0..100).
     */
    fun evaluate(speeds: List<Int>, tempC: Float): Int {
        if (speeds.size != TEMPERATURE_STEPS.size) return 0

        if (tempC <= TEMPERATURE_STEPS.first()) {
            return speeds.first()
        }

        if (tempC >= TEMPERATURE_STEPS.last()) {
            return speeds.last()
        }

        for (i in 0 until TEMPERATURE_STEPS.size - 1) {
            val tLow = TEMPERATURE_STEPS[i].toFloat()
            val tHigh = TEMPERATURE_STEPS[i + 1].toFloat()
            if (tempC in tLow..tHigh) {
                val ratio = (tempC - tLow) / (tHigh - tLow)
                val sLow = speeds[i].toFloat()
                val sHigh = speeds[i + 1].toFloat()
                val interpolated = sLow + ratio * (sHigh - sLow)
                return interpolated.toInt().coerceIn(0, 100)
            }
        }

        return speeds.last()
    }
}
