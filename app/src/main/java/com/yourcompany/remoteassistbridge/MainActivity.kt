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
        usbService = UsbService(this, ::onButtonPressed)
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
            registerReceiver(usbReceiver, filter)
        }

        // Check for USB device on startup
        checkUsbDevice()

        updateUI()
        // TEST BUTTONS - Remove when hardware ready
        binding.btnTestStart.setOnClickListener {
            onButtonPressed("START")
        }
        binding.btnTestConfirm.setOnClickListener {
            onButtonPressed("CONFIRM")
        }
        binding.btnTestStop.setOnClickListener {
            onButtonPressed("STOP")
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

    private fun checkUsbDevice() {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList = usbManager.deviceList

        if (deviceList.isNotEmpty()) {
            val device = deviceList.values.first()
            requestUsbPermission(device)
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
                    synchronized(this) {
                        val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }

                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.let {
                                usbService.connect(it)
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
                    device?.let { requestUsbPermission(it) }
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
    }

    private fun handleConfirm() {
        if (stateMachine.onConfirmPressed()) {
            // Start Splashtop AND initiate call
            splashtopController.startSession()
            makePhoneCall()
        }
    }

    private fun handleStop() {
        stateMachine.onStopPressed()
        splashtopController.stopSession()
    }

    private fun makePhoneCall() {
        // TODO: Replace with actual parent phone number
        // Consider storing this in SharedPreferences during setup
        val parentNumber = "tel:+1234567890" // Load from config

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
            tvUsbStatus.text = if (usbService.isConnected()) "Dongle Connected" else "Waiting for dongle..."
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
        usbService.disconnect()
    }
}