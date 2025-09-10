package com.example.seniorproject.data

interface SensorSource {
    /** Returns the next feature vector for inference (length must be 2 for your POC), or null if done */
    suspend fun nextFeatures(): FloatArray?
    suspend fun close() {}
}
