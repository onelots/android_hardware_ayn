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
import org.lineageos.settings.ayn.R

class FanTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
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
        qsTile.label = getString(R.string.fan_tile_label_format, profileName)
        qsTile.subtitle = if (currentProfile == FanProfile.AUTO) {
            profileName
        } else {
            "${currentProfile.percentage}%"
        }
        qsTile.contentDescription = qsTile.label

        qsTile.state = if (currentProfile == FanProfile.AUTO) {
            Tile.STATE_INACTIVE
        } else {
            Tile.STATE_ACTIVE
        }

        qsTile.updateTile()
    }
}
