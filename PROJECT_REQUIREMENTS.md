# Remote Assist Bridge — Complete Project Requirements Document

> **Purpose of this document:** Provide full context of the working codebase so that a new AI chat session (Claude, ChatGPT, etc.) can understand the entire project and modify/extend it to add new features.

---

## 1. PROJECT OVERVIEW

### 1.1 What is Remote Assist Bridge?

**Remote Assist Bridge** is an Android app that acts as middleware between a **physical USB button dongle** (Arduino/RP2040-based) and a **Splashtop remote desktop session + phone call**. 

**Use case:** An elderly person (or child) has a physical device with 3 buttons (START, CONFIRM, STOP). When they press the buttons in sequence, the app automatically:
1. Launches a Splashtop remote assist session (so a helper can see/control their screen)
2. Initiates a phone call to a pre-configured parent/helper number

This allows a non-tech-savvy user to get remote help with a simple physical button press, without needing to navigate any apps.

### 1.2 High-Level Flow

```
[Physical Button Dongle (RP2040/Arduino)]
        │ USB Serial (115200 baud)
        ▼
[Android App - Remote Assist Bridge]
        │
        ├──► Launches Splashtop app
        └──► Initiates phone call to helper
```

**User flow:**
1. User plugs in USB dongle (or it's already connected)
2. User presses **START** button → App moves to "START_PRESSED" state
3. User presses **CONFIRM** button → App launches Splashtop + dials phone number
4. User presses **STOP** button → App ends the session

---

## 2. TECH STACK & BUILD CONFIGURATION

### 2.1 Platform & Language
- **Platform:** Android (native)
- **Language:** Kotlin
- **Min SDK:** 26 (Android 8.0 Oreo)
- **Target SDK:** 36
- **Compile SDK:** 36
- **Java Compatibility:** Java 11

### 2.2 Build System
- **Gradle** with Kotlin DSL (`build.gradle.kts`)
- **AGP (Android Gradle Plugin):** 9.0.0
- **Kotlin:** 2.0.21
- **Version Catalog:** `gradle/libs.versions.toml`

### 2.3 Project Identifiers
- **Package name:** `com.yourcompany.remoteassistbridge`
- **Application ID:** `com.yourcompany.remoteassistbridge`
- **Root project name:** `Remote Assist Bridge`
- **Version:** 1.0 (versionCode=1)

### 2.4 Dependencies

| Dependency | Version | Purpose |
|---|---|---|
| `androidx.core:core-ktx` | 1.12.0 | Kotlin extensions for Android |
| `androidx.appcompat:appcompat` | 1.6.1 | AppCompat (backwards compat UI) |
| `com.google.android.material:material` | 1.11.0 | Material Design components |
| `com.github.mik3y:usb-serial-for-android` | 3.7.2 | USB Serial communication library |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.7.3 | Coroutines for async USB reading |
| `androidx.compose.*` (BOM 2024.09.00) | various | Jetpack Compose (present but UI uses XML Views) |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.6.1 | Lifecycle-aware components |
| `androidx.activity:activity-compose` | 1.8.0 | Compose activity integration |

**Important note:** The project has both Compose and View Binding enabled in `build.gradle.kts`, but the actual UI is built with **XML layouts + ViewBinding**, NOT Compose. The Compose dependencies are scaffolded but unused in the main UI. The `ui/theme/` package (Color.kt, Theme.kt, Type.kt) contains Compose theme definitions that are default/boilerplate.

### 2.5 Repository Sources
```kotlin
// settings.gradle.kts
repositories {
    google()
    mavenCentral()
    maven { setUrl("https://jitpack.io") }  // Required for usb-serial-for-android
}
```

---

## 3. ANDROID MANIFEST & PERMISSIONS

**File:** `app/src/main/AndroidManifest.xml`

### 3.1 Permissions
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.CALL_PHONE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

### 3.2 Hardware Features
```xml
<uses-feature android:name="android.hardware.usb.host" />
```
The device MUST support USB Host mode (OTG).

### 3.3 Activity Declaration
- Single activity: `MainActivity`
- Theme: `@style/Theme.AppCompat.Light`
- Has USB_DEVICE_ATTACHED intent filter so the app auto-launches when the USB dongle is plugged in
- USB device filter references `@xml/device_filter`

### 3.4 USB Device Filter
**File:** `app/src/main/res/xml/device_filter.xml`
```xml
<usb-device vendor-id="11914" product-id="10" />
```
- **Vendor ID 11914 (0x2E8A)** = Raspberry Pi Foundation (RP2040)
- **Product ID 10 (0x000A)** = RP2040 CDC serial
- This means the app specifically targets RP2040-based USB devices

---

## 4. ARCHITECTURE & SOURCE FILES

The app follows a simple **single-Activity architecture** with 4 classes:

```
com.yourcompany.remoteassistbridge/
├── MainActivity.kt          ← Main entry point, UI controller, USB receiver
├── UsbService.kt            ← USB serial communication handler
├── StateMachine.kt          ← 3-state workflow state machine
├── SplashtopController.kt   ← Launches/stops Splashtop app
└── ui/theme/                ← Compose theme (boilerplate, unused)
    ├── Color.kt
    ├── Theme.kt
    └── Type.kt
```

---

## 5. DETAILED CODE DOCUMENTATION

### 5.1 StateMachine.kt

**Purpose:** Manages the 3-state workflow enforcing button press order.

**States (enum):**
```
IDLE → START_PRESSED → SESSION_ACTIVE → IDLE (cycle)
```

**Transitions:**
| Current State | Button | Next State | Returns |
|---|---|---|---|
| `IDLE` | START | `START_PRESSED` | void |
| `START_PRESSED` | CONFIRM | `SESSION_ACTIVE` | `true` |
| `SESSION_ACTIVE` | STOP | `IDLE` | void |
| Any other combo | Any | No change | `false` (for confirm) |

**Key behavior:**
- `onStartPressed()` — Only advances from IDLE
- `onConfirmPressed()` — Only advances from START_PRESSED, returns `true` if successful (used to trigger Splashtop + call)
- `onStopPressed()` — Only resets from SESSION_ACTIVE
- Constructor takes a callback `(State) -> Unit` that fires on every state change
- `getCurrentState()` returns the current state

**Full source:**
```kotlin
class StateMachine(private val onStateChanged: (State) -> Unit) {
    enum class State { IDLE, START_PRESSED, SESSION_ACTIVE }
    private var currentState = State.IDLE

    fun onStartPressed() {
        if (currentState == State.IDLE) {
            currentState = State.START_PRESSED
            onStateChanged(currentState)
        }
    }

    fun onConfirmPressed(): Boolean {
        if (currentState == State.START_PRESSED) {
            currentState = State.SESSION_ACTIVE
            onStateChanged(currentState)
            return true
        }
        return false
    }

    fun onStopPressed() {
        if (currentState == State.SESSION_ACTIVE) {
            currentState = State.IDLE
            onStateChanged(currentState)
        }
    }

    fun getCurrentState() = currentState
}
```

---

### 5.2 UsbService.kt

**Purpose:** Handles USB serial communication with the physical button dongle. Reads serial data, parses button commands, and invokes callbacks.

**Key details:**
- Uses the `usb-serial-for-android` library (mik3y)
- Serial config: **115200 baud, 8 data bits, 1 stop bit, no parity**
- Reads data on a **coroutine (Dispatchers.IO)** in a continuous loop
- Accumulates bytes and processes **complete lines** (newline-delimited)
- **Protocol:** Expects `BTN:COMMAND\n` format from the dongle
  - `BTN:START\n` → triggers "START"
  - `BTN:CONFIRM\n` → triggers "CONFIRM"  
  - `BTN:STOP\n` → triggers "STOP"

**Constructor:**
```kotlin
UsbService(context: Context, onButtonPressed: (String) -> Unit)
```

**Public methods:**
| Method | Description |
|---|---|
| `connect(device: UsbDevice)` | Opens USB serial connection, starts reading loop |
| `disconnect()` | Cancels read coroutine, closes serial port |
| `isConnected(): Boolean` | Returns true if port is open |

**Internal flow:**
1. `connect()` → Probes the USB device with `UsbSerialProber.getDefaultProber()`
2. Opens the first port of the driver
3. Sets serial parameters: 115200/8/N/1
4. Starts `startReading()` coroutine
5. `startReading()` loops reading 256-byte chunks with 1000ms timeout
6. Accumulates text, splits on `\n`, processes complete lines
7. `processCommand()` strips `BTN:` prefix and calls `onButtonPressed(button)`

**Full source:**
```kotlin
class UsbService(
    private val context: Context,
    private val onButtonPressed: (String) -> Unit
) {
    private var serialPort: UsbSerialPort? = null
    private var readJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun connect(device: UsbDevice) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
        if (driver == null) return
        val connection = usbManager.openDevice(device) ?: return
        serialPort = driver.ports[0].apply {
            open(connection)
            setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        }
        startReading()
    }

    private fun startReading() {
        readJob = scope.launch {
            val buffer = ByteArray(256)
            var accumulated = ""
            while (true) {
                try {
                    val len = serialPort?.read(buffer, 1000) ?: break
                    if (len > 0) {
                        val received = String(buffer, 0, len)
                        accumulated += received
                        while (accumulated.contains("\n")) {
                            val lineEnd = accumulated.indexOf("\n")
                            val line = accumulated.substring(0, lineEnd).trim()
                            accumulated = accumulated.substring(lineEnd + 1)
                            processCommand(line)
                        }
                    }
                } catch (e: IOException) { break }
            }
        }
    }

    private fun processCommand(command: String) {
        if (command.startsWith("BTN:")) {
            val button = command.substring(4)
            onButtonPressed(button)
        }
    }

    fun isConnected(): Boolean = serialPort != null && serialPort?.isOpen == true

    fun disconnect() {
        readJob?.cancel()
        try { serialPort?.close() } catch (e: IOException) {}
        serialPort = null
    }
}
```

---

### 5.3 SplashtopController.kt

**Purpose:** Launches and stops the Splashtop remote desktop app.

**Key details:**
- Target package: `com.splashtop.remote.pad.v2` (Splashtop Personal)
- Uses `packageManager.getLaunchIntentForPackage()` to launch it
- `stopSession()` only shows a Toast — Splashtop has no public API to programmatically disconnect

**Full source:**
```kotlin
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
        Toast.makeText(context, "Please disconnect Splashtop manually", Toast.LENGTH_SHORT).show()
    }
}
```

---

### 5.4 MainActivity.kt

**Purpose:** Main entry point. Ties together USB handling, state machine, Splashtop control, and phone calling. Also contains test buttons for development without hardware.

**Key behaviors:**

#### Initialization (onCreate):
1. Inflates layout via ViewBinding (`ActivityMainBinding`)
2. Creates `UsbService`, `StateMachine`, `SplashtopController`
3. Requests `CALL_PHONE` runtime permission
4. Registers a `BroadcastReceiver` for USB events (permission, attach, detach)
5. Checks if USB device already connected
6. Wires up 3 test buttons for software-based testing

#### USB Handling:
- Registers for: `USB_PERMISSION`, `USB_DEVICE_ATTACHED`, `USB_DEVICE_DETACHED`
- On attach: requests permission, then calls `usbService.connect(device)`
- On detach: calls `usbService.disconnect()`
- Handles API level differences (TIRAMISU+ vs older) for `getParcelableExtra()`

#### Button Handling (from USB or test buttons):
```
onButtonPressed("START")   → stateMachine.onStartPressed()
onButtonPressed("CONFIRM") → stateMachine.onConfirmPressed() → if true: splashtopController.startSession() + makePhoneCall()
onButtonPressed("STOP")    → stateMachine.onStopPressed() + splashtopController.stopSession()
```

#### Phone Call:
- Hardcoded number: `tel:+1234567890` (TODO: make configurable)
- Uses `Intent.ACTION_CALL` (direct call, not dialer)
- Requires `CALL_PHONE` permission

#### UI Updates:
- Updates `tvStatus` with current state
- Updates `tvUsbStatus` with "Dongle Connected" or "Waiting for dongle..."

#### Test Mode:
The current layout has 3 buttons (`btnTestStart`, `btnTestConfirm`, `btnTestStop`) that simulate hardware button presses for testing. These are labeled "--- TEST MODE ---" and should be removed when hardware is ready.

---

## 6. UI LAYOUT

**File:** `app/src/main/res/layout/activity_main.xml`

Simple vertical `LinearLayout` centered on screen:

| View | ID | Content |
|---|---|---|
| Title | `tvTitle` | "Remote Assist Bridge" (24sp, bold) |
| USB Status | `tvUsbStatus` | "Waiting for dongle..." (16sp) |
| State | `tvStatus` | "Status: IDLE" (18sp) |
| Instructions | — | Multi-line instructions text |
| Test Header | — | "--- TEST MODE ---" (red, bold) |
| Button | `btnTestStart` | "TEST: START" |
| Button | `btnTestConfirm` | "TEST: CONFIRM" |
| Button | `btnTestStop` | "TEST: STOP" |

**Note:** There's also `activity_main_ORIGINAL.xml` which is the same layout WITHOUT the test buttons (the production layout).

---

## 7. USB DONGLE PROTOCOL

### Expected Hardware
- **Microcontroller:** RP2040 (Raspberry Pi Pico) or Arduino
- **USB Vendor ID:** 0x2E8A (11914 decimal) — Raspberry Pi Foundation
- **USB Product ID:** 0x000A (10 decimal) — RP2040 CDC

### Serial Protocol
- **Baud rate:** 115200
- **Data bits:** 8
- **Stop bits:** 1
- **Parity:** None
- **Line ending:** `\n` (newline)

### Command Format
The dongle sends newline-terminated commands:
```
BTN:START\n    → User wants to initiate a session
BTN:CONFIRM\n  → User confirms session start  
BTN:STOP\n     → User wants to end the session
```

### Expected Arduino/RP2040 Code Pattern
```cpp
// Pseudocode for the dongle firmware
void loop() {
    if (startButtonPressed()) Serial.println("BTN:START");
    if (confirmButtonPressed()) Serial.println("BTN:CONFIRM");
    if (stopButtonPressed()) Serial.println("BTN:STOP");
}
```

---

## 8. FILE TREE WITH DESCRIPTIONS

```
Remote Assist Bridge/
├── build.gradle.kts                          # Root build file (plugin declarations)
├── settings.gradle.kts                       # Project name, module includes, repositories
├── gradle.properties                         # JVM args, AndroidX, Kotlin style
├── gradlew / gradlew.bat                     # Gradle wrapper scripts
├── local.properties                          # Local SDK path (gitignored)
├── .gitignore                                # Git ignore rules
│
├── gradle/
│   ├── libs.versions.toml                    # Version catalog (AGP, Kotlin, Compose, etc.)
│   └── wrapper/
│       └── gradle-wrapper.properties         # Gradle 8.x wrapper config
│
└── app/
    ├── build.gradle.kts                      # App module build config (deps, SDK, features)
    ├── proguard-rules.pro                    # ProGuard rules (empty)
    ├── .gitignore                            # Ignores /build directory
    │
    └── src/main/
        ├── AndroidManifest.xml               # Permissions, USB host, activity declaration
        │
        ├── java/com/yourcompany/remoteassistbridge/
        │   ├── MainActivity.kt               # Main UI + USB receiver + test buttons
        │   ├── MainActivity_ORIGINAL.kt      # Backup of original (no test buttons)
        │   ├── UsbService.kt                 # USB serial communication
        │   ├── StateMachine.kt               # IDLE→START_PRESSED→SESSION_ACTIVE state machine
        │   ├── SplashtopController.kt        # Splashtop app launcher
        │   └── ui/theme/
        │       ├── Color.kt                  # Compose colors (boilerplate)
        │       ├── Theme.kt                  # Compose theme (boilerplate)
        │       └── Type.kt                   # Compose typography (boilerplate)
        │
        └── res/
            ├── layout/
            │   ├── activity_main.xml          # Current layout (with test buttons)
            │   └── activity_main_ORIGINAL.xml # Original layout (no test buttons)
            ├── values/
            │   ├── strings.xml                # App name: "Remote Assist Bridge"
            │   ├── colors.xml                 # Basic color palette
            │   └── themes.xml                 # AppCompat Light theme
            ├── xml/
            │   ├── device_filter.xml          # USB device filter (RP2040)
            │   ├── backup_rules.xml           # Backup config (default)
            │   └── data_extraction_rules.xml  # Data extraction config (default)
            ├── drawable/                       # Launcher icon vectors
            └── mipmap-*/                      # Launcher icons (various densities)
```

---

## 9. CURRENT TODOs & KNOWN ISSUES

1. **Phone number is hardcoded** (`tel:+1234567890`) — needs to be configurable via SharedPreferences or a settings screen
2. **Test buttons should be removed** for production — use `activity_main_ORIGINAL.xml` layout
3. **Splashtop has no stop API** — `stopSession()` only shows a Toast telling user to disconnect manually
4. **No error recovery** — if USB disconnects mid-session, the state machine doesn't reset
5. **No background service** — if the user navigates away, USB reading stops
6. **MainActivity_ORIGINAL.kt has a class name issue** — the class is named with backticks which is unusual Kotlin syntax
7. **Compose theme is unused** — the `ui/theme/` package is default boilerplate, UI uses XML layouts
8. **No settings/configuration screen** — phone number, Splashtop package, etc. are hardcoded

---

## 10. POTENTIAL FEATURES TO ADD

- **Settings screen** for phone number, Splashtop config
- **Foreground service** to keep USB reading alive when app is backgrounded
- **LED/buzzer feedback** via USB serial write-back to dongle
- **Multiple phone numbers** support
- **Session logging/history**
- **Auto-reconnect** on USB disconnect
- **Battery/status indicators** on the physical dongle
- **Alternative remote desktop apps** (TeamViewer, AnyDesk, etc.)
- **Voice feedback** using TTS to guide the user through button presses
- **Kiosk/lock mode** to prevent the user from accidentally leaving the app

---

## 11. HOW TO BUILD & RUN

### Prerequisites
- Android Studio (latest)
- Android SDK 36
- A device with USB Host (OTG) support
- RP2040/Arduino dongle for hardware testing (or use test buttons)

### Steps
1. Clone: `git clone https://github.com/IshaanSirbhaiya/Idea.git`
2. Open in Android Studio
3. Sync Gradle
4. Connect Android device with USB debugging
5. Run on device
6. Use the 3 test buttons on screen, OR connect the RP2040 dongle

### For hardware testing
1. Flash RP2040/Arduino with firmware that sends `BTN:START\n`, `BTN:CONFIRM\n`, `BTN:STOP\n` over USB serial at 115200 baud
2. Connect dongle to Android device via OTG adapter
3. App should auto-detect and show "Dongle Connected"

---

## 12. INSTRUCTIONS FOR AI ASSISTANTS

When modifying this project:

1. **The UI uses XML layouts + ViewBinding**, not Jetpack Compose. Don't convert to Compose unless asked.
2. **ViewBinding** is enabled — access views via `binding.viewId` (e.g., `binding.tvStatus`)
3. **The main logic flow** is: USB dongle sends `BTN:X` → `UsbService` parses → calls `onButtonPressed` callback → `MainActivity` routes to `StateMachine` → state change triggers UI update / actions
4. **All USB operations** run on `Dispatchers.IO` via coroutines
5. **The package name** is `com.yourcompany.remoteassistbridge` — keep it consistent
6. **The Splashtop package** is `com.splashtop.remote.pad.v2`
7. **The USB device filter** targets RP2040 (VID: 11914, PID: 10)
8. **Test buttons** exist for development — they simulate `onButtonPressed()` calls
9. **Phone calling** uses `Intent.ACTION_CALL` which requires `CALL_PHONE` permission and actually dials (not just opens dialer)
10. **The `_ORIGINAL` files** are backups of the pre-test-button versions

When adding new features, maintain the existing architecture pattern: create a focused service/controller class and wire it into `MainActivity`.
