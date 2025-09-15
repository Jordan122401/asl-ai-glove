package com.example.seniorproject.data

import kotlin.random.Random
import kotlinx.coroutines.delay
import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

class DemoSensorSource(
    private val context: Context,
    private val periodMs: Long = 300L, // how often to emit a sample
    private val csvAsset: String? = null
) : SensorSource {

    private val samples: List<FloatArray>
    private var i = 0

    init {
        samples = try {
            if (csvAsset != null) {
                context.assets.open(csvAsset).use { ins ->
                    BufferedReader(InputStreamReader(ins))
                        .readLines()
                        .filter { it.isNotBlank() }
                        .map { line ->
                            val parts = line.split(",")
                            floatArrayOf(parts[0].trim().toFloat(), parts[1].trim().toFloat())
                        }
                }.ifEmpty { defaultSamples() }
            } else {
                defaultSamples()
            }
        } catch (e: Exception) {
            android.util.Log.w("DemoSensorSource", "Could not read $csvAsset, using defaults: ${e.message}")
            defaultSamples()
        }
    }

    private fun defaultSamples(): List<FloatArray> = listOf(
        floatArrayOf(0.2f, -0.3f),
        floatArrayOf(0.8f,  0.1f),
        floatArrayOf(0.4f,  0.7f)
    )

    override suspend fun nextFeatures(): FloatArray? {
        kotlinx.coroutines.delay(periodMs)
        if (samples.isEmpty()) return null
        return samples[kotlin.random.Random.nextInt(samples.size)]
    }

    override suspend fun close() { /* nothing to close for demo */ }

}
