package com.example.seniorproject.data

import java.util.LinkedList

/**
 * Circular buffer for collecting sequences of sensor readings.
 * Maintains a sliding window of the most recent timesteps.
 * 
 * Usage:
 * ```
 * val buffer = SequenceBuffer(maxLength = 75, numFeatures = 10)
 * 
 * // Add samples as they arrive
 * buffer.add(floatArrayOf(0.34f, 0.77f, ...)) // 10 features
 * 
 * // Check if buffer is full
 * if (buffer.isFull()) {
 *     val sequence = buffer.getSequence()
 *     // Run inference on sequence
 * }
 * 
 * // Or get partial sequence if needed
 * val partialSequence = buffer.getSequence()
 * ```
 */
class SequenceBuffer(
    private val maxLength: Int = 75,
    private val numFeatures: Int = 10
) {
    private val buffer = LinkedList<FloatArray>()
    
    /**
     * Add a new sample to the buffer.
     * If buffer is full, oldest sample is removed (FIFO).
     * 
     * @param sample Feature vector of length numFeatures
     * @throws IllegalArgumentException if sample length doesn't match numFeatures
     */
    fun add(sample: FloatArray) {
        require(sample.size == numFeatures) {
            "Expected sample of length $numFeatures, got ${sample.size}"
        }
        
        // Add new sample
        buffer.add(sample.copyOf()) // Copy to avoid external mutations
        
        // Remove oldest if buffer exceeds max length
        if (buffer.size > maxLength) {
            buffer.removeFirst()
        }
    }
    
    /**
     * Get the current sequence as a 2D array.
     * Returns all samples currently in the buffer (may be less than maxLength).
     * 
     * @return Array<FloatArray> of shape [currentSize, numFeatures]
     */
    fun getSequence(): Array<FloatArray> {
        return buffer.toTypedArray()
    }
    
    /**
     * Get the sequence with padding/truncation to exact length.
     * If buffer has fewer than targetLength samples, pads with zeros.
     * If buffer has more than targetLength samples, takes the most recent.
     * 
     * @param targetLength Desired sequence length (default: maxLength)
     * @return Array<FloatArray> of shape [targetLength, numFeatures]
     */
    fun getPaddedSequence(targetLength: Int = maxLength): Array<FloatArray> {
        val currentSize = buffer.size
        
        return if (currentSize >= targetLength) {
            // Take most recent targetLength samples
            buffer.takeLast(targetLength).toTypedArray()
        } else {
            // Pad with zeros (post-padding to match training)
            val padded = Array(targetLength) { FloatArray(numFeatures) }
            buffer.forEachIndexed { index, sample ->
                padded[index] = sample.copyOf()
            }
            // Remaining positions are already zeros
            padded
        }
    }
    
    /**
     * Check if buffer has reached maximum capacity
     */
    fun isFull() = buffer.size >= maxLength
    
    /**
     * Get current number of samples in buffer
     */
    fun size() = buffer.size
    
    /**
     * Check if buffer is empty
     */
    fun isEmpty() = buffer.isEmpty()
    
    /**
     * Clear all samples from buffer
     */
    fun clear() {
        buffer.clear()
    }
    
    /**
     * Get the most recent sample (or null if empty)
     */
    fun getLatest(): FloatArray? {
        return buffer.lastOrNull()?.copyOf()
    }
    
    /**
     * Get statistics about the current buffer state
     */
    fun getStats(): BufferStats {
        if (buffer.isEmpty()) {
            return BufferStats(
                size = 0,
                capacity = maxLength,
                fillPercentage = 0f,
                featureAverages = FloatArray(numFeatures)
            )
        }
        
        // Compute average of each feature across all timesteps
        val featureAverages = FloatArray(numFeatures)
        buffer.forEach { sample ->
            for (i in 0 until numFeatures) {
                featureAverages[i] += sample[i]
            }
        }
        val count = buffer.size.toFloat()
        for (i in 0 until numFeatures) {
            featureAverages[i] /= count
        }
        
        return BufferStats(
            size = buffer.size,
            capacity = maxLength,
            fillPercentage = (buffer.size.toFloat() / maxLength) * 100f,
            featureAverages = featureAverages
        )
    }
    
    data class BufferStats(
        val size: Int,
        val capacity: Int,
        val fillPercentage: Float,
        val featureAverages: FloatArray
    ) {
        override fun toString(): String {
            return "BufferStats(size=$size/$capacity, ${fillPercentage.toInt()}% full, " +
                   "avgFeatures=${featureAverages.joinToString { "%.2f".format(it) }})"
        }
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as BufferStats
            
            if (size != other.size) return false
            if (capacity != other.capacity) return false
            if (fillPercentage != other.fillPercentage) return false
            if (!featureAverages.contentEquals(other.featureAverages)) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = size
            result = 31 * result + capacity
            result = 31 * result + fillPercentage.hashCode()
            result = 31 * result + featureAverages.contentHashCode()
            return result
        }
    }
}


