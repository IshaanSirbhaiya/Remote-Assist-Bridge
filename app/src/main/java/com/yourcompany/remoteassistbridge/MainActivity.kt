package com.yourcompany.remoteassistbridge

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.yourcompany.remoteassistbridge.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var usbService: UsbService
    private lateinit var stateMachine: StateMachine
    private lateinit var splashtopController: SplashtopController

    private val ACTION_USB_PERMISSION = "com.yourcompany.remoteassistbridge.USB_PERMISSION"

    companion object {
        private const val CALL_PERMISSION_REQUEST = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize components
        usbService = UsbService(this, ::onButtonPressed) { isConnected ->
            runOnUiThread { updateUI() }
        }

        stateMachine = StateMachine(::onStateChanged)
        splashtopController = SplashtopController(this)

        // Request call permission
        checkCallPermission()

        // Register USB receiver
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED)
        }

        // Check for USB device on startup
        checkBlePermissions()
        checkUsbDevice()

        updateUI()
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                android.util.Log.d("RemoteBridge", "BLE permissions granted, starting scan...")
                usbService.connect()
            } else {
                android.util.Log.d("RemoteBridge", "BLE permissions DENIED!")
            }
        }
    }

    private fun checkBlePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                ),
                101
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                101
            )
        }
    }

    private fun checkCallPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CALL_PHONE),
                CALL_PERMISSION_REQUEST
            )
        }
    }


    // ✅ UPDATED: Pick first device that actually probes as serial
    private fun checkUsbDevice() {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        val device = usbManager.deviceList.values.firstOrNull { dev ->
            UsbSerialProber.getDefaultProber().probeDevice(dev) != null
        }

        if (device != null) {
            android.util.Log.d("USB", "checkUsbDevice picked VID=${device.vendorId} PID=${device.productId}")
            requestUsbPermission(device)
        } else {
            android.util.Log.e("USB", "No compatible USB serial device found")
        }
    }

    private fun requestUsbPermission(device: UsbDevice) {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val permissionIntent = PendingIntent.getBroadcast(
            this, 0,
            Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_IMMUTABLE
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    android.util.Log.d("USB", "PERMISSION intent received")
                    Toast.makeText(this@MainActivity, "USB permission result", Toast.LENGTH_SHORT).show()
                    synchronized(this) {
                        val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }

                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.let {
                                android.util.Log.d("USB", "Permission granted VID=${it.vendorId} PID=${it.productId}")
                                usbService.connect(it)
                                updateUI() // ✅ immediate UI refresh
                            }
                        } else {
                            Toast.makeText(context, "USB Permission Denied", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }

                    if (device == null) return

                    android.util.Log.d("USB", "ATTACHED VID=${device.vendorId} PID=${device.productId}")
                    Toast.makeText(this@MainActivity, "USB attached", Toast.LENGTH_SHORT).show()

                    requestUsbPermission(device)
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    usbService.disconnect()
                    updateUI()
                }
            }
        }
    }

    private fun onButtonPressed(button: String) {
        runOnUiThread {
            when (button) {
                "START" -> handleStart()
                "CONFIRM" -> handleConfirm()
                "STOP" -> handleStop()
            }
        }
    }

    private fun handleStart() {
        stateMachine.onStartPressed()
        makePhoneCall()
    }

    private fun handleConfirm() {
        if (stateMachine.onConfirmPressed()) {
            splashtopController.startSession()
        }
    }

    private fun handleStop() {
        stateMachine.forceStop()
        splashtopController.stopSession()
    }
    private fun makePhoneCall() {
        val parentNumber = "tel:+REDACTED_PHONE" // Load from config

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED) {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse(parentNumber)
            }
            startActivity(callIntent)
        } else {
            Toast.makeText(this, "Call permission required", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onStateChanged(newState: StateMachine.State) {
        updateUI()
    }

    private fun updateUI() {
        binding.apply {
            tvStatus.text = "Status: ${stateMachine.getCurrentState()}"
            tvUsbStatus.text = if (usbService.isConnected()) "Dongle Connected ✓" else "Waiting for dongle..."
        }

        android.util.Log.d("RemoteBridge", "USB Connected: ${usbService.isConnected()}")
        android.util.Log.d("RemoteBridge", "State: ${stateMachine.getCurrentState()}")
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
        usbService.disconnect()
    }
}