package com.example.seniorproject.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.random.Random

/**
 * Demo sensor source for Fusion model that emits 10-feature vectors:
 * [flex1, flex2, flex3, flex4, flex5, roll_deg, pitch_deg, ax_g, ay_g, az_g]
 * 
 * Can load from CSV or generate synthetic data for testing.
 */
class FusionDemoSensorSource(
    private val context: Context,
    private val periodMs: Long = 50L,  // ~20 Hz sampling rate
    private val csvAsset: String? = "demo_samples.csv"
) : SensorSource {
    
    companion object {
        private const val NUM_FEATURES = 10
        private const val TAG = "FusionDemoSensor"
    }
    
    private val samples: List<FloatArray>
    private var currentIndex = 0
    
    init {
        samples = loadSamples()
        Log.d(TAG, "Loaded ${samples.size} samples with ${samples[0].size} features each")
    }
    
    private fun loadSamples(): List<FloatArray> {
        // Try to load from CSV
        if (csvAsset != null) {
            try {
                context.assets.open(csvAsset).use { ins ->
                    val lines = BufferedReader(InputStreamReader(ins))
                        .readLines()
                        .filter { it.isNotBlank() && !it.startsWith("#") }
                    
                    if (lines.isNotEmpty()) {
                        // Check if first line is header
                        val startIdx = if (lines[0].contains("flex") || lines[0].contains("roll")) 1 else 0
                        
                        val csvSamples = lines.drop(startIdx)
                            .mapNotNull { line ->
                                try {
                                    val parts = line.split(",").map { it.trim().toFloat() }
                                    if (parts.size >= NUM_FEATURES) {
                                        parts.take(NUM_FEATURES).toFloatArray()
                                    } else {
                                        null
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to parse line: $line")
                                    null
                                }
                            }
                        
                        if (csvSamples.isNotEmpty()) {
                            Log.d(TAG, "Loaded ${csvSamples.size} samples from $csvAsset")
                            return csvSamples
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not read $csvAsset: ${e.message}, using synthetic data")
            }
        }
        
        // Generate synthetic samples if CSV not available
        return generateSyntheticSamples()
    }
    
    /**
     * Generate synthetic sensor data for testing.
     * Creates realistic-looking patterns for ASL gestures with more variation.
     */
    private fun generateSyntheticSamples(): List<FloatArray> {
        val samples = mutableListOf<FloatArray>()
        
        // Generate more varied patterns for different gestures
        // Pattern A: Letter A - High flex1, low others (more pronounced)
        repeat(30) { i ->
            val progress = i / 30.0f
            samples.add(floatArrayOf(
                0.8f + Random.nextFloat() * 0.15f,   // flex1 - high (0.8-0.95)
                0.1f + Random.nextFloat() * 0.1f,    // flex2 - very low (0.1-0.2)
                0.1f + Random.nextFloat() * 0.1f,    // flex3 - very low (0.1-0.2)
                0.1f + Random.nextFloat() * 0.1f,    // flex4 - very low (0.1-0.2)
                0.1f + Random.nextFloat() * 0.1f,    // flex5 - very low (0.1-0.2)
                20f + Random.nextFloat() * 20f,      // roll_deg (20-40)
                10f + Random.nextFloat() * 10f,      // pitch_deg (10-20)
                0.0f + Random.nextFloat() * 0.2f,    // ax_g (-0.1 to 0.1)
                0.0f + Random.nextFloat() * 0.2f,    // ay_g (-0.1 to 0.1)
                1.0f + Random.nextFloat() * 0.1f     // az_g (1.0-1.1)
            ))
        }
        
        // Pattern B: Letter B - All fingers extended (low flex values)
        repeat(30) { i ->
            samples.add(floatArrayOf(
                0.1f + Random.nextFloat() * 0.1f,    // flex1 - very low (0.1-0.2)
                0.1f + Random.nextFloat() * 0.1f,    // flex2 - very low (0.1-0.2)
                0.1f + Random.nextFloat() * 0.1f,    // flex3 - very low (0.1-0.2)
                0.1f + Random.nextFloat() * 0.1f,    // flex4 - very low (0.1-0.2)
                0.1f + Random.nextFloat() * 0.1f,    // flex5 - very low (0.1-0.2)
                0f + Random.nextFloat() * 10f,       // roll_deg (0-10)
                60f + Random.nextFloat() * 20f,      // pitch_deg (60-80)
                0.0f + Random.nextFloat() * 0.2f,    // ax_g (-0.1 to 0.1)
                0.0f + Random.nextFloat() * 0.2f,    // ay_g (-0.1 to 0.1)
                1.0f + Random.nextFloat() * 0.1f     // az_g (1.0-1.1)
            ))
        }
        
        // Pattern C: Letter C - Curved hand (medium flex values)
        repeat(30) { i ->
            samples.add(floatArrayOf(
                0.5f + Random.nextFloat() * 0.2f,    // flex1 - medium (0.5-0.7)
                0.5f + Random.nextFloat() * 0.2f,    // flex2 - medium (0.5-0.7)
                0.5f + Random.nextFloat() * 0.2f,    // flex3 - medium (0.5-0.7)
                0.5f + Random.nextFloat() * 0.2f,    // flex4 - medium (0.5-0.7)
                0.5f + Random.nextFloat() * 0.2f,    // flex5 - medium (0.5-0.7)
                40f + Random.nextFloat() * 20f,      // roll_deg (40-60)
                30f + Random.nextFloat() * 20f,      // pitch_deg (30-50)
                0.1f + Random.nextFloat() * 0.2f,    // ax_g (0.0 to 0.3)
                0.1f + Random.nextFloat() * 0.2f,    // ay_g (0.0 to 0.3)
                0.9f + Random.nextFloat() * 0.1f     // az_g (0.9-1.0)
            ))
        }
        
        // Pattern D: Letter D - Index finger extended (high flex2, low others)
        repeat(30) { i ->
            samples.add(floatArrayOf(
                0.2f + Random.nextFloat() * 0.1f,    // flex1 - low (0.2-0.3)
                0.8f + Random.nextFloat() * 0.15f,   // flex2 - high (0.8-0.95)
                0.2f + Random.nextFloat() * 0.1f,    // flex3 - low (0.2-0.3)
                0.2f + Random.nextFloat() * 0.1f,    // flex4 - low (0.2-0.3)
                0.2f + Random.nextFloat() * 0.1f,    // flex5 - low (0.2-0.3)
                15f + Random.nextFloat() * 15f,      // roll_deg (15-30)
                45f + Random.nextFloat() * 15f,      // pitch_deg (45-60)
                0.0f + Random.nextFloat() * 0.2f,    // ax_g (-0.1 to 0.1)
                0.0f + Random.nextFloat() * 0.2f,    // ay_g (-0.1 to 0.1)
                1.0f + Random.nextFloat() * 0.1f     // az_g (1.0-1.1)
            ))
        }
        
        // Neutral position (more varied)
        repeat(20) {
            samples.add(floatArrayOf(
                0.3f + Random.nextFloat() * 0.1f,    // flex1 - neutral (0.3-0.4)
                0.3f + Random.nextFloat() * 0.1f,    // flex2 - neutral (0.3-0.4)
                0.3f + Random.nextFloat() * 0.1f,    // flex3 - neutral (0.3-0.4)
                0.3f + Random.nextFloat() * 0.1f,    // flex4 - neutral (0.3-0.4)
                0.3f + Random.nextFloat() * 0.1f,    // flex5 - neutral (0.3-0.4)
                0f + Random.nextFloat() * 10f,       // roll_deg (0-10)
                0f + Random.nextFloat() * 10f,       // pitch_deg (0-10)
                0.0f + Random.nextFloat() * 0.1f,    // ax_g (-0.05 to 0.05)
                0.0f + Random.nextFloat() * 0.1f,    // ay_g (-0.05 to 0.05)
                1.0f + Random.nextFloat() * 0.05f    // az_g (1.0-1.05)
            ))
        }
        
        Log.d(TAG, "Generated ${samples.size} varied synthetic samples")
        return samples
    }
    
    override suspend fun nextFeatures(): FloatArray? {
        delay(periodMs)
        
        if (samples.isEmpty()) {
            return null
        }
        
        // Sequential playback (cycles through all samples)
        // Safety check to prevent IndexOutOfBoundsException
        if (currentIndex >= samples.size) {
            Log.w(TAG, "Current index $currentIndex is out of bounds (samples: ${samples.size}), resetting to 0")
            currentIndex = 0
        }
        
        val sample = samples[currentIndex]
        currentIndex = (currentIndex + 1) % samples.size
        
        // Log which pattern we're currently using
        val samplesPerPattern = samples.size / 5
        val patternType = when {
            currentIndex < samplesPerPattern -> "Pattern A"
            currentIndex < samplesPerPattern * 2 -> "Pattern B" 
            currentIndex < samplesPerPattern * 3 -> "Pattern C"
            currentIndex < samplesPerPattern * 4 -> "Pattern D"
            else -> "Neutral"
        }
        
        Log.d(TAG, "Sample $currentIndex/$samples.size ($patternType): ${sample.joinToString { "%.2f".format(it) }}")
        
        return sample.copyOf()
    }
    
    override suspend fun nextSequence(maxLength: Int): Array<FloatArray>? {
        // Collect a sequence of samples
        val sequence = mutableListOf<FloatArray>()
        
        repeat(maxLength) {
            val sample = nextFeatures() ?: return if (sequence.isEmpty()) null else sequence.toTypedArray()
            sequence.add(sample)
        }
        
        return sequence.toTypedArray()
    }
    
    override suspend fun close() {
        // Nothing to clean up for demo source
    }
    
    /**
     * Reset to beginning of sample sequence
     */
    fun reset() {
        currentIndex = 0
    }
    
    /**
     * Force jump to a specific pattern for testing
     */
    fun jumpToPattern(patternIndex: Int) {
        if (samples.isEmpty()) {
            Log.w(TAG, "Cannot jump to pattern - no samples available")
            return
        }
        
        // Calculate safe indices based on actual sample count
        val samplesPerPattern = samples.size / 5  // Divide into 5 patterns
        val safeIndex = when (patternIndex) {
            0 -> 0                           // Pattern A - start
            1 -> samplesPerPattern           // Pattern B
            2 -> samplesPerPattern * 2       // Pattern C
            3 -> samplesPerPattern * 3       // Pattern D
            4 -> samplesPerPattern * 4       // Neutral
            else -> 0
        }
        
        // Ensure index is within bounds
        currentIndex = minOf(safeIndex, samples.size - 1)
        Log.d(TAG, "Jumped to pattern $patternIndex at index $currentIndex (samples: ${samples.size}, per pattern: $samplesPerPattern)")
    }
}


