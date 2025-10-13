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
     * Creates realistic-looking patterns for ASL gestures.
     */
    private fun generateSyntheticSamples(): List<FloatArray> {
        val samples = mutableListOf<FloatArray>()
        
        // Generate patterns for different gestures
        // Pattern A: High flex1, low others
        repeat(20) {
            samples.add(floatArrayOf(
                0.8f + Random.nextFloat() * 0.1f,  // flex1
                0.2f + Random.nextFloat() * 0.1f,  // flex2
                0.3f + Random.nextFloat() * 0.1f,  // flex3
                0.3f + Random.nextFloat() * 0.1f,  // flex4
                0.3f + Random.nextFloat() * 0.1f,  // flex5
                45f + Random.nextFloat() * 10f,     // roll_deg
                10f + Random.nextFloat() * 5f,      // pitch_deg
                0.1f + Random.nextFloat() * 0.05f,  // ax_g
                -0.1f + Random.nextFloat() * 0.05f, // ay_g
                1.0f + Random.nextFloat() * 0.1f    // az_g
            ))
        }
        
        // Pattern B: All fingers flexed
        repeat(20) {
            samples.add(floatArrayOf(
                0.9f + Random.nextFloat() * 0.1f,  // flex1
                0.9f + Random.nextFloat() * 0.1f,  // flex2
                0.9f + Random.nextFloat() * 0.1f,  // flex3
                0.9f + Random.nextFloat() * 0.1f,  // flex4
                0.9f + Random.nextFloat() * 0.1f,  // flex5
                20f + Random.nextFloat() * 10f,     // roll_deg
                80f + Random.nextFloat() * 5f,      // pitch_deg
                0.0f + Random.nextFloat() * 0.05f,  // ax_g
                0.0f + Random.nextFloat() * 0.05f,  // ay_g
                1.0f + Random.nextFloat() * 0.1f    // az_g
            ))
        }
        
        // Pattern C: Curved hand
        repeat(20) {
            samples.add(floatArrayOf(
                0.5f + Random.nextFloat() * 0.1f,  // flex1
                0.5f + Random.nextFloat() * 0.1f,  // flex2
                0.5f + Random.nextFloat() * 0.1f,  // flex3
                0.5f + Random.nextFloat() * 0.1f,  // flex4
                0.5f + Random.nextFloat() * 0.1f,  // flex5
                60f + Random.nextFloat() * 10f,     // roll_deg
                45f + Random.nextFloat() * 5f,      // pitch_deg
                0.2f + Random.nextFloat() * 0.05f,  // ax_g
                0.1f + Random.nextFloat() * 0.05f,  // ay_g
                0.9f + Random.nextFloat() * 0.1f    // az_g
            ))
        }
        
        // Neutral position
        repeat(20) {
            samples.add(floatArrayOf(
                0.3f + Random.nextFloat() * 0.05f,  // flex1
                0.3f + Random.nextFloat() * 0.05f,  // flex2
                0.3f + Random.nextFloat() * 0.05f,  // flex3
                0.3f + Random.nextFloat() * 0.05f,  // flex4
                0.3f + Random.nextFloat() * 0.05f,  // flex5
                0f + Random.nextFloat() * 5f,       // roll_deg
                0f + Random.nextFloat() * 5f,       // pitch_deg
                0.0f + Random.nextFloat() * 0.02f,  // ax_g
                0.0f + Random.nextFloat() * 0.02f,  // ay_g
                1.0f + Random.nextFloat() * 0.05f   // az_g
            ))
        }
        
        Log.d(TAG, "Generated ${samples.size} synthetic samples")
        return samples
    }
    
    override suspend fun nextFeatures(): FloatArray? {
        delay(periodMs)
        
        if (samples.isEmpty()) {
            return null
        }
        
        // Sequential playback (cycles through all samples)
        val sample = samples[currentIndex]
        currentIndex = (currentIndex + 1) % samples.size
        
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
}


