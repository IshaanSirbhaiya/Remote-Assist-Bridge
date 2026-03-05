package com.yourcompany.remoteassistbridge

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import java.util.UUID

class UsbService(
    private val context: Context,
    private val onButtonPressed: (String) -> Unit,
    private val onConnectionChanged: (Boolean) -> Unit = {}
) {
    private val SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    private val CHAR_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var bluetoothGatt: BluetoothGatt? = null
    private var connected = false
    private val handler = Handler(Looper.getMainLooper())

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
                    result.device.name else null
            } else result.device.name

            android.util.Log.d("RemoteBridge", "Found BLE device: $name | Address: ${result.device.address}")

            if (name == "RemoteAssistBridge") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                        bluetoothAdapter?.bluetoothLeScanner?.stopScan(this)
                    }
                } else {
                    bluetoothAdapter?.bluetoothLeScanner?.stopScan(this)
                }
                connectToDevice(result.device)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connected = true
                android.util.Log.d("RemoteBridge", "BLE Connected!")
                handler.post { onConnectionChanged(true) }
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    gatt.discoverServices()
                }
            } else {
                connected = false
                android.util.Log.d("RemoteBridge", "BLE Disconnected, rescanning...")
                handler.post { onConnectionChanged(false) }
                handler.postDelayed({ startScan() }, 2000)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            android.util.Log.d("RemoteBridge", "Services discovered, status: $status")  
            val characteristic = gatt.getService(SERVICE_UUID)?.getCharacteristic(CHAR_UUID)
            android.util.Log.d("RemoteBridge", "Characteristic found: ${characteristic != null}")
            characteristic?.let {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    gatt.setCharacteristicNotification(it, true)
                    val descriptor = it.getDescriptor(CCCD_UUID)
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val command = characteristic.getStringValue(0)?.trim() ?: return
            android.util.Log.d("RemoteBridge", "BLE Command: $command")
            handler.post {
                when (command) {
                    "CMD:CONNECT" -> onButtonPressed("START")
                    "CMD:CONFIRM" -> onButtonPressed("CONFIRM")
                    "CMD:STOP" -> onButtonPressed("STOP")
                }
            }
        }
    }

    fun connect(device: Any? = null) {
        android.util.Log.d("RemoteBridge", "Starting BLE scan...")
        startScan()
    }

    private fun startScan() {
        handler.postDelayed({
            android.util.Log.d("RemoteBridge", "Scanning for BLE devices...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothAdapter?.bluetoothLeScanner?.startScan(scanCallback)
                } else {
                    android.util.Log.d("RemoteBridge", "BLUETOOTH_SCAN permission not granted!")
                }
            } else {
                bluetoothAdapter?.bluetoothLeScanner?.startScan(scanCallback)
            }
        }, 1000)
    }

    private fun connectToDevice(device: BluetoothDevice) {
        android.util.Log.d("RemoteBridge", "Connecting to device...")
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
        }
    }

    fun isConnected() = connected

    fun disconnect() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        }
        bluetoothGatt = null
        connected = false
    }
}