# Project Requirements — Remote Assist Bridge

## 1. Purpose

Remote Assist Bridge is designed to allow elderly or non-technical users to request remote technical assistance by pressing a physical button. An ESP32-based remote control communicates over BLE with an Android companion app that automatically initiates a TeamViewer QuickSupport session and optionally places a phone call to a caregiver.

---

## 2. System Components

### 2.1 Hardware — ESP32 Remote Control

| Requirement | Detail |
|---|---|
| Microcontroller | ESP32 with BLE support (ESP32-C3, ESP32-S3, or equivalent) |
| Buttons | 3 tactile push buttons: Connect (GPIO 20), Confirm (GPIO 21), Stop (GPIO 10) |
| Button Wiring | Active-low with internal pull-up resistors (`INPUT_PULLUP`) |
| Debounce | Software debounce at 50 ms per button |
| Stop Interrupt | GPIO 10 configured with a hardware interrupt (`FALLING` edge) for immediate response |
| BLE Advertising Name | `RemoteAssistBridge` |
| BLE TX Power | `ESP_PWR_LVL_P9` (maximum range) |
| Serial Debug | 115200 baud for debug logging |
| Send Rate Limit | Minimum 300 ms between BLE command transmissions |

### 2.2 Software — Android Companion App

| Requirement | Detail |
|---|---|
| Language | Kotlin |
| Min SDK | 26 (Android 8.0 Oreo) |
| Target SDK | 36 |
| Build System | Gradle (Kotlin DSL) with version catalog (`libs.versions.toml`) |
| UI | View Binding (`ActivityMainBinding`) |
| Package | `com.yourcompany.remoteassistbridge` |

---

## 3. Functional Requirements

### 3.1 BLE Communication

| ID | Requirement |
|---|---|
| FR-BLE-01 | The ESP32 shall advertise as a BLE peripheral using the Nordic UART Service (NUS): Service UUID `6E400001-B5A3-F393-E0A9-E50E24DCCA9E` |
| FR-BLE-02 | The ESP32 shall send commands via BLE Notify on characteristic `6E400003-B5A3-F393-E0A9-E50E24DCCA9E` |
| FR-BLE-03 | The Android app shall scan for BLE devices and auto-connect when it finds a device named `RemoteAssistBridge` |
| FR-BLE-04 | On disconnection, the Android app shall automatically re-scan after a 2-second delay |
| FR-BLE-05 | The ESP32 shall restart advertising automatically on client disconnect |

### 3.2 Button Commands

| ID | Command | Trigger | ESP32 Action | Android Action |
|---|---|---|---|---|
| FR-CMD-01 | `CMD:CONNECT` | Connect button pressed | Send BLE notification | Place phone call to caregiver + transition to `START_PRESSED` state |
| FR-CMD-02 | `CMD:CONFIRM` | Confirm button pressed | Send BLE notification | Launch TeamViewer QuickSupport + transition to `SESSION_ACTIVE` state |
| FR-CMD-03 | `CMD:STOP` | Stop button pressed (interrupt) | Send BLE notification | Close TeamViewer via Accessibility Service + transition to `IDLE` state |

### 3.3 State Machine

| State | Description | Transitions |
|---|---|---|
| `IDLE` | Default state, waiting for user input | → `START_PRESSED` (on Connect) |
| `START_PRESSED` | Connect pressed, call initiated | → `SESSION_ACTIVE` (on Confirm) / → `IDLE` (on Stop) |
| `SESSION_ACTIVE` | TeamViewer session running | → `IDLE` (on Stop) |

### 3.4 Phone Call

| ID | Requirement |
|---|---|
| FR-CALL-01 | When the Connect button is pressed, the app shall automatically place a phone call to the configured caregiver number |
| FR-CALL-02 | The app shall request `CALL_PHONE` permission at runtime |

### 3.5 TeamViewer Integration

| ID | Requirement |
|---|---|
| FR-TV-01 | The app shall launch TeamViewer QuickSupport (`com.teamviewer.quicksupport.market`) when Confirm is pressed |
| FR-TV-02 | The app shall display a toast if TeamViewer is not installed |
| FR-TV-03 | The app shall close TeamViewer via an Accessibility Service when Stop is pressed |
| FR-TV-04 | If the Accessibility Service is not enabled, the app shall redirect the user to Accessibility Settings |

### 3.6 USB Support (Legacy/Fallback)

| ID | Requirement |
|---|---|
| FR-USB-01 | The app shall detect USB serial devices on attach and request permission |
| FR-USB-02 | The app shall use the `usb-serial-for-android` library (v3.7.2) for USB serial probing |
| FR-USB-03 | The app shall handle USB attach/detach broadcast events |

---

## 4. Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-01 | The system shall respond to button presses within 500 ms (BLE latency + app processing) |
| NFR-02 | The ESP32 firmware shall fit within standard ESP32 flash memory |
| NFR-03 | The Android app shall support devices running Android 8.0 (API 26) and above |
| NFR-04 | BLE reconnection shall occur automatically without user intervention |
| NFR-05 | The UI shall display real-time connection status and current state |

---

## 5. Android Permissions

| Permission | Purpose | Required |
|---|---|---|
| `BLUETOOTH` | Legacy Bluetooth access | Yes |
| `BLUETOOTH_ADMIN` | Bluetooth management | Yes |
| `BLUETOOTH_SCAN` | BLE scanning (Android 12+) | Yes |
| `BLUETOOTH_CONNECT` | BLE connection (Android 12+) | Yes |
| `ACCESS_FINE_LOCATION` | BLE scanning (Android < 12) | Yes |
| `ACCESS_COARSE_LOCATION` | BLE scanning fallback | Yes |
| `CALL_PHONE` | Auto-dial caregiver | Yes |
| `KILL_BACKGROUND_PROCESSES` | Force-close TeamViewer | Yes |
| Accessibility Service | Programmatically close TeamViewer | Yes (manual enable) |

---

## 6. Dependencies

### Android App

| Library | Version | Purpose |
|---|---|---|
| AndroidX Core KTX | 1.12.0 | Kotlin extensions |
| AppCompat | 1.6.1 | Backward-compatible UI |
| Material Components | 1.11.0 | Material Design UI |
| Jetpack Compose (BOM) | Managed | Compose UI framework |
| usb-serial-for-android | 3.7.2 | USB serial device support |
| Kotlinx Coroutines | 1.7.3 | Asynchronous programming |

### Arduino Firmware

| Library | Source | Purpose |
|---|---|---|
| BLEDevice / BLEServer / BLEUtils / BLE2902 | ESP32 Arduino Core | BLE peripheral functionality |

---

## 7. Hardware Wiring Diagram

```
ESP32 Board
├── GPIO 20 ── [BTN_CONNECT] ── GND
├── GPIO 21 ── [BTN_CONFIRM] ── GND
└── GPIO 10 ── [BTN_STOP]    ── GND (with interrupt)

All buttons use internal pull-up resistors (INPUT_PULLUP).
Active LOW — button press pulls pin to GND.
```

---

## 8. Configuration

| Parameter | Current Value | Location |
|---|---|---|
| Caregiver phone number | set via `caregiverPhone` in `local.properties` (gitignored) | `MainActivity.kt` — `makePhoneCall()` |
| BLE device name | `RemoteAssistBridge` | `ble_cmd.cpp` — `ble_init()` |
| BLE TX power | `ESP_PWR_LVL_P9` | `ble_cmd.cpp` — `ble_init()` |
| Debounce interval | 50 ms | `buttons.h` — `DEBOUNCE_MS` |
| BLE send rate limit | 300 ms | `ble_cmd.cpp` — `ble_send()` |
| TeamViewer package | `com.teamviewer.quicksupport.market` | `SplashtopController.kt` |
