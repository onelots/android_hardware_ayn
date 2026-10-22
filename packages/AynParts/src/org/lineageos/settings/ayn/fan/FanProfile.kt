/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.ayn.fan

import androidx.annotation.StringRes
import org.lineageos.settings.ayn.R

enum class FanProfile(
    val id: String,
    val pwm: Int,
    val percentage: Int,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
) {
    AUTO("auto", 0, 0, R.string.fan_profile_auto, R.string.fan_profile_auto_desc),
    QUIET("quiet", 38, 15, R.string.fan_profile_quiet, R.string.fan_profile_quiet_desc),
    MEDIUM("medium", 102, 40, R.string.fan_profile_medium, R.string.fan_profile_medium_desc),
    HIGH("high", 178, 70, R.string.fan_profile_high, R.string.fan_profile_high_desc),
    MAX("max", 255, 100, R.string.fan_profile_max, R.string.fan_profile_max_desc);

    companion object {
        fun fromId(id: String?): FanProfile {
            return entries.find { it.id == id } ?: AUTO
        }
    }
}
