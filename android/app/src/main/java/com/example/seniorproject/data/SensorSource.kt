package com.example.seniorproject.data

interface SensorSource {
    /** 
     * Returns the next feature vector for inference.
     * For fusion model: should return 10 features [flex1-5, roll, pitch, ax, ay, az]
     * For POC model: should return 2 features
     */
    suspend fun nextFeatures(): FloatArray?
    
    /**
     * Returns the next complete sequence for fusion model inference.
     * Should return Array<FloatArray> of shape [timesteps, features]
     * where timesteps can be <= 75 (will be padded if needed)
     */
    suspend fun nextSequence(maxLength: Int = 75): Array<FloatArray>? {
        // Default implementation: collect single samples into sequence
        val sequence = mutableListOf<FloatArray>()
        repeat(maxLength) {
            val features = nextFeatures() ?: return if (sequence.isEmpty()) null else sequence.toTypedArray()
            sequence.add(features)
        }
        return sequence.toTypedArray()
    }
    
    suspend fun close() {}
}
