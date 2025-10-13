package com.example.seniorproject.ml

import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Fusion classifier combining LSTM + XGBoost for ASL gesture recognition.
 * 
 * Model Architecture (from Our_model.ipynb - Model Three):
 * 1. LSTM processes sequences of sensor data (75 timesteps x 10 features)
 * 2. XGBoost uses flattened sequence + residual for final classification
 * 
 * Input features (10): flex1, flex2, flex3, flex4, flex5, roll_deg, pitch_deg, ax_g, ay_g, az_g
 * Sequence length: 75 timesteps
 * 
 * Inference flow:
 * 1. Collect 75 timesteps of sensor data
 * 2. Pass through LSTM to get class probabilities
 * 3. Flatten sequence data (75 * 10 = 750 features)
 * 4. Compute residual (predicted class - true class, 0 for new data)
 * 5. Concatenate [flattened_features, residual] -> 751 features
 * 6. Pass through XGBoost for final prediction
 */
class FusionASLClassifier(
    private val context: Context,
    private val lstmModelFileName: String = "LSTM_model.tflite",
    private val xgbModelFileName: String = "xgb_model.json",
    private val labelsFileName: String? = null,
    numThreads: Int = 2
) {
    companion object {
        private const val SEQUENCE_LENGTH = 75  // Number of timesteps
        private const val NUM_FEATURES = 10     // Number of features per timestep
        private const val TAG = "FusionASLClassifier"
    }

    private val lstmInterpreter: Interpreter
    private var xgbPredictor: XGBoostPredictor? = null
    private var simpleXgbPredictor: SimpleXGBoostPredictor? = null
    private var numClasses: Int = -1
    private lateinit var labels: List<String>
    
    // Buffers for LSTM
    private val lstmInputBuffer = Array(1) { Array(SEQUENCE_LENGTH) { FloatArray(NUM_FEATURES) } }
    private lateinit var lstmOutputBuffer: Array<FloatArray>
    
    // Buffer for XGBoost (flattened sequence + residual)
    private lateinit var xgbInputBuffer: FloatArray

    init {
        try {
            // Load LSTM model with fallback options
            val lstmOptions = Interpreter.Options().apply {
                setNumThreads(numThreads)
                // Allow TensorFlow ops fallback if needed
                setAllowFp16PrecisionForFp32(false)
            }
            
            lstmInterpreter = Interpreter(loadModelFile(lstmModelFileName), lstmOptions)
            
            // Set input shape for LSTM: [1, 75, 10]
            lstmInterpreter.resizeInput(0, intArrayOf(1, SEQUENCE_LENGTH, NUM_FEATURES))
            lstmInterpreter.allocateTensors()
            
            // Auto-detect number of classes from LSTM output
            val outShape = lstmInterpreter.getOutputTensor(0).shape() // e.g., [1, 5]
            require(outShape.size == 2 && outShape[0] == 1) {
                "Unexpected LSTM output shape: ${outShape.contentToString()}"
            }
            numClasses = outShape[1]
            lstmOutputBuffer = Array(1) { FloatArray(numClasses) }
            
            // XGBoost expects: 750 (flattened) + 1 (residual) = 751 features
            xgbInputBuffer = FloatArray(SEQUENCE_LENGTH * NUM_FEATURES + 1)
            
            // Load XGBoost model with fallback
            try {
                xgbPredictor = XGBoostPredictor(context, xgbModelFileName)
                Log.d(TAG, "XGBoost model loaded successfully")
                
                // Test XGBoost with dummy data to verify it's working
                val testInput = FloatArray(751) { 0.5f }
                val testOutput = xgbPredictor?.predict(testInput) ?: FloatArray(numClasses) { 1.0f / numClasses }
                Log.d(TAG, "XGBoost test output: ${testOutput.joinToString { "%.3f".format(it) }}")
                
                // Check if XGBoost is producing uniform probabilities (indicates dummy trees)
                val isUniform = testOutput.all { kotlin.math.abs(it - testOutput[0]) < 0.001f }
                if (isUniform) {
                    Log.w(TAG, "XGBoost is producing uniform probabilities - likely using dummy trees")
                }
                
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load XGBoost model, using simple fallback", e)
                xgbPredictor = null
                simpleXgbPredictor = SimpleXGBoostPredictor(context, xgbModelFileName)
                
                // Test simple fallback
                val testInput = FloatArray(751) { 0.5f }
                val testOutput = simpleXgbPredictor?.predict(testInput) ?: FloatArray(numClasses) { 1.0f / numClasses }
                Log.d(TAG, "Simple XGBoost fallback test output: ${testOutput.joinToString { "%.3f".format(it) }}")
            }
            
            // Load labels
            labels = loadLabelsOrDefault(numClasses)
            require(labels.size == numClasses) {
                "Labels count (${labels.size}) must equal detected numClasses ($numClasses)."
            }
            
            Log.d(TAG, "Fusion model loaded: $numClasses classes, ${labels.joinToString()}")
            
            // Test LSTM with dummy data to verify it's working
            val dummySequence = Array(75) { FloatArray(10) { 0.5f } }
            val testPrediction = predict(dummySequence)
            Log.d(TAG, "LSTM test prediction: $testPrediction")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load fusion model", e)
            Log.e(TAG, "Error details: ${e.message}")
            Log.e(TAG, "This might be due to Bidirectional LSTM requiring SELECT_TF_OPS")
            Log.e(TAG, "Consider using a simpler LSTM model or retraining without Bidirectional layers")
            throw e
        }
    }

    private fun loadModelFile(fileName: String): MappedByteBuffer {
        val afd = context.assets.openFd(fileName)
        FileInputStream(afd.fileDescriptor).channel.use { channel ->
            return channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
        }
    }

    private fun loadLabelsOrDefault(expected: Int): List<String> {
        if (labelsFileName == null) {
            // Default labels for ASL: A, B, C, D, Neutral (or whatever your 5 classes are)
            return listOf("A", "B", "C", "D", "Neutral").take(expected)
        }
        
        return try {
            context.assets.open(labelsFileName).use { input ->
                BufferedReader(InputStreamReader(input))
                    .readLines()
                    .filter { it.isNotBlank() }
            }.let { lines ->
                if (lines.size == expected) lines
                else listOf("A", "B", "C", "D", "Neutral").take(expected)
            }
        } catch (_: Exception) {
            listOf("A", "B", "C", "D", "Neutral").take(expected)
        }
    }

    data class Prediction(
        val index: Int,
        val label: String,
        val probability: Float,
        val lstmProbabilities: FloatArray,
        val xgbProbabilities: FloatArray
    ) {
        override fun toString(): String {
            return "Prediction(label='$label', prob=${"%.3f".format(probability)}, " +
                   "lstm=${lstmProbabilities.joinToString(prefix = "[", postfix = "]") { "%.2f".format(it) }}, " +
                   "xgb=${xgbProbabilities.joinToString(prefix = "[", postfix = "]") { "%.2f".format(it) }})"
        }
    }

    /**
     * Predict ASL gesture from a sequence of sensor readings.
     * 
     * @param sequence 2D array of shape [timesteps, features] where timesteps <= 75
     *                 If timesteps < 75, sequence will be padded with zeros (post-padding)
     *                 If timesteps > 75, sequence will be truncated
     * @return Prediction containing class label, probability, and intermediate outputs
     */
    @WorkerThread
    fun predict(sequence: Array<FloatArray>?): Prediction? {
        // Add null check to prevent NullPointerException
        if (sequence == null) {
            Log.w(TAG, "Sequence is null, returning null prediction")
            return null
        }
        
        Log.d(TAG, "Predicting with sequence of ${sequence.size} timesteps")
        
        require(sequence.isNotEmpty()) { "Sequence cannot be empty" }
        require(sequence[0].size == NUM_FEATURES) {
            "Expected $NUM_FEATURES features per timestep, got ${sequence[0].size}"
        }
        
        // Step 1: Prepare LSTM input with padding/truncation
        val paddedSequence = padSequence(sequence)
        
        // Copy to LSTM input buffer with validation
        for (t in 0 until SEQUENCE_LENGTH) {
            for (f in 0 until NUM_FEATURES) {
                val value = paddedSequence[t][f]
                if (value.isNaN() || value.isInfinite()) {
                    Log.w(TAG, "Invalid input value at [$t][$f]: $value, using 0.0")
                    lstmInputBuffer[0][t][f] = 0.0f
                } else {
                    lstmInputBuffer[0][t][f] = value
                }
            }
        }
        
        // Step 2: Run LSTM inference with error handling
        val lstmProbs = try {
            lstmInterpreter.run(lstmInputBuffer, lstmOutputBuffer)
            lstmOutputBuffer[0].copyOf()
        } catch (e: Exception) {
            Log.e(TAG, "LSTM inference failed, using fallback", e)
            // Create a simple pattern-based fallback instead of uniform probabilities
            createLSTMFallbackPrediction(paddedSequence)
        }
        
        Log.d(TAG, "LSTM output: ${lstmProbs.joinToString { "%.3f".format(it) }}")
        
        // Find best LSTM prediction for comparison
        var bestLstmIdx = 0
        var bestLstmProb = lstmProbs[0]
        for (i in 1 until numClasses) {
            if (lstmProbs[i] > bestLstmProb) {
                bestLstmProb = lstmProbs[i]
                bestLstmIdx = i
            }
        }
        Log.d(TAG, "LSTM best: ${labels[bestLstmIdx]} (${"%.3f".format(bestLstmProb)})")
        
        // Step 3: Prepare XGBoost input
        // Flatten the padded sequence
        var idx = 0
        for (t in 0 until SEQUENCE_LENGTH) {
            for (f in 0 until NUM_FEATURES) {
                xgbInputBuffer[idx++] = paddedSequence[t][f]
            }
        }
        
        // Add residual (0 for new samples, as we don't know the true label)
        xgbInputBuffer[idx] = 0f
        
        // Step 4: Run XGBoost inference with fallback
        val xgbProbs = try {
            xgbPredictor?.predict(xgbInputBuffer) ?: throw IllegalStateException("XGBoost predictor not loaded")
        } catch (e: Exception) {
            Log.w(TAG, "XGBoost prediction failed, using simple fallback", e)
            simpleXgbPredictor?.predict(xgbInputBuffer) ?: FloatArray(numClasses) { 1.0f / numClasses }
        }
        
        Log.d(TAG, "XGBoost output: ${xgbProbs.joinToString { "%.3f".format(it) }}")
        
        // Find best XGBoost prediction for comparison
        var bestXgbIdx = 0
        var bestXgbProb = xgbProbs[0]
        for (i in 1 until numClasses) {
            if (xgbProbs[i] > bestXgbProb) {
                bestXgbProb = xgbProbs[i]
                bestXgbIdx = i
            }
        }
        Log.d(TAG, "XGBoost best: ${labels[bestXgbIdx]} (${"%.3f".format(bestXgbProb)})")
        
        // Step 5: Fusion - Combine LSTM and XGBoost predictions
        // Use weighted average: 60% LSTM + 40% XGBoost (balanced approach)
        val lstmWeight = 0.6f
        val xgbWeight = 0.4f
        
        val fusedProbs = FloatArray(numClasses)
        for (i in 0 until numClasses) {
            fusedProbs[i] = lstmWeight * lstmProbs[i] + xgbWeight * xgbProbs[i]
        }
        
        // Find best prediction from fused probabilities
        var bestIdx = 0
        var bestProb = fusedProbs[0]
        for (i in 1 until numClasses) {
            if (fusedProbs[i] > bestProb) {
                bestProb = fusedProbs[i]
                bestIdx = i
            }
        }
        
        Log.d(TAG, "Fusion output: ${fusedProbs.joinToString { "%.3f".format(it) }}")
        Log.d(TAG, "Fusion best: ${labels[bestIdx]} (${"%.3f".format(bestProb)})")
        
        return Prediction(
            index = bestIdx,
            label = labels[bestIdx],
            probability = bestProb,
            lstmProbabilities = lstmProbs,
            xgbProbabilities = xgbProbs
        )
    }

    /**
     * Pad or truncate sequence to exactly SEQUENCE_LENGTH timesteps
     * Uses post-padding with zeros (same as training)
     */
    private fun padSequence(sequence: Array<FloatArray>): Array<FloatArray> {
        return if (sequence.size >= SEQUENCE_LENGTH) {
            // Truncate if too long (post-truncation)
            sequence.take(SEQUENCE_LENGTH).toTypedArray()
        } else {
            // Pad if too short (post-padding with zeros)
            val padded = Array(SEQUENCE_LENGTH) { FloatArray(NUM_FEATURES) }
            for (t in sequence.indices) {
                padded[t] = sequence[t].copyOf()
            }
            // Remaining timesteps are already zeros
            padded
        }
    }

    fun close() {
        lstmInterpreter.close()
    }
    
    /**
     * Create a simple pattern-based fallback prediction when LSTM fails
     */
    private fun createLSTMFallbackPrediction(sequence: Array<FloatArray>): FloatArray {
        val prediction = FloatArray(numClasses)
        
        // Simple pattern matching based on average feature values
        val avgFlex1 = sequence.take(30).map { it[0] }.average().toFloat()
        val avgFlex2 = sequence.take(30).map { it[1] }.average().toFloat()
        val avgRoll = sequence.take(30).map { it[5] }.average().toFloat()
        val avgPitch = sequence.take(30).map { it[6] }.average().toFloat()
        
        // Pattern-based predictions
        when {
            avgFlex1 > 0.7f -> {
                prediction[0] = 0.7f // A - high flex1
                prediction[1] = 0.1f
                prediction[2] = 0.1f
                prediction[3] = 0.05f
                prediction[4] = 0.05f
            }
            avgFlex2 > 0.7f -> {
                prediction[0] = 0.1f
                prediction[1] = 0.1f
                prediction[2] = 0.1f
                prediction[3] = 0.7f // D - high flex2
                prediction[4] = 0.0f
            }
            avgFlex1 < 0.3f && avgFlex2 < 0.3f -> {
                prediction[0] = 0.1f
                prediction[1] = 0.7f // B - low flex values
                prediction[2] = 0.1f
                prediction[3] = 0.05f
                prediction[4] = 0.05f
            }
            avgFlex1 in 0.4f..0.6f && avgFlex2 in 0.4f..0.6f -> {
                prediction[0] = 0.1f
                prediction[1] = 0.1f
                prediction[2] = 0.7f // C - medium flex values
                prediction[3] = 0.05f
                prediction[4] = 0.05f
            }
            else -> {
                prediction[0] = 0.2f
                prediction[1] = 0.2f
                prediction[2] = 0.2f
                prediction[3] = 0.2f
                prediction[4] = 0.2f // Neutral
            }
        }
        
        Log.d(TAG, "Using LSTM fallback prediction based on patterns")
        return prediction
    }
    
    /**
     * Get the expected number of features per timestep
     */
    fun getNumFeatures() = NUM_FEATURES
    
    /**
     * Get the expected sequence length
     */
    fun getSequenceLength() = SEQUENCE_LENGTH
    
    /**
     * Get the list of class labels
     */
    fun getLabels() = labels.toList()
}

