package com.example.banglehealth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.UUID

/**
 * Conexión sencilla con el Bangle.js 2 por Bluetooth LE.
 *
 * El reloj ofrece el servicio "Nordic UART": escribimos comandos de JavaScript
 * en RX y el reloj contesta por TX (notificaciones). Para probar la conexión
 * le pedimos el nivel de batería.
 */
@SuppressLint("MissingPermission") // Los permisos se piden en MainActivity antes de llamar a connect()
@Suppress("DEPRECATION") // API antiguas para que funcione también en Android 12 (API 31-32)
class BangleConnection(
    context: Context,
    private val onStatus: (String) -> Unit,
    private val onResponse: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val adapter = appContext.getSystemService(BluetoothManager::class.java).adapter
    private val mainHandler = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var scanning = false
    private val buffer = StringBuilder()

    fun connect() {
        if (adapter == null || !adapter.isEnabled) {
            status("Activa el Bluetooth")
            return
        }
        disconnect()
        val scanner = adapter.bluetoothLeScanner ?: run {
            status("Activa el Bluetooth")
            return
        }
        status("Buscando reloj…")
        scanning = true
        scanner.startScan(scanCallback)
        // Si en 10 s no aparece, paramos la búsqueda
        mainHandler.postDelayed({
            if (scanning) {
                stopScan()
                status("No se encuentra el reloj")
            }
        }, 10_000)
    }

    fun disconnect() {
        stopScan()
        gatt?.close()
        gatt = null
    }

    private fun stopScan() {
        if (scanning) {
            scanning = false
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: result.device.name ?: return
            if (scanning && name.startsWith("Bangle.js")) {
                stopScan()
                status("Conectando con $name…")
                gatt = result.device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            status("Error al buscar ($errorCode)")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, st: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                status("Desconectado")
                g.close()
                if (gatt == g) gatt = null
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, st: Int) {
            val tx = g.getService(UART_SERVICE)?.getCharacteristic(UART_TX)
            if (tx == null) {
                status("El reloj no tiene el servicio UART")
                return
            }
            // Activar notificaciones de TX para recibir lo que conteste el reloj
            g.setCharacteristicNotification(tx, true)
            val cccd = tx.getDescriptor(CCCD)
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            g.writeDescriptor(cccd)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, st: Int) {
            status("Conectado")
            // \u0010 al principio = el reloj no repite el comando (sin eco)
            send(g, "\u0010Bluetooth.println(E.getBattery())\n")
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            buffer.append(String(c.value))
            // La respuesta termina en salto de línea
            while (buffer.contains('\n')) {
                val line = buffer.substring(0, buffer.indexOf('\n')).trim()
                buffer.delete(0, buffer.indexOf('\n') + 1)
                if (line.isNotEmpty() && line != ">") {
                    mainHandler.post { onResponse("Batería: $line %") }
                }
            }
        }
    }

    private fun send(g: BluetoothGatt, text: String) {
        val rx = g.getService(UART_SERVICE)?.getCharacteristic(UART_RX) ?: return
        rx.value = text.toByteArray()
        g.writeCharacteristic(rx)
    }

    private fun status(text: String) = mainHandler.post { onStatus(text) }

    companion object {
        val UART_SERVICE: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val UART_RX: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        val UART_TX: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
