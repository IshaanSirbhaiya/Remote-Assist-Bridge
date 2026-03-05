package com.yourcompany.remoteassistbridge

import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.app.ActivityManager


class SplashtopController(private val context: Context) {

    private val teamviewerPackage = "com.teamviewer.quicksupport.market"

    fun startSession() {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(teamviewerPackage)
            if (launchIntent != null) {
                context.startActivity(launchIntent)
            } else {
                Toast.makeText(context, "TeamViewer QuickSupport not installed", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error launching TeamViewer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun stopSession() {
        val service = RemoteAccessibilityService.instance
        android.util.Log.d("RemoteBridge", "stopSession called, service: ${service != null}")
        if (service != null) {
            android.util.Log.d("RemoteBridge", "Calling stopTeamViewer...")
            service.stopTeamViewer()
        } else {
            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Toast.makeText(context, "Please enable RemoteAssistBridge accessibility service", Toast.LENGTH_LONG).show()
        }
    }
}