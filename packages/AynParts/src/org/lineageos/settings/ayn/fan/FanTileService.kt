/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.ayn.fan

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.lineageos.settings.ayn.R

class FanTileService : TileService() {

    private var listeningScope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        listeningScope?.cancel()
        val scope = CoroutineScope(Dispatchers.Main + Job())
        listeningScope = scope
        scope.launch {
            FanController.currentProfileFlow.collect { profile ->
                updateTile(profile)
            }
        }
        updateTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        listeningScope?.cancel()
        listeningScope = null
    }

    override fun onClick() {
        super.onClick()
        val next = FanController.cycleProfile(this)
        updateTile(next)
    }

    private fun updateTile(currentProfile: FanProfile = FanController.getCurrentProfile(this)) {
        val qsTile = qsTile ?: return

        qsTile.icon = Icon.createWithResource(this, R.drawable.ic_fan)
        qsTile.label = getString(R.string.fan_tile_title)

        val sub = when (currentProfile) {
            FanProfile.QUIET -> getString(R.string.fan_tile_sub_quiet)
            FanProfile.MEDIUM -> getString(R.string.fan_tile_sub_medium)
            FanProfile.HIGH -> getString(R.string.fan_tile_sub_high)
            FanProfile.MAX -> getString(R.string.fan_tile_sub_max)
            else -> getString(R.string.fan_tile_sub_auto)
        }
        qsTile.subtitle = sub

        val stateDesc = when (currentProfile) {
            FanProfile.QUIET,
            FanProfile.MEDIUM,
            FanProfile.HIGH,
            FanProfile.MAX -> "${getString(currentProfile.titleRes)}, ${currentProfile.percentage}%"
            else -> getString(R.string.fan_profile_auto)
        }
        qsTile.stateDescription = stateDesc
        qsTile.contentDescription = "${qsTile.label}, $sub"
        qsTile.state = Tile.STATE_ACTIVE

        qsTile.updateTile()
    }
}
