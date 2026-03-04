package com.yourcompany.remoteassistbridge

import android.content.Context
import android.content.Intent
import android.widget.Toast

class SplashtopController(private val context: Context) {

    private val splashtopPackage = "com.splashtop.remote.pad.v2"

    fun startSession() {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(splashtopPackage)
            if (launchIntent != null) {
                context.startActivity(launchIntent)
                Toast.makeText(context, "Starting Splashtop session...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Splashtop not installed", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error launching Splashtop: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun stopSession() {
        // Splashtop doesn't have a public API to programmatically end sessions
        // The user will need to manually disconnect
        Toast.makeText(context, "Please disconnect Splashtop manually", Toast.LENGTH_SHORT).show()
    }
}