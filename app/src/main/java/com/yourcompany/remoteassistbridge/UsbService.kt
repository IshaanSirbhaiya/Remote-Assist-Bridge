package com.yourcompany.remoteassistbridge

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException

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

        if (driver == null) {
            return
        }

        val connection = usbManager.openDevice(device)
        if (connection == null) {
            return
        }

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

                        // Process complete lines
                        while (accumulated.contains("\n")) {
                            val lineEnd = accumulated.indexOf("\n")
                            val line = accumulated.substring(0, lineEnd).trim()
                            accumulated = accumulated.substring(lineEnd + 1)

                            processCommand(line)
                        }
                    }
                } catch (e: IOException) {
                    break
                }
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
        try {
            serialPort?.close()
        } catch (e: IOException) {
            // Ignore
        }
        serialPort = null
    }
}