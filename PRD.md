# PRD — Remote Assist Bridge (Complete Technical Context)

> **Use this document to give any AI assistant (Claude, ChatGPT, etc.) or human developer full context on the project. Paste it at the start of a new chat to continue working immediately.**

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Problem Statement](#2-problem-statement)
3. [Solution Architecture](#3-solution-architecture)
4. [System Components Overview](#4-system-components-overview)
5. [Repository Structure](#5-repository-structure)
6. [Complete Source Code](#6-complete-source-code)
7. [BLE Communication Protocol](#7-ble-communication-protocol)
8. [State Machine Design](#8-state-machine-design)
9. [Android App — Deep Technical Breakdown](#9-android-app--deep-technical-breakdown)
10. [Arduino Firmware — Deep Technical Breakdown](#10-arduino-firmware--deep-technical-breakdown)
11. [Accessibility Service Architecture](#11-accessibility-service-architecture)
12. [Build System & Dependencies](#12-build-system--dependencies)
13. [Hardware Wiring & Pin Configuration](#13-hardware-wiring--pin-configuration)
14. [Android Permissions Matrix](#14-android-permissions-matrix)
15. [UI Layout Specification](#15-ui-layout-specification)
16. [Configuration Parameters](#16-configuration-parameters)
17. [Data Flow — End to End](#17-data-flow--end-to-end)
18. [Known Issues & Technical Debt](#18-known-issues--technical-debt)
19. [Setup & Deployment Instructions](#19-setup--deployment-instructions)
20. [Testing Strategy](#20-testing-strategy)

---

## 1. Executive Summary

**Remote Assist Bridge** is a two-part system that enables elderly or non-technical users to request remote technical assistance by pressing physical buttons on an ESP32-based remote control. The ESP32 communicates over Bluetooth Low Energy (BLE) with an Android companion app that:

1. Automatically places a phone call to a caregiver.
2. Launches TeamViewer QuickSupport for a remote desktop session.
3. Can forcefully close TeamViewer when the session ends.

**GitHub Repository:** https://github.com/IshaanSirbhaiya/Idea

---

## 2. Problem Statement

Elderly users or people with limited technical knowledge often need remote assistance with their devices but struggle to:
- Navigate to the right app
- Initiate a remote support session themselves
- End a session when they want to

This project solves that with a **physical 3-button remote** (Connect / Confirm / Stop) that handles the entire workflow automatically.

---

## 3. Solution Architecture

```
┌────────────────────────────┐        BLE (NUS)        ┌─────────────────────────────┐
│   ESP32 Remote Control     │ ◄──────────────────────► │   Android Companion App     │
│                            │     CMD:CONNECT          │                             │
│  [CONNECT] GPIO 20        │     CMD:CONFIRM          │  UsbService.kt (BLE scan)   │
│  [CONFIRM] GPIO 21        │     CMD:STOP             │  MainActivity.kt (commands)  │
│  [STOP]    GPIO 10 (ISR)  │                          │  StateMachine.kt (states)    │
│                            │                          │  SplashtopController.kt      │
│  BLE Peripheral (NUS)      │                          │  RemoteAccessibilityService  │
│  Name: RemoteAssistBridge  │                          │                             │
└────────────────────────────┘                          │  ┌───────────┐ ┌──────────┐ │
                                                        │  │ Phone     │ │TeamViewer│ │
                                                        │  │ Dialer    │ │QuickSup. │ │
                                                        │  └───────────┘ └──────────┘ │
                                                        └─────────────────────────────┘
```

**Communication:** Unidirectional — ESP32 (peripheral) → Android (central) via BLE Notify.

---

## 4. System Components Overview

| Component | Technology | Role |
|---|---|---|
| ESP32 Firmware | C++ / Arduino Framework | Reads buttons, sends BLE commands |
| Android App | Kotlin / Android SDK | Receives BLE commands, manages sessions |
| BLE Protocol | Nordic UART Service (NUS) | Wireless communication layer |
| TeamViewer QuickSupport | 3rd-party APK | Remote desktop session |
| Accessibility Service | Android API | Programmatically closes TeamViewer |
| Phone Dialer | Android Intent | Auto-calls caregiver |

---

## 5. Repository Structure

```
Idea/
├── README.md                                 # Project overview
├── REQUIREMENTS.md                           # Functional & non-functional requirements
├── PRD.md                                    # THIS DOCUMENT — complete technical context
├── .gitignore
├── build.gradle.kts                          # Root Gradle build (Kotlin DSL)
├── settings.gradle.kts                       # Project settings, includes :app module
├── gradle.properties                         # JVM args, AndroidX config
├── gradlew / gradlew.bat                     # Gradle wrapper scripts
│
├── gradle/
│   ├── libs.versions.toml                    # Version catalog (AGP 9.0.1, Kotlin 2.2.10)
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
│
├── app/
│   ├── build.gradle.kts                      # App-level build config
│   ├── proguard-rules.pro
│   ├── .gitignore
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml           # Permissions, activities, services
│       │   ├── java/com/yourcompany/remoteassistbridge/
│       │   │   ├── MainActivity.kt           # Entry point — BLE/USB handling, UI, commands
│       │   │   ├── UsbService.kt             # BLE scanning, connection, notification listener
│       │   │   ├── StateMachine.kt           # 3-state machine (IDLE → START_PRESSED → SESSION_ACTIVE)
│       │   │   ├── SplashtopController.kt    # TeamViewer launch & stop via accessibility
│       │   │   ├── RemoteAccessibilityService.kt  # Force-close TeamViewer via gestures
│       │   │   └── ui/theme/
│       │   │       ├── Color.kt              # Material3 color definitions
│       │   │       ├── Theme.kt              # Dynamic theming (Material You)
│       │   │       └── Type.kt               # Typography definitions
│       │   └── res/
│       │       ├── layout/
│       │       │   ├── activity_main.xml     # Main UI: title, status, USB status, instructions
│       │       │   └── activity_main_ORIGINAL.xml  # Backup of original layout
│       │       ├── xml/
│       │       │   ├── accessibility_service_config.xml  # Accessibility service declaration
│       │       │   ├── device_filter.xml     # USB device VID/PID filter
│       │       │   ├── backup_rules.xml
│       │       │   └── data_extraction_rules.xml
│       │       ├── values/
│       │       │   ├── colors.xml            # Color resources
│       │       │   ├── strings.xml           # App name: "Remote Assist Bridge"
│       │       │   └── themes.xml            # Material Light NoActionBar theme
│       │       ├── drawable/                 # Launcher icon vectors
│       │       └── mipmap-*/                 # Launcher icons (various densities)
│       ├── androidTest/                      # Instrumented tests
│       └── test/                             # Unit tests
│
└── arduino codes/
    └── RemoteControl/
        ├── RemoteControl.ino                 # Main Arduino sketch
        ├── ble_cmd.cpp                       # BLE init, send, stop-check
        ├── ble_cmd.h                         # BLE function declarations
        ├── buttons.cpp                       # Button init, debounce, ISR
        └── buttons.h                         # Pin definitions, constants
```

---

## 6. Complete Source Code

### 6.1 Arduino Firmware

#### `RemoteControl.ino` — Main Sketch

```cpp
#include "buttons.h"
#include "ble_cmd.h"

static bool connect_last = HIGH;
static bool confirm_last = HIGH;
static bool stop_last = HIGH;

void setup() {
  buttons_init();
  ble_init();
  attachInterrupt(digitalPinToInterrupt(BTN_STOP), stop_ISR, FALLING);
}

void loop() {
  bool connect_now = digitalRead(BTN_CONNECT);
  bool confirm_now = digitalRead(BTN_CONFIRM);
  bool stop_now = digitalRead(BTN_STOP);

  if (connect_now == LOW && connect_last == HIGH) {
    Serial.println("CONNECT pressed!");
    ble_send("CMD:CONNECT");
  }
  if (confirm_now == LOW && confirm_last == HIGH) {
    Serial.println("CONFIRM pressed!");
    ble_send("CMD:CONFIRM");
  }
  if (stop_now == LOW && stop_last == HIGH) {
    Serial.println("STOP pressed!");
    ble_send("CMD:STOP");
  }

  connect_last = connect_now;
  confirm_last = confirm_now;
  stop_last = stop_now;

  check_stop();
}
```

**How it works:**
- `setup()`: Initializes buttons with pull-ups, starts BLE advertising, attaches a FALLING-edge interrupt on the Stop button.
- `loop()`: Polls Connect and Confirm buttons for HIGH→LOW transitions (press detection). Stop is additionally handled via hardware interrupt for immediate response.
- Each press sends the corresponding `CMD:*` string over BLE.

#### `ble_cmd.h`

```cpp
#ifndef BLE_CMD_H
#define BLE_CMD_H

void ble_init();
void ble_send(const char* cmd);
void check_stop();

#endif
```

#### `ble_cmd.cpp` — BLE Peripheral Implementation

```cpp
#include "ble_cmd.h"
#include "buttons.h"
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

#define SERVICE_UUID        "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"

BLEServer* pServer = NULL;
BLECharacteristic* pCharacteristic = NULL;
bool deviceConnected = false;

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) {
    deviceConnected = true;
    Serial.println("Device connected!");
  }
  void onDisconnect(BLEServer* pServer) {
    deviceConnected = false;
    Serial.println("Restarting advertising...");
    delay(500);
    BLEDevice::startAdvertising();
    Serial.println("Advertising restarted!");
  }
};

void ble_init() {
  Serial.begin(115200);
  Serial.println("Starting BLE...");

  BLEDevice::init("RemoteAssistBridge");
  BLEDevice::setPower(ESP_PWR_LVL_P9);

  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new ServerCallbacks());

  BLEService* pService = pServer->createService(SERVICE_UUID);
  pCharacteristic = pService->createCharacteristic(
    CHARACTERISTIC_UUID,
    BLECharacteristic::PROPERTY_NOTIFY
  );
  pCharacteristic->addDescriptor(new BLE2902());
  pService->start();

  BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);
  pAdvertising->setMaxPreferred(0x12);
  BLEDevice::startAdvertising();

  Serial.println("BLE advertising started!");
}

void ble_send(const char* cmd) {
  static unsigned long last_send = 0;
  unsigned long now = millis();
  if (now - last_send < 300) return;  // Rate limit: 300ms between sends
  last_send = now;

  if (deviceConnected) {
    Serial.print("Sending: ");
    Serial.println(cmd);
    pCharacteristic->setValue(cmd);
    pCharacteristic->notify();
  }
}

void check_stop() {
  if (stop_triggered) {
    stop_triggered = false;
    ble_send("CMD:STOP");
  }
}
```

**Key details:**
- BLE device name: `"RemoteAssistBridge"` — the Android app scans for exactly this name.
- Uses Nordic UART Service UUIDs.
- Notify-only characteristic (no read/write from central).
- TX power set to max (`ESP_PWR_LVL_P9`) for range.
- 300ms rate limit prevents duplicate sends from button bounce.
- On disconnect, automatically restarts advertising after 500ms.
- `check_stop()` is called every loop iteration to handle interrupt-triggered stops.

#### `buttons.h`

```cpp
#ifndef BUTTONS_H
#define BUTTONS_H

#include <Arduino.h>

#define BTN_CONNECT  20
#define BTN_CONFIRM  21
#define BTN_STOP     10
#define DEBOUNCE_MS  50

extern volatile bool stop_triggered;

void buttons_init();
bool button_pressed(int pin);
void IRAM_ATTR stop_ISR();

#endif
```

#### `buttons.cpp` — Button Hardware Abstraction

```cpp
#include "buttons.h"
#include <Arduino.h>

static unsigned long last_press[3] = {0, 0, 0};
static bool last_state[3] = {HIGH, HIGH, HIGH};
volatile bool stop_triggered = false;

void buttons_init() {
  pinMode(BTN_CONNECT, INPUT_PULLUP);
  pinMode(BTN_CONFIRM, INPUT_PULLUP);
  pinMode(BTN_STOP,    INPUT_PULLUP);
}

int pinIndex(int pin) {
  if (pin == BTN_CONNECT) return 0;
  if (pin == BTN_CONFIRM) return 1;
  return 2;
}

bool button_pressed(int pin) {
  int i = pinIndex(pin);
  bool current = digitalRead(pin);
  if (current == LOW && last_state[i] == HIGH) {
    unsigned long now = millis();
    if (now - last_press[i] > DEBOUNCE_MS) {
      last_press[i] = now;
      last_state[i] = LOW;
      return true;
    }
  }
  if (current == HIGH) last_state[i] = HIGH;
  return false;
}

void IRAM_ATTR stop_ISR() {
  stop_triggered = true;
}
```

**Key details:**
- All buttons use internal pull-ups (`INPUT_PULLUP`), active LOW.
- Software debounce: 50ms per button.
- `stop_ISR()` is marked `IRAM_ATTR` for ESP32 ISR placement in IRAM for fast execution.
- `stop_triggered` is `volatile` since it's shared between ISR and main loop.
- `button_pressed()` utility function is defined but the main sketch uses direct `digitalRead()` with manual edge detection instead (potential cleanup target).

---

### 6.2 Android App — Kotlin Source

#### `MainActivity.kt` — Entry Point

```kotlin
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
                usbService.connect()
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

    private fun checkUsbDevice() {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val device = usbManager.deviceList.values.firstOrNull { dev ->
            UsbSerialProber.getDefaultProber().probeDevice(dev) != null
        }
        if (device != null) {
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
                                updateUI()
                            }
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
        val parentNumber = "tel:${BuildConfig.CAREGIVER_PHONE}" // set caregiverPhone in local.properties
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED) {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse(parentNumber)
            }
            startActivity(callIntent)
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
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
        usbService.disconnect()
    }
}
```

**Key behaviors:**
- On launch: requests BLE + call permissions, registers USB broadcast receiver, starts BLE scan, checks for USB serial devices.
- `onButtonPressed()` is a callback from `UsbService` (which is actually the BLE service, despite the name).
- `CMD:CONNECT` → maps to `"START"` → calls caregiver phone number + changes state.
- `CMD:CONFIRM` → maps to `"CONFIRM"` → launches TeamViewer.
- `CMD:STOP` → maps to `"STOP"` → force-closes TeamViewer + resets state.
- UI updates show BLE connection status and current state machine state.

**IMPORTANT NOTE:** The class `UsbService` is misleadingly named — it actually implements **BLE communication**, not USB. The USB code in `MainActivity` is a legacy/fallback path that also exists but the primary communication is BLE.

---

#### `UsbService.kt` — BLE Central (Scanner & GATT Client)

```kotlin
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
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED) result.device.name else null
            } else result.device.name

            if (name == "RemoteAssistBridge") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                        == PackageManager.PERMISSION_GRANTED) {
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
                handler.post { onConnectionChanged(true) }
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    gatt.discoverServices()
                }
            } else {
                connected = false
                handler.post { onConnectionChanged(false) }
                handler.postDelayed({ startScan() }, 2000)  // Auto-reconnect after 2 seconds
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val characteristic = gatt.getService(SERVICE_UUID)?.getCharacteristic(CHAR_UUID)
            characteristic?.let {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    gatt.setCharacteristicNotification(it, true)
                    val descriptor = it.getDescriptor(CCCD_UUID)
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val command = characteristic.getStringValue(0)?.trim() ?: return
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
        startScan()
    }

    private fun startScan() {
        handler.postDelayed({
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED) {
                    bluetoothAdapter?.bluetoothLeScanner?.startScan(scanCallback)
                }
            } else {
                bluetoothAdapter?.bluetoothLeScanner?.startScan(scanCallback)
            }
        }, 1000)  // 1-second delay before scanning
    }

    private fun connectToDevice(device: BluetoothDevice) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
        }
    }

    fun isConnected() = connected

    fun disconnect() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        }
        bluetoothGatt = null
        connected = false
    }
}
```

**BLE Flow:**
1. `connect()` → `startScan()` (1-second delay) → scans for all BLE devices.
2. `scanCallback` filters by name `"RemoteAssistBridge"`, stops scan, calls `connectToDevice()`.
3. GATT connection established → `onConnectionStateChange` → `discoverServices()`.
4. Services discovered → subscribes to NUS TX characteristic notifications (writes CCCD descriptor).
5. `onCharacteristicChanged()` receives command strings, maps them, and calls `onButtonPressed()`.
6. On disconnect → auto-retries scan after 2 seconds.

---

#### `StateMachine.kt` — State Management

```kotlin
package com.yourcompany.remoteassistbridge

class StateMachine(private val onStateChanged: (State) -> Unit) {

    enum class State {
        IDLE,
        START_PRESSED,
        SESSION_ACTIVE
    }

    private var currentState = State.IDLE

    fun onStartPressed() {
        currentState = State.START_PRESSED
        onStateChanged(currentState)
    }

    fun onConfirmPressed(): Boolean {
        currentState = State.SESSION_ACTIVE
        onStateChanged(currentState)
        return true
    }

    fun forceStop() {
        currentState = State.IDLE
        onStateChanged(currentState)
    }

    fun onStopPressed() {
        currentState = State.IDLE
        onStateChanged(currentState)
    }

    fun getCurrentState() = currentState
}
```

**State transitions:**
```
IDLE ──(CMD:CONNECT)──► START_PRESSED ──(CMD:CONFIRM)──► SESSION_ACTIVE
  ▲                          │                                │
  └────────(CMD:STOP)────────┘────────────(CMD:STOP)──────────┘
```

---

#### `SplashtopController.kt` — TeamViewer Session Manager

```kotlin
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
        if (service != null) {
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
```

**Note:** The class is named `SplashtopController` (from an earlier design that considered Splashtop), but it actually manages **TeamViewer QuickSupport** (`com.teamviewer.quicksupport.market`).

---

#### `RemoteAccessibilityService.kt` — Force-Close TeamViewer

```kotlin
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
        val launchIntent = packageManager.getLaunchIntentForPackage(
            "com.teamviewer.quicksupport.market"
        )?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (launchIntent != null) {
            applicationContext.startActivity(launchIntent)

            // Step 1: Press BACK after 3 seconds to open the close popup
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                performGlobalAction(GLOBAL_ACTION_BACK)

                // Step 2: Tap "Close" button at exact screen coordinates after 2 more seconds
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    val path = android.graphics.Path()
                    path.moveTo(780f, 1331f)  // Hardcoded screen coordinates for Close button
                    val gestureBuilder = GestureDescription.Builder()
                    gestureBuilder.addStroke(
                        GestureDescription.StrokeDescription(path, 0, 100)
                    )
                    dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription) {}
                        override fun onCancelled(gestureDescription: GestureDescription) {}
                    }, null)
                }, 2000)
            }, 3000)
        }
    }

    private fun findAndClickDisconnect(node: AccessibilityNodeInfo) {
        val closeNodes = node.findAccessibilityNodeInfosByText("Close")
        for (closeNode in closeNodes) {
            if (closeNode.isClickable) {
                closeNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
        }

        val keywords = listOf("Close", "close", "disconnect", "end session", "end", "stop")
        for (keyword in keywords) {
            val nodes = node.findAccessibilityNodeInfosByText(keyword)
            if (nodes.isNotEmpty()) {
                nodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
        }

        performGlobalAction(GLOBAL_ACTION_BACK)
    }
}
```

**How TeamViewer is force-closed:**
1. Brings TeamViewer to foreground.
2. Waits 3 seconds, then presses BACK (triggers TeamViewer's "are you sure" dialog).
3. Waits 2 more seconds, then performs a gesture tap at coordinates (780, 1331) — the hardcoded position of the "Close" button.
4. `findAndClickDisconnect()` is defined as an alternative approach (searches by text) but is NOT currently called by `stopTeamViewer()`. This is a **known area for improvement**.

**WARNING:** The hardcoded coordinates (780, 1331) are device-specific and will break on different screen sizes/resolutions.

---

### 6.3 Android XML Resources

#### `AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.CALL_PHONE" />
    <uses-permission android:name="android.permission.KILL_BACKGROUND_PROCESSES" />
    <uses-feature android:name="android.hardware.telephony" android:required="false" />
    <uses-feature android:name="android.hardware.usb.host" />

    <queries>
        <package android:name="com.teamviewer.quicksupport.market" />
    </queries>

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.AppCompat.Light"
        tools:targetApi="31">

        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED" />
            </intent-filter>
            <meta-data
                android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED"
                android:resource="@xml/device_filter" />
        </activity>

        <service
            android:name=".RemoteAccessibilityService"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>
    </application>
</manifest>
```

#### `activity_main.xml` — Layout

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="24dp"
    android:gravity="center">

    <TextView android:id="@+id/tvTitle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Remote Assist Bridge"
        android:textSize="24sp"
        android:textStyle="bold"
        android:layout_marginBottom="32dp"/>

    <TextView android:id="@+id/tvUsbStatus"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Waiting for dongle..."
        android:textSize="16sp"
        android:layout_marginBottom="16dp"/>

    <TextView android:id="@+id/tvStatus"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Status: IDLE"
        android:textSize="18sp"
        android:textColor="@android:color/black"
        android:layout_marginBottom="24dp"/>

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Instructions:\n1. Press START button\n2. Press CONFIRM to begin session\n3. Press STOP to end"
        android:textSize="14sp"
        android:gravity="center"/>
</LinearLayout>
```

**View IDs used by ViewBinding:**
- `tvTitle` — App title display
- `tvUsbStatus` — Shows "Dongle Connected ✓" or "Waiting for dongle..."
- `tvStatus` — Shows "Status: IDLE / START_PRESSED / SESSION_ACTIVE"

#### `accessibility_service_config.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows"
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="true"
    android:description="@string/accessibility_service_description"
    android:notificationTimeout="100" />
```

#### `device_filter.xml` — USB Device Whitelist

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <usb-device vendor-id="12346" product-id="4097" />
    <usb-device vendor-id="11914" product-id="10" />
</resources>
```

These VID/PIDs correspond to specific USB-to-Serial adapters tested with the project.

---

## 7. BLE Communication Protocol

### UUIDs (Nordic UART Service)

| UUID | Role |
|---|---|
| `6E400001-B5A3-F393-E0A9-E50E24DCCA9E` | Service UUID |
| `6E400003-B5A3-F393-E0A9-E50E24DCCA9E` | TX Characteristic (Notify) |
| `00002902-0000-1000-8000-00805f9b34fb` | Client Characteristic Configuration Descriptor (CCCD) |

### Command Protocol

| Wire Command | Sent By | Received As | Android Action |
|---|---|---|---|
| `CMD:CONNECT` | ESP32 | `"START"` | Place phone call + state → `START_PRESSED` |
| `CMD:CONFIRM` | ESP32 | `"CONFIRM"` | Launch TeamViewer + state → `SESSION_ACTIVE` |
| `CMD:STOP` | ESP32 | `"STOP"` | Close TeamViewer + state → `IDLE` |

### Connection Flow

```
ESP32 (Peripheral)                     Android (Central)
     │                                      │
     │◄── BLE Scan ────────────────────────│  (scans for name "RemoteAssistBridge")
     │                                      │
     │── Advertising (name + NUS UUID) ───►│
     │                                      │
     │◄── connectGatt() ──────────────────│
     │                                      │
     │── STATE_CONNECTED ─────────────────►│
     │                                      │
     │◄── discoverServices() ─────────────│
     │                                      │
     │── onServicesDiscovered ────────────►│
     │                                      │
     │◄── setCharacteristicNotification ──│  (subscribes to TX char)
     │◄── writeDescriptor(CCCD) ──────────│
     │                                      │
     │    ~~~ ready for commands ~~~        │
     │                                      │
     │── notify("CMD:CONNECT") ──────────►│  → onButtonPressed("START")
     │── notify("CMD:CONFIRM") ──────────►│  → onButtonPressed("CONFIRM")
     │── notify("CMD:STOP") ─────────────►│  → onButtonPressed("STOP")
```

---

## 8. State Machine Design

```
                    ┌─────────────────────────────────────┐
                    │                                     │
                    ▼                                     │
              ┌──────────┐    CMD:CONNECT    ┌────────────────┐    CMD:CONFIRM    ┌────────────────┐
              │          │ ────────────────► │                │ ────────────────► │                │
              │   IDLE   │                   │ START_PRESSED  │                   │ SESSION_ACTIVE │
              │          │ ◄──── CMD:STOP ── │                │ ◄──── CMD:STOP ── │                │
              └──────────┘                   └────────────────┘                   └────────────────┘
                    ▲                                                                    │
                    │                                                                    │
                    └────────────────────── CMD:STOP ───────────────────────────────────┘
```

| State | UI Display | What Happens |
|---|---|---|
| `IDLE` | "Status: IDLE" | Waiting for user. No active session. |
| `START_PRESSED` | "Status: START_PRESSED" | Phone call placed to caregiver. Waiting for confirm. |
| `SESSION_ACTIVE` | "Status: SESSION_ACTIVE" | TeamViewer is running. Remote session in progress. |

---

## 9. Android App — Deep Technical Breakdown

### Architecture Pattern
Semi-MVC with callback-based communication between components:

```
MainActivity (Controller + View)
    ├── UsbService (BLE Model) ──── callback: onButtonPressed(String)
    ├── StateMachine (State Model) ── callback: onStateChanged(State)
    └── SplashtopController (Action Executor)
         └── RemoteAccessibilityService (System Service)
```

### Thread Safety
- BLE callbacks arrive on background threads.
- All UI updates are wrapped in `runOnUiThread {}` or `handler.post {}`.
- The `connected` flag in `UsbService` is accessed from both BLE callback threads and the main thread (no synchronization — potential race condition, but low-risk for this use case).

### Permission Handling
- BLE permissions are requested with `requestCode = 101`.
- Call permission is requested with `requestCode = 100`.
- Android 12+ (API 31+) requires new `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` permissions. Older versions use `ACCESS_FINE_LOCATION`.
- All permission checks are version-gated with `Build.VERSION.SDK_INT` comparisons.

### USB Fallback Path
- `MainActivity` has a full USB serial implementation (using `usb-serial-for-android` library).
- The `checkUsbDevice()` function probes for connected USB-to-serial devices.
- A `BroadcastReceiver` handles USB attach/detach/permission events.
- **Currently both BLE and USB paths coexist**, but BLE is the primary communication method.

---

## 10. Arduino Firmware — Deep Technical Breakdown

### Board Configuration
- **Target:** ESP32 (any variant with BLE: ESP32, ESP32-C3, ESP32-S3)
- **Framework:** Arduino
- **Required Board Package:** ESP32 Arduino Core (via Arduino Board Manager)
- **Serial Baud Rate:** 115200

### BLE Peripheral Setup
1. `BLEDevice::init("RemoteAssistBridge")` — Sets the advertised device name.
2. Creates a BLE Server with connect/disconnect callbacks.
3. Creates NUS service with one Notify-only characteristic.
4. Adds BLE2902 descriptor (CCCD) for notification subscription.
5. Configures advertising: includes service UUID, scan response enabled.
6. Connection interval hints: min `0x06` (7.5ms), max `0x12` (22.5ms).

### Button Handling Strategy
- **Connect & Confirm buttons:** Polled in `loop()` with manual edge detection (HIGH→LOW transition).
- **Stop button:** Uses hardware interrupt (`FALLING` edge) + `volatile bool stop_triggered` flag. The ISR sets the flag, and `check_stop()` in `loop()` sends the BLE command. This ensures stop is responsive even if `loop()` is briefly busy.
- **Debounce:** 50ms software debounce in `button_pressed()` utility AND 300ms rate limit in `ble_send()`.

### Memory Considerations
- `IRAM_ATTR` on `stop_ISR()` places the ISR in instruction RAM for faster execution on ESP32.
- Global BLE objects (`pServer`, `pCharacteristic`) are heap-allocated and persist for the lifetime of the program.

---

## 11. Accessibility Service Architecture

The `RemoteAccessibilityService` uses Android's Accessibility API to interact with TeamViewer's UI:

### Registration
- Declared in `AndroidManifest.xml` as a `<service>` with `BIND_ACCESSIBILITY_SERVICE` permission.
- Configuration in `accessibility_service_config.xml`:
  - Listens for: `typeWindowStateChanged`, `typeWindowContentChanged`
  - Flags: `flagReportViewIds`, `flagRetrieveInteractiveWindows`
  - Capabilities: `canRetrieveWindowContent`, `canPerformGestures`

### Singleton Pattern
- `instance` static variable is set in `onServiceConnected()`.
- `SplashtopController.stopSession()` checks `RemoteAccessibilityService.instance` to call `stopTeamViewer()`.
- If the service isn't enabled, the user is redirected to Accessibility Settings.

### Stop Sequence (Current Implementation)
```
1. Launch TeamViewer (bring to foreground)
2. Wait 3000ms
3. performGlobalAction(GLOBAL_ACTION_BACK)  ← triggers close dialog
4. Wait 2000ms
5. dispatchGesture(tap at 780, 1331)        ← taps "Close" button
```

### Alternative Implementation (Defined but Unused)
`findAndClickDisconnect()` searches the accessibility node tree for text matching "Close", "disconnect", "end session", etc. This is more robust but is **not currently wired up**.

---

## 12. Build System & Dependencies

### Gradle Configuration

**Root `build.gradle.kts`:**
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

**`settings.gradle.kts`:**
```kotlin
rootProject.name = "Remote Assist Bridge"
include(":app")
// Repositories: google(), mavenCentral(), jitpack.io
```

**`gradle/libs.versions.toml` — Version Catalog:**

| Key | Version |
|---|---|
| AGP | 9.0.1 |
| Kotlin | 2.2.10 |
| Core KTX | 1.10.1 |
| Lifecycle Runtime KTX | 2.6.1 |
| Activity Compose | 1.8.0 |
| Compose BOM | 2024.09.00 |
| JUnit | 4.13.2 |
| Espresso | 3.5.1 |

**App `build.gradle.kts` key settings:**

| Setting | Value |
|---|---|
| `namespace` | `com.yourcompany.remoteassistbridge` |
| `compileSdk` | 36 |
| `minSdk` | 26 |
| `targetSdk` | 36 |
| `versionCode` | 1 |
| `versionName` | "1.0" |
| `sourceCompatibility` | Java 11 |
| `buildFeatures.compose` | true |
| `buildFeatures.viewBinding` | true |

### Direct Dependencies (in app `build.gradle.kts`)

| Dependency | Version | Purpose |
|---|---|---|
| `androidx.core:core-ktx` | 1.12.0 | Kotlin extensions for Android |
| `androidx.appcompat:appcompat` | 1.6.1 | Backward-compatible activity |
| `com.google.android.material:material` | 1.11.0 | Material Design components |
| `com.github.mik3y:usb-serial-for-android` | 3.7.2 | USB serial communication (JitPack) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.7.3 | Coroutines |
| Jetpack Compose (via BOM) | 2024.09.00 | Compose UI (included but layout uses XML) |

**Note:** The project includes both Compose and ViewBinding. The actual UI uses ViewBinding XML layouts, while Compose dependencies are present from the project template but not actively used in the main UI.

---

## 13. Hardware Wiring & Pin Configuration

```
ESP32 Board (e.g., ESP32-C3-DevKitM-1)
════════════════════════════════════════

  GPIO 20 ───┤BTN├─── GND     CONNECT button
  GPIO 21 ───┤BTN├─── GND     CONFIRM button
  GPIO 10 ───┤BTN├─── GND     STOP button (interrupt-capable)

  All buttons: NO (Normally Open) tactile switches
  Pull-ups: Internal (INPUT_PULLUP in software)
  Logic: Active LOW (pressed = GND, released = VCC via pull-up)
```

| GPIO | Button | Function | Special |
|---|---|---|---|
| 20 | CONNECT | Initiate connection + phone call | Polled in loop() |
| 21 | CONFIRM | Confirm & start TeamViewer | Polled in loop() |
| 10 | STOP | Emergency stop, end session | Hardware interrupt (FALLING) |

---

## 14. Android Permissions Matrix

| Permission | Min API | Purpose | When Requested |
|---|---|---|---|
| `BLUETOOTH` | All | Legacy BLE access | Manifest only |
| `BLUETOOTH_ADMIN` | All | BLE management | Manifest only |
| `BLUETOOTH_SCAN` | 31 (S) | BLE scanning | Runtime (code 101) |
| `BLUETOOTH_CONNECT` | 31 (S) | BLE GATT connection | Runtime (code 101) |
| `ACCESS_FINE_LOCATION` | All | BLE scanning (pre-S) | Runtime (code 101) |
| `ACCESS_COARSE_LOCATION` | All | BLE scanning fallback | Manifest only |
| `CALL_PHONE` | All | Auto-dial caregiver | Runtime (code 100) |
| `KILL_BACKGROUND_PROCESSES` | All | Force-stop TeamViewer | Manifest only |
| Accessibility Service | All | Gesture dispatch on TeamViewer | Manual (Settings) |

---

## 15. UI Layout Specification

The app has a single `Activity` with a vertical `LinearLayout`:

```
┌──────────────────────────────┐
│                              │
│     Remote Assist Bridge     │  ← tvTitle (24sp, bold)
│                              │
│     Waiting for dongle...    │  ← tvUsbStatus (16sp)
│              or              │     "Dongle Connected ✓"
│                              │
│       Status: IDLE           │  ← tvStatus (18sp, black)
│              or              │     "Status: START_PRESSED"
│                              │     "Status: SESSION_ACTIVE"
│                              │
│       Instructions:          │  ← Static text
│   1. Press START button      │
│   2. Press CONFIRM to begin  │
│   3. Press STOP to end       │
│                              │
└──────────────────────────────┘
```

---

## 16. Configuration Parameters

| Parameter | Value | File | Line/Function |
|---|---|---|---|
| BLE Device Name | `"RemoteAssistBridge"` | `ble_cmd.cpp` | `ble_init()` |
| BLE TX Power | `ESP_PWR_LVL_P9` | `ble_cmd.cpp` | `ble_init()` |
| BLE Service UUID | `6E400001-B5A3-F393-E0A9-E50E24DCCA9E` | `ble_cmd.cpp` + `UsbService.kt` | Class constants |
| BLE TX Char UUID | `6E400003-B5A3-F393-E0A9-E50E24DCCA9E` | `ble_cmd.cpp` + `UsbService.kt` | Class constants |
| Button Debounce | 50 ms | `buttons.h` | `DEBOUNCE_MS` |
| BLE Send Rate Limit | 300 ms | `ble_cmd.cpp` | `ble_send()` |
| Connect Button Pin | GPIO 20 | `buttons.h` | `BTN_CONNECT` |
| Confirm Button Pin | GPIO 21 | `buttons.h` | `BTN_CONFIRM` |
| Stop Button Pin | GPIO 10 | `buttons.h` | `BTN_STOP` |
| Caregiver Phone | set via `caregiverPhone` in `local.properties` (gitignored) | `MainActivity.kt` | `makePhoneCall()` |
| TeamViewer Package | `com.teamviewer.quicksupport.market` | `SplashtopController.kt` | `teamviewerPackage` |
| Close Button Coords | (780, 1331) | `RemoteAccessibilityService.kt` | `stopTeamViewer()` |
| BLE Reconnect Delay | 2000 ms | `UsbService.kt` | `onConnectionStateChange()` |
| BLE Scan Start Delay | 1000 ms | `UsbService.kt` | `startScan()` |
| Advertising Restart Delay | 500 ms | `ble_cmd.cpp` | `onDisconnect()` |
| Back-to-Close Delay | 3000 ms | `RemoteAccessibilityService.kt` | `stopTeamViewer()` |
| Close Tap Delay | 2000 ms | `RemoteAccessibilityService.kt` | `stopTeamViewer()` |
| Serial Baud Rate | 115200 | `ble_cmd.cpp` | `ble_init()` |
| Min SDK | 26 | `app/build.gradle.kts` | `defaultConfig` |
| Target SDK | 36 | `app/build.gradle.kts` | `defaultConfig` |
| USB VID Filters | 12346, 11914 | `device_filter.xml` | — |
| USB PID Filters | 4097, 10 | `device_filter.xml` | — |

---

## 17. Data Flow — End to End

### Scenario: User Presses CONNECT

```
1. User presses physical CONNECT button on ESP32 remote
2. GPIO 20 goes LOW (internal pull-up to HIGH at rest)
3. RemoteControl.ino loop() detects HIGH→LOW transition on BTN_CONNECT
4. Calls ble_send("CMD:CONNECT")
5. ble_send() checks rate limit (300ms since last send)
6. If deviceConnected == true:
   a. Sets characteristic value to "CMD:CONNECT"
   b. Calls pCharacteristic->notify()
7. Android UsbService.gattCallback.onCharacteristicChanged() fires
8. Extracts string value: "CMD:CONNECT"
9. Maps to onButtonPressed("START") via handler.post{}
10. MainActivity.onButtonPressed("START") runs on UI thread
11. Calls handleStart():
    a. stateMachine.onStartPressed() → state = START_PRESSED
    b. makePhoneCall() → Intent(ACTION_CALL, "tel:<caregiverPhone from local.properties>")
12. UI updates: "Status: START_PRESSED"
```

### Scenario: User Presses CONFIRM

```
1. User presses CONFIRM button → GPIO 21 LOW
2. ble_send("CMD:CONFIRM") → notify
3. Android receives "CMD:CONFIRM" → onButtonPressed("CONFIRM")
4. handleConfirm():
   a. stateMachine.onConfirmPressed() → state = SESSION_ACTIVE, returns true
   b. splashtopController.startSession()
      → getLaunchIntentForPackage("com.teamviewer.quicksupport.market")
      → startActivity(launchIntent)
5. TeamViewer QuickSupport opens
6. UI updates: "Status: SESSION_ACTIVE"
```

### Scenario: User Presses STOP

```
1. User presses STOP button → GPIO 10 LOW
2. FALLING interrupt fires → stop_ISR() sets stop_triggered = true
3. loop() calls check_stop() → detects stop_triggered == true
4. ble_send("CMD:STOP") → notify
5. Android receives "CMD:STOP" → onButtonPressed("STOP")
6. handleStop():
   a. stateMachine.forceStop() → state = IDLE
   b. splashtopController.stopSession()
      → Gets RemoteAccessibilityService.instance
      → Calls stopTeamViewer():
         i.   Brings TeamViewer to foreground
         ii.  Waits 3s → GLOBAL_ACTION_BACK
         iii. Waits 2s → gesture tap at (780, 1331)
7. TeamViewer close dialog appears → "Close" button tapped
8. UI updates: "Status: IDLE"
```

---

## 18. Known Issues & Technical Debt

| # | Issue | Severity | Detail |
|---|---|---|---|
| 1 | **Hardcoded close coordinates** | High | `stopTeamViewer()` taps at (780, 1331) which is device-specific. Will fail on different screens. Should use `findAndClickDisconnect()` instead. |
| 2 | **`UsbService` naming** | Medium | Class handles BLE, not USB. Should be renamed to `BleService`. |
| 3 | **`SplashtopController` naming** | Medium | Manages TeamViewer, not Splashtop. Should be renamed to `TeamViewerController`. |
| 4 | **Unused `button_pressed()` function** | Low | Defined in `buttons.cpp` but the main sketch uses direct `digitalRead()` with manual edge detection instead. |
| 5 | **`findAndClickDisconnect()` unused** | Medium | More robust text-based approach exists but is never called. `stopTeamViewer()` uses hardcoded coordinates. |
| 6 | **No BLE scan filter** | Low | `startScan()` scans all devices and filters by name in callback. Should use `ScanFilter` for efficiency. |
| 7 | **Thread safety on `connected` flag** | Low | `UsbService.connected` is read/written from multiple threads without synchronization. |
| 8 | **Hardcoded phone number** | Medium | Caregiver number is hardcoded in `makePhoneCall()`. Should be configurable. |
| 9 | **Compose dependencies unused** | Low | Jetpack Compose BOM + libraries are included but the UI uses XML ViewBinding. |
| 10 | **No error handling on BLE disconnect during command** | Low | If BLE disconnects mid-operation, the state machine doesn't reset. |
| 11 | **USB VID/PID hardcoded** | Low | `device_filter.xml` contains specific VID/PIDs. |
| 12 | **Duplicate receiver registration code** | Low | The `if/else` block in `onCreate()` for receiver registration does the same thing in both branches. |

---

## 19. Setup & Deployment Instructions

### Arduino (ESP32 Remote)

1. **Install Arduino IDE** (2.x recommended).
2. **Add ESP32 Board Package:**
   - Preferences → Additional Board URLs: `https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json`
   - Board Manager → Search "ESP32" → Install.
3. **Select Board:** Tools → Board → ESP32 → (your specific board, e.g., ESP32C3 Dev Module).
4. **Open Sketch:** `arduino codes/RemoteControl/RemoteControl.ino`
5. **Wire Buttons:**
   - GPIO 20 → Button → GND (CONNECT)
   - GPIO 21 → Button → GND (CONFIRM)
   - GPIO 10 → Button → GND (STOP)
6. **Upload** at 115200 baud.
7. **Open Serial Monitor** at 115200 to verify: should print "BLE advertising started!"

### Android App

1. **Install Android Studio** (Ladybug or newer for AGP 9.x).
2. **Clone repo:** `git clone https://github.com/IshaanSirbhaiya/Idea.git`
3. **Open** the root folder in Android Studio.
4. **Sync Gradle** — it will download all dependencies including JitPack libraries.
5. **Install TeamViewer QuickSupport** on the target Android device from Play Store.
6. **Build & Deploy** the app to the device.
7. **Grant Permissions:**
   - Bluetooth (auto-prompted)
   - Phone call (auto-prompted)
   - Accessibility Service: Settings → Accessibility → Remote Assist Bridge → Enable
8. **Verify:** App should show "Waiting for dongle..." and scan for BLE devices.

### End-to-End Test
1. Power on ESP32 → Serial shows "BLE advertising started!"
2. Open app on Android → Should connect and show "Dongle Connected ✓"
3. Press CONNECT → Phone dials caregiver, status shows START_PRESSED
4. Press CONFIRM → TeamViewer launches, status shows SESSION_ACTIVE
5. Press STOP → TeamViewer closes, status returns to IDLE

---

## 20. Testing Strategy

### Current Tests (Template)
- `app/src/test/` — `ExampleUnitTest.kt` (JUnit placeholder)
- `app/src/androidTest/` — `ExampleInstrumentedTest.kt` (Espresso placeholder)

### Recommended Test Plan

| Area | Test Type | What to Test |
|---|---|---|
| StateMachine | Unit Test | All state transitions, edge cases (double-press, stop from each state) |
| BLE Command Parsing | Unit Test | `onCharacteristicChanged` mapping of CMD:* to button actions |
| SplashtopController | Instrumented | Launch intent creation, null package handling |
| RemoteAccessibilityService | Manual | Stop sequence on target device |
| Button Debounce | Hardware | Rapid presses don't send duplicate commands |
| BLE Reconnection | Integration | Disconnect ESP32 → verify auto-reconnect in ~2 seconds |
| Full E2E Flow | Manual | CONNECT → CONFIRM → STOP with real hardware |

---

## Appendix: Quick-Start Prompt for AI Assistants

If pasting this PRD into a new AI chat, use this prompt:

> I'm working on the **Remote Assist Bridge** project. The complete PRD with all source code is above. The project has two parts:
> 1. **ESP32 Arduino firmware** (C++) — 3-button remote that sends BLE commands
> 2. **Android Kotlin app** — receives BLE commands, manages TeamViewer sessions
>
> **Repository:** https://github.com/IshaanSirbhaiya/Idea
>
> I need help with: [describe your task here]

---

*Last updated: March 5, 2026*
