package com.example.seniorproject

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.util.Log
import java.util.UUID

/**
 * BLE Service for managing connection to ASL glove.
 * Handles GATT operations including writing commands to the glove.
 */
class BLEService(private val context: Context) {
    
    companion object {
        private const val TAG = "BLEService"
        
        // Nordic UART Service (NUS) UUIDs from ASL_BLE_FINAL_frfrfrfr.ino
        private val NUS_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        private val NUS_RX_CHAR_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E") // Write here
        private val NUS_TX_CHAR_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E") // Notify from here
        private val CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
    
    private var gatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var isConnected = false
    private var deviceAddress: String? = null
    
    // Callbacks
    var onConnectionStateChange: ((connected: Boolean) -> Unit)? = null
    var onDataReceived: ((data: String) -> Unit)? = null
    
    /**
     * Connect to a BLE device.
     */
    fun connect(device: BluetoothDevice): Boolean {
        deviceAddress = device.address
        Log.d(TAG, "Connecting to BLE device: ${device.address}")
        
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        
        gatt = device.connectGatt(context, false, gattCallback)
        return gatt != null
    }
    
    /**
     * Write a command to the glove via RX characteristic.
     */
    fun writeCommand(command: String): Boolean {
        val characteristic = rxCharacteristic
        val gatt = this.gatt
        
        if (characteristic == null || gatt == null || !isConnected) {
            Log.e(TAG, "Cannot write: characteristic=${characteristic != null}, gatt=${gatt != null}, connected=$isConnected")
            return false
        }
        
        try {
            val data = "$command\n".toByteArray() // Add newline for command termination
            characteristic.value = data
            // Use default write type for broader compatibility across devices
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            
            val success = gatt.writeCharacteristic(characteristic)
            Log.d(TAG, "Writing command: $command (${data.size} bytes) - Success: $success")
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Error writing command: $command", e)
            return false
        }
    }
    
    /**
     * Disconnect from the device.
     */
    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        isConnected = false
        rxCharacteristic = null
        txCharacteristic = null
        deviceAddress = null
        Log.d(TAG, "Disconnected from BLE device")
    }
    
    /**
     * Check if currently connected.
     */
    fun isConnected(): Boolean = isConnected
    
    /**
     * Get the current device address.
     */
    fun getDeviceAddress(): String? = deviceAddress
    
    /**
     * GATT callback for handling connection events.
     */
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to GATT server")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from GATT server")
                    isConnected = false
                    onConnectionStateChange?.invoke(false)
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered")
                
                val service = gatt.getService(NUS_SERVICE_UUID)
                if (service != null) {
                    Log.d(TAG, "Found NUS service")
                    
                    // Get RX characteristic (for writing commands)
                    rxCharacteristic = service.getCharacteristic(NUS_RX_CHAR_UUID)
                    if (rxCharacteristic != null) {
                        Log.d(TAG, "Found RX characteristic")
                    } else {
                        Log.e(TAG, "RX characteristic not found!")
                    }
                    
                    // Get TX characteristic (for receiving notifications)
                    val txChar = service.getCharacteristic(NUS_TX_CHAR_UUID)
                    txCharacteristic = txChar
                    if (txChar != null) {
                        Log.d(TAG, "Found TX characteristic")
                        
                        // Enable notifications
                        gatt.setCharacteristicNotification(txChar, true)
                        val descriptor = txChar.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                        descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                        Log.d(TAG, "Enabled notifications for TX characteristic")
                        // Request higher MTU to reduce fragmentation
                        try {
                            gatt.requestMtu(185)
                            Log.d(TAG, "Requested MTU 185")
                        } catch (e: Exception) {
                            Log.w(TAG, "MTU request failed: ${e.message}")
                        }
                    } else {
                        Log.e(TAG, "TX characteristic not found!")
                    }
                    
                    isConnected = true
                    onConnectionStateChange?.invoke(true)
                } else {
                    Log.e(TAG, "NUS service not found!")
                }
            } else {
                Log.e(TAG, "Service discovery failed: $status")
            }
        }
        
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            // Data received from the glove
            val data = characteristic.value
            val string = String(data, Charsets.UTF_8)
            Log.d(TAG, "Received: $string")
            onDataReceived?.invoke(string)
        }
        
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Write successful")
            } else {
                Log.e(TAG, "Write failed: $status")
            }
        }
    }
}
