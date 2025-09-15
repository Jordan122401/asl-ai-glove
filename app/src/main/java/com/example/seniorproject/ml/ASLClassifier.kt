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
 * Minimal TFLite/LiteRT wrapper for single inferences.
 * - INPUT_SIZE: number of features our model expects (2 for Proof of Concept).
 * - Output classes are auto-detected from the model's output tensor.
 */
class ASLClassifier(
    private val context: Context,
    private val modelFileName: String = "asl_model.tflite",
    private val labelsFileName: String? = "labels.txt",
    numThreads: Int = 2
) {
    // Update these to our real values later
    private val INPUT_SIZE = 2
    private var numClasses: Int = -1
    private lateinit var outputBuffer: Array<FloatArray>
    // If we standardized in training, paste means/stds (length = INPUT_SIZE)
    private val MEANS = FloatArray(INPUT_SIZE) { 0f }
    private val STDS  = FloatArray(INPUT_SIZE) { 1f }
    // ===========================================

    private val interpreter: Interpreter
    private lateinit var labels: List<String>
    private val inputBuffer = Array(1) { FloatArray(INPUT_SIZE) }

    init {

        val options = Interpreter.Options().apply {
            setNumThreads(1)
            setUseXNNPACK(false)
        }

        try {
            interpreter = Interpreter(loadModelFile(modelFileName), options)

            // Explicitly set the expected input shape: [1, INPUT_SIZE]
            interpreter.resizeInput(0, intArrayOf(1, INPUT_SIZE))
            interpreter.allocateTensors()

            // Auto-detect output shape/classes and allocate output buffer
            val outShape = interpreter.getOutputTensor(0).shape() // e.g., [1, 3]
            require(outShape.size == 2 && outShape[0] == 1) {
                "Unexpected output shape: ${outShape.contentToString()}"
            }
            numClasses = outShape[1]
            outputBuffer = Array(1) { FloatArray(numClasses) }

            // Load labels to match detected numClasses (fallback to A..Z style)
            labels = loadLabelsOrAlphabet(numClasses)
            require(labels.size == numClasses) {
                "Labels count (${labels.size}) must equal detected numClasses ($numClasses)."
            }
        } catch (e: Exception) {
            Log.e("ASLClassifier", "Failed to load/prepare model $modelFileName", e)
            throw e
        }
    }

    private fun loadModelFile(fileName: String): MappedByteBuffer {
        val afd = context.assets.openFd(fileName)
        FileInputStream(afd.fileDescriptor).channel.use { channel ->
            return channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
        }
    }

    // Accept expected count so labels match model output
    private fun loadLabelsOrAlphabet(expected: Int): List<String> {
        labelsFileName ?: return (0 until expected).map { ('A' + it).toString() }
        return try {
            context.assets.open(labelsFileName).use { input ->
                BufferedReader(InputStreamReader(input))
                    .readLines()
                    .filter { it.isNotBlank() }
            }.let { lines ->
                if (lines.size == expected) lines
                else (0 until expected).map { ('A' + it).toString() }
            }
        } catch (_: Exception) {
            (0 until expected).map { ('A' + it).toString() }
        }
    }

    // Normalize features exactly like training: (x - mean) / std
    private fun normalizeInPlace(features: FloatArray) {
        for (i in features.indices) {
            val std = if (STDS[i] == 0f) 1f else STDS[i]
            features[i] = (features[i] - MEANS[i]) / std
        }
    }

    data class Prediction(val index: Int, val label: String, val probability: Float)

    /**
     * Run inference on a single feature vector.
     * @param rawFeatures FloatArray of size INPUT_SIZE.
     */
    @WorkerThread
    fun predict(rawFeatures: FloatArray): Prediction {
        require(rawFeatures.size == INPUT_SIZE) {
            "Expected feature length $INPUT_SIZE, got ${rawFeatures.size}"
        }

        // Copy + normalize
        val f = rawFeatures.copyOf()
        normalizeInPlace(f)
        for (i in 0 until INPUT_SIZE) inputBuffer[0][i] = f[i]

        // Inference
        interpreter.run(inputBuffer, outputBuffer)

        // Argmax
        val probs = outputBuffer[0]
        var bestIdx = 0
        var best = Float.NEGATIVE_INFINITY
        for (i in probs.indices) {
            if (probs[i] > best) {
                best = probs[i]
                bestIdx = i
            }
        }
        return Prediction(bestIdx, labels[bestIdx], best)
    }

    fun close() = interpreter.close()
}
