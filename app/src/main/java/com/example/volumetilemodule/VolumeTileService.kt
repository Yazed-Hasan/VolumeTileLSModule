package com.example.volumetilemodule

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class VolumeTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        // Update tile state to active so it looks clickable
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = "Volume"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        
        if (isLocked) {
            unlockAndRun {
                openVolumePanel()
            }
        } else {
            openVolumePanel()
        }
    }

    private fun openVolumePanel() {
        // Open the volume panel
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // This flag forces the system volume UI to show up
        audioManager.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)

        // Close the Quick Settings panel
        sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
    }
}