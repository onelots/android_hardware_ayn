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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
        scope.launch {
            while (isActive) {
                delay(2500)
                if (FanController.getCurrentProfile(this@FanTileService) == FanProfile.CUSTOM) {
                    updateTile(FanProfile.CUSTOM)
                }
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

        val profileName = getString(currentProfile.titleRes)
        qsTile.icon = Icon.createWithResource(this, R.drawable.ic_fan)
        qsTile.label = getString(R.string.fan_tile_title)

        val sub = when (currentProfile) {
            FanProfile.AUTO -> profileName
            FanProfile.CUSTOM -> {
                val actual = (FanController.getActualPwm() * 100 / 255)
                "$profileName • $actual%"
            }
            else -> "$profileName • ${currentProfile.percentage}%"
        }
        qsTile.subtitle = sub
        qsTile.stateDescription = sub
        qsTile.contentDescription = "${qsTile.label}, $sub"
        qsTile.state = Tile.STATE_ACTIVE

        qsTile.updateTile()
    }
}
