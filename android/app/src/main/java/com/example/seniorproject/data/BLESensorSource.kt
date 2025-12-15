package com.example.seniorproject.data

import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * BLE Sensor source that receives streaming data from the ASL glove via BLEService.
 * 
 * Compatible with ASL_BLE_FINAL_frfrfrfr firmware which sends windowed batches:
 * - Exactly 75 samples per batch
 * - 1-second gap between batches (handled by firmware)
 * - Header line before each batch: "flex1,flex2,flex3,flex4,flex5,roll_deg,pitch_deg,ax_g,ay_g,az_g"
 * 
 * Expected data format from glove (CSV, 10 features per line, no time prefix):
 * flex1,flex2,flex3,flex4,flex5,roll_deg,pitch_deg,ax_g,ay_g,az_g
 * 
 * Example: 0.7523,0.2341,0.1234,0.1543,0.0832,25.34,12.10,0.0523,-0.0231,1.0123
 * 
 * This source implements SensorSource interface and buffers incoming BLE data
 * for real-time inference processing. Header lines (starting with # or matching CSV header)
 * are automatically skipped.
 */
class BLESensorSource : SensorSource {
    
    companion object {
        private const val TAG = "BLESensorSource"
        private const val NUM_FEATURES = 10
    }
    
    private val dataQueue = ConcurrentLinkedQueue<FloatArray>()
    private val lineBuffer = StringBuilder()
    @Volatile
    private var isActive = false
    
    /**
     * Called by BLEService when new data is received from the glove.
     * Parses CSV format and adds to internal queue.
     */
    fun onDataReceived(data: String) {
        if (!isActive) return

        // Accumulate into buffer and process complete lines only
        synchronized(lineBuffer) {
            lineBuffer.append(data)
            // Normalize CRLF to LF to simplify splitting
            val normalized = lineBuffer.toString().replace("\r\n", "\n").replace("\r", "\n")
            val parts = normalized.split("\n")
            // Keep the last part in the buffer if it's not a complete line
            lineBuffer.setLength(0)
            val last = parts.last()
            if (last.isNotEmpty()) {
                lineBuffer.append(last)
            }

            // Process all complete lines (all except last)
            for (i in 0 until parts.size - 1) {
                val trimmed = parts[i].trim()
            
            // Skip empty lines, comments, or non-data lines
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("$")) {
                continue
            }
            
            // Skip CSV header lines (e.g., "flex1,flex2,flex3,flex4,flex5,roll_deg,pitch_deg,ax_g,ay_g,az_g")
            if (trimmed.contains("flex") || trimmed.contains("roll_deg") || trimmed.contains("pitch_deg")) {
                Log.d(TAG, "Skipping CSV header line: $trimmed")
                continue
            }
                // Parse CSV line
                val tokens = trimmed.split(",").map { it.trim() }
            
            // Handle both formats: with time (11 values) or without (10 values)
            // If 11 values, first is time - skip it. If 10, use all.
                val startIndex = if (tokens.size == NUM_FEATURES + 1) {
                1 // Skip time value
                } else if (tokens.size == NUM_FEATURES) {
                0 // Use all values
                } else {
                    Log.w(TAG, "Unexpected data format: expected $NUM_FEATURES or ${NUM_FEATURES + 1} values, got ${tokens.size}: $trimmed")
                continue
                }
            
            try {
                val features = FloatArray(NUM_FEATURES)
                var parseError = false
                for (iFeature in 0 until NUM_FEATURES) {
                    val partIndex = startIndex + iFeature
                    val value = tokens[partIndex].toFloatOrNull()
                    if (value == null) {
                        // If first parsing fails, might be header - check if it's text
                        if (!tokens[partIndex].matches(Regex("-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?"))) {
                            Log.d(TAG, "Skipping non-numeric line (likely header): $trimmed")
                            parseError = true
                            break
                        }
                        Log.w(TAG, "Failed to parse value at index $partIndex: ${tokens.getOrNull(partIndex)}")
                        parseError = true
                        break
                    }
                    features[iFeature] = value
                }
                
                if (parseError) continue
                
                // Add to queue for processing
                dataQueue.offer(features)
                Log.d(TAG, "Parsed and queued sensor data: ${features.joinToString { "%.2f".format(it) }}")
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing sensor data line: $trimmed", e)
            }
            }
        }
    }
    
    /**
     * Get the next feature vector from the queue.
     * Returns null if queue is empty or source is inactive.
     */
    override suspend fun nextFeatures(): FloatArray? {
        return synchronized(dataQueue) {
            dataQueue.poll()
        }
    }
    
    /**
     * Enable/disable this sensor source.
     */
    fun setActive(active: Boolean) {
        isActive = active
        if (!active) {
            // Clear queue when stopping
            synchronized(dataQueue) {
                dataQueue.clear()
            }
        }
        Log.d(TAG, "Sensor source ${if (active) "activated" else "deactivated"}")
    }
    
    /**
     * Check if source is currently active.
     */
    fun isActive(): Boolean = isActive
    
    /**
     * Get current queue size (number of pending readings).
     */
    fun queueSize(): Int = dataQueue.size
    
    /**
     * Clear all queued data.
     */
    override suspend fun close() {
        setActive(false)
        Log.d(TAG, "BLESensorSource closed")
    }
}

