/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.ayn.fan

import android.app.PendingIntent
import android.content.Intent
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
        val intent = Intent(this, FanDialogActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (isLocked) {
            unlockAndRun {
                startActivityAndCollapse(pendingIntent)
            }
        } else {
            startActivityAndCollapse(pendingIntent)
        }
    }

    private fun updateTile(currentProfile: FanProfile = FanController.getCurrentProfile(this)) {
        val qsTile = qsTile ?: return

        val profileName = getString(currentProfile.titleRes)
        qsTile.icon = Icon.createWithResource(this, R.drawable.ic_fan)
        qsTile.label = getString(R.string.fan_tile_title)

        val sub = if (currentProfile == FanProfile.AUTO) {
            profileName
        } else {
            "$profileName • ${currentProfile.percentage}%"
        }
        qsTile.subtitle = sub
        qsTile.stateDescription = sub
        qsTile.contentDescription = "${qsTile.label}, $sub"
        qsTile.state = Tile.STATE_ACTIVE

        qsTile.updateTile()
    }
}
