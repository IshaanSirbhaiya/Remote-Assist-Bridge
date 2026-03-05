package com.yourcompany.remoteassistbridge

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Intent
import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.accessibilityservice.GestureDescription

class RemoteAccessibilityService : AccessibilityService() {

    companion object {
        var instance: RemoteAccessibilityService? = null
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}

    override fun onInterrupt() {}

    fun stopTeamViewer() {
        val launchIntent = packageManager.getLaunchIntentForPackage("com.teamviewer.quicksupport.market")?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (launchIntent != null) {
            applicationContext.startActivity(launchIntent)

            // First - press back to open popup
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                performGlobalAction(GLOBAL_ACTION_BACK)

                // Second - tap Close button by exact coordinates
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    android.util.Log.d("RemoteBridge", "Tapping Close at 780, 1331")

                    val path = android.graphics.Path()
                    path.moveTo(780f, 1331f)
                    val gestureBuilder = android.accessibilityservice.GestureDescription.Builder()
                    gestureBuilder.addStroke(
                        android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 100)
                    )
                    dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription) {
                            android.util.Log.d("RemoteBridge", "Gesture completed!")
                        }
                        override fun onCancelled(gestureDescription: GestureDescription) {
                            android.util.Log.d("RemoteBridge", "Gesture cancelled!")
                        }
                    }, null)
                }, 2000)
            }, 3000)
        }
    }

    private fun findAndClickDisconnect(node: AccessibilityNodeInfo) {
        // First try to find exact "Close" button
        val closeNodes = node.findAccessibilityNodeInfosByText("Close")
        for (closeNode in closeNodes) {
            if (closeNode.isClickable) {
                android.util.Log.d("RemoteBridge", "Clicking Close button")
                closeNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
        }

        // Then try disconnect keywords but NOT cancel
        val keywords = listOf("Close","close","disconnect", "end session", "end", "stop")
        for (keyword in keywords) {
            val nodes = node.findAccessibilityNodeInfosByText(keyword)
            if (nodes.isNotEmpty()) {
                android.util.Log.d("RemoteBridge", "Clicking: ${nodes[0].text}")
                nodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
        }

        performGlobalAction(GLOBAL_ACTION_BACK)
    }
}