package com.example.seniorproject.data

import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * Sensor source that receives real-time data from the ASL glove via Bluetooth.
 * 
 * Expected data format from glove (10 features per line):
 * flex1,flex2,flex3,flex4,flex5,roll,pitch,ax,ay,az
 * 
 * Example: 0.75,0.23,0.12,0.15,0.08,25.3,12.1,0.05,-0.02,1.01
 */
class BluetoothSensorSource(
    private val socket: BluetoothSocket
) : SensorSource {
    
    companion object {
        private const val TAG = "BluetoothSensor"
        private const val NUM_FEATURES = 10
    }
    
    private var reader: BufferedReader? = null
    private var isConnected = false
    
    init {
        try {
            reader = BufferedReader(InputStreamReader(socket.inputStream))
            isConnected = true
            Log.d(TAG, "Bluetooth sensor source initialized successfully")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to initialize Bluetooth reader", e)
            isConnected = false
        }
    }
    
    /**
     * Read the next line of sensor data from the Bluetooth glove.
     * Returns null if connection is lost or data is invalid.
     */
    override suspend fun nextFeatures(): FloatArray? = withContext(Dispatchers.IO) {
        if (!isConnected || reader == null) {
            Log.w(TAG, "Bluetooth not connected")
            return@withContext null
        }
        
        try {
            val line = reader?.readLine()
            if (line == null) {
                Log.w(TAG, "Received null line from Bluetooth (connection may be closed)")
                isConnected = false
                return@withContext null
            }
            
            // Skip empty lines or comments
            if (line.isBlank() || line.startsWith("#")) {
                return@withContext nextFeatures() // Recursively get next valid line
            }
            
            // Parse comma-separated values
            val parts = line.split(",").map { it.trim() }
            
            if (parts.size < NUM_FEATURES) {
                Log.w(TAG, "Invalid data format: expected $NUM_FEATURES values, got ${parts.size}")
                return@withContext null
            }
            
            // Convert to float array
            val features = FloatArray(NUM_FEATURES)
            for (i in 0 until NUM_FEATURES) {
                features[i] = parts[i].toFloatOrNull() ?: run {
                    Log.w(TAG, "Failed to parse value at index $i: ${parts[i]}")
                    return@withContext null
                }
            }
            
            Log.d(TAG, "Received sensor data: ${features.joinToString { "%.2f".format(it) }}")
            return@withContext features
            
        } catch (e: IOException) {
            Log.e(TAG, "Error reading from Bluetooth", e)
            isConnected = false
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error parsing sensor data", e)
            return@withContext null
        }
    }
    
    /**
     * Collect a sequence of sensor readings.
     * @param maxLength Number of timesteps to collect
     */
    override suspend fun nextSequence(maxLength: Int): Array<FloatArray>? {
        val sequence = mutableListOf<FloatArray>()
        
        repeat(maxLength) {
            val features = nextFeatures() ?: return if (sequence.isEmpty()) {
                null
            } else {
                sequence.toTypedArray()
            }
            sequence.add(features)
        }
        
        return sequence.toTypedArray()
    }
    
    /**
     * Close the Bluetooth connection and clean up resources.
     */
    override suspend fun close() {
        withContext(Dispatchers.IO) {
            try {
                reader?.close()
                socket.close()
                isConnected = false
                Log.d(TAG, "Bluetooth sensor source closed")
            } catch (e: IOException) {
                Log.e(TAG, "Error closing Bluetooth connection", e)
            }
        }
    }
    
    /**
     * Check if Bluetooth connection is still active.
     */
    fun isConnected(): Boolean = isConnected && socket.isConnected
    
    /**
     * Get the connected device name.
     */
    fun getDeviceName(): String? = try {
        socket.remoteDevice?.name
    } catch (e: Exception) {
        null
    }
}

