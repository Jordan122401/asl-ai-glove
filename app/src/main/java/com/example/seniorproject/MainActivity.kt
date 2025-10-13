package com.example.seniorproject

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.*
import com.example.seniorproject.ml.ASLClassifier
import com.example.seniorproject.ml.FusionASLClassifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import com.example.seniorproject.data.DemoSensorSource
import com.example.seniorproject.data.FusionDemoSensorSource
import com.example.seniorproject.data.SequenceBuffer

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var editTextInput: EditText
    private lateinit var textViewResult: TextView
    private lateinit var buttonConnect: Button
    private lateinit var buttonSettings: Button
    private lateinit var bluetoothButton: Button
    private lateinit var clearTextButton: Button
    private lateinit var tts: TextToSpeech
    
    // Demo control UI elements
    private lateinit var startDemoButton: Button
    private lateinit var stopDemoButton: Button
    private lateinit var demoStatusText: TextView
    
    // Option 1: Use old POC classifier (2 features)
    // private lateinit var classifier: ASLClassifier
    // private lateinit var demo: DemoSensorSource
    
    // Option 2: Use new Fusion classifier (10 features, 75 timesteps)
    private lateinit var fusionClassifier: FusionASLClassifier
    private lateinit var fusionDemo: FusionDemoSensorSource
    private lateinit var sequenceBuffer: SequenceBuffer
    
    private lateinit var inputEdit: EditText
    private var ttsEnabled = true
    private var fontSize = 20
    private val BLUETOOTH_PERMISSION_REQUEST = 1001
    private val USE_FUSION_MODEL = true  // Toggle between POC and Fusion model

    private var streamJob: Job? = null
    private var demoJob: Job? = null
    private var isDemoRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        inputEdit = findViewById(R.id.inputText)
        
        // Initialize model based on flag
        if (USE_FUSION_MODEL) {
            initFusionModel()
        } else {
            // Keep old POC model for backward compatibility
            // classifier = ASLClassifier(this)
            // demo = DemoSensorSource(this, periodMs = 300L, csvAsset = "demo_samples.csv")
            // startPOCStream()
            Toast.makeText(this, "POC model disabled. Set USE_FUSION_MODEL=false to enable.", Toast.LENGTH_SHORT).show()
        }


        // Adjust for system bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize views
        editTextInput = findViewById(R.id.inputText)
        textViewResult = findViewById(R.id.connectedText)
        buttonConnect = findViewById(R.id.connectButton)
        buttonSettings = findViewById(R.id.settingsButton)
        bluetoothButton = findViewById(R.id.checkBluetoothButton)
        clearTextButton = findViewById(R.id.clearTextButton)
        
        // Initialize demo control views
        startDemoButton = findViewById(R.id.startDemoButton)
        stopDemoButton = findViewById(R.id.stopDemoButton)
        demoStatusText = findViewById(R.id.demoStatusText)

        // Initialize TTS
        tts = TextToSpeech(this, this)

        // Connect Text button: keep your existing behavior (concat + TTS)
        buttonConnect.setOnClickListener {
            val lines = editTextInput.text.toString().split("\n")
            val connected = lines.joinToString(" ") { it.replace(" ", "") }
            textViewResult.text = connected
            textViewResult.textSize = fontSize.toFloat()

            if (ttsEnabled) {
                tts.speak(connected, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }

        // Settings button
        buttonSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        // Bluetooth button
        bluetoothButton.setOnClickListener {
            checkBluetoothPermission()
        }

        // Clear Text button
        clearTextButton.setOnClickListener {
            editTextInput.text.clear()
            textViewResult.text = ""
        }
        
        // Demo control buttons
        startDemoButton.setOnClickListener {
            startDemo()
        }
        
        stopDemoButton.setOnClickListener {
            stopDemo()
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        fontSize = prefs.getInt("fontSize", 20)
        ttsEnabled = prefs.getBoolean("ttsEnabled", true)

        textViewResult.textSize = fontSize.toFloat()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
        } else {
            Toast.makeText(this, "TTS Initialization failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initFusionModel() {
        try {
            // Initialize fusion classifier
            fusionClassifier = FusionASLClassifier(
                context = this,
                lstmModelFileName = "TFLiteCompatible_LSTM.tflite",
                xgbModelFileName = "TFLiteCompatible_XGB.json"
            )
            
            // Initialize demo sensor source
            fusionDemo = FusionDemoSensorSource(
                context = this,
                periodMs = 50L,  // 20 Hz sampling
                csvAsset = "demo_samples.csv"
            )
            
            // Initialize sequence buffer
            sequenceBuffer = SequenceBuffer(
                maxLength = fusionClassifier.getSequenceLength(),
                numFeatures = fusionClassifier.getNumFeatures()
            )
            
            Toast.makeText(this, "Fusion model loaded successfully", Toast.LENGTH_SHORT).show()
            
            // Note: Inference stream is now started manually via demo buttons
            
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to initialize fusion model", e)
            Toast.makeText(this, "Failed to load fusion model: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun startFusionStream() {
        streamJob = lifecycleScope.launch(Dispatchers.IO) {
            var lastPrediction = ""
            var stableCount = 0
            val STABILITY_THRESHOLD = 3  // Require 3 consecutive same predictions
            
            while (isActive) {
                try {
                    // Get next sensor reading
                    val features = fusionDemo.nextFeatures() ?: continue
                    
                    // Add to buffer
                    sequenceBuffer.add(features)
                    
                    // Only predict when buffer has enough data (at least 50% full)
                    if (sequenceBuffer.size() >= fusionClassifier.getSequenceLength() / 2) {
                        // Get sequence from buffer
                        val sequence = sequenceBuffer.getSequence()
                        
                        // Run fusion prediction
                        val pred = fusionClassifier.predict(sequence)
                        if (pred == null) {
                            android.util.Log.w("FusionDemo", "Prediction returned null, skipping")
                            continue
                        }
                        
                        android.util.Log.d("FusionDemo", 
                            "Buffer: ${sequenceBuffer.size()}/${fusionClassifier.getSequenceLength()}, " +
                            "Prediction: $pred"
                        )
                        
                        // Stability check: only add prediction if it's stable
                        if (pred.label == lastPrediction) {
                            stableCount++
                        } else {
                            lastPrediction = pred.label
                            stableCount = 1
                        }
                        
                        // Add to input if prediction is stable and confident
                        if (stableCount >= STABILITY_THRESHOLD && 
                            pred.probability >= 0.5f &&
                            pred.label != "Neutral") {  // Don't add neutral gestures
                            
                            withContext(Dispatchers.Main) {
                                inputEdit.append(pred.label)
                            }
                            
                            // Reset for next gesture
                            stableCount = 0
                            lastPrediction = ""
                            sequenceBuffer.clear()  // Clear buffer for next gesture
                            
                            // Small delay to avoid rapid repeated predictions
                            kotlinx.coroutines.delay(500L)
                        }
                    }
                    
                } catch (e: Exception) {
                    android.util.Log.e("FusionDemo", "Error in inference loop", e)
                }
            }
        }
    }

    override fun onDestroy() {
        streamJob?.cancel() // stop the coroutine loop
        streamJob = null
        
        // Stop demo if running
        demoJob?.cancel()
        demoJob = null
        isDemoRunning = false

        tts.stop()
        tts.shutdown()
        
        if (USE_FUSION_MODEL) {
            fusionClassifier.close()
            lifecycleScope.launch {
                fusionDemo.close()
            }
        } else {
            // classifier.close()
            // lifecycleScope.launch {
            //     demo.close()
            // }
        }
        
        super.onDestroy()
    }

    private fun checkBluetoothPermission() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }

        if (hasPermission) {
            // Permission granted, go to BluetoothActivity
            startActivity(Intent(this, BluetoothActivity::class.java))
        } else {
            // Request permission
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Manifest.permission.BLUETOOTH_CONNECT
            } else {
                Manifest.permission.BLUETOOTH
            }
            ActivityCompat.requestPermissions(this, arrayOf(permission), BLUETOOTH_PERMISSION_REQUEST)
        }
    }
    
    // Demo control methods
    private fun startDemo() {
        android.util.Log.d("Demo", "startDemo() called - stack trace: ${Thread.currentThread().stackTrace.take(10).joinToString("\n")}")
        
        // Log the call for debugging
        android.util.Log.d("Demo", "startDemo() called from button click")
        
        if (!USE_FUSION_MODEL) {
            Toast.makeText(this, "Demo requires fusion model to be enabled", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (isDemoRunning) {
            Toast.makeText(this, "Demo is already running", Toast.LENGTH_SHORT).show()
            return
        }
        
        android.util.Log.d("Demo", "Starting demo...")
        android.util.Log.d("Demo", "Before start: isDemoRunning=$isDemoRunning")
        isDemoRunning = true
        android.util.Log.d("Demo", "After start: isDemoRunning=$isDemoRunning")
        updateDemoUI()
        
        // Reset demo state before starting
        if (::sequenceBuffer.isInitialized) {
            sequenceBuffer.clear()
        }
        if (::fusionDemo.isInitialized) {
            fusionDemo.reset()
        }
        
        // Start the fusion stream
        startFusionStream()
        
        // Start demo coroutine
        demoJob = lifecycleScope.launch(Dispatchers.IO) {
            runDemo()
        }
        
        Toast.makeText(this, "Demo started - simulating glove sensor data", Toast.LENGTH_SHORT).show()
        android.util.Log.d("Demo", "Demo started successfully")
    }
    
    private fun stopDemo() {
        if (!isDemoRunning) {
            Toast.makeText(this, "Demo is not running", Toast.LENGTH_SHORT).show()
            return
        }
        
        android.util.Log.d("Demo", "Stopping demo...")
        android.util.Log.d("Demo", "Before stop: isDemoRunning=$isDemoRunning, demoJob=$demoJob")
        isDemoRunning = false
        demoJob?.cancel()
        demoJob = null
        android.util.Log.d("Demo", "After stop: isDemoRunning=$isDemoRunning, demoJob=$demoJob")
        
        // Stop the fusion stream
        streamJob?.cancel()
        streamJob = null
        
        // Reset demo state
        if (::sequenceBuffer.isInitialized) {
            sequenceBuffer.clear()
        }
        if (::fusionDemo.isInitialized) {
            fusionDemo.reset()
        }
        
        updateDemoUI()
        Toast.makeText(this, "Demo stopped", Toast.LENGTH_SHORT).show()
        android.util.Log.d("Demo", "Demo stopped successfully")
    }
    
    private fun updateDemoUI() {
        startDemoButton.isEnabled = !isDemoRunning
        stopDemoButton.isEnabled = isDemoRunning
        
        val statusText = if (isDemoRunning) {
            "Demo: Running - Simulating glove data..."
        } else {
            "Demo: Ready to start"
        }
        demoStatusText.text = statusText
    }
    
    private suspend fun runDemo() = coroutineScope {
        if (!::fusionDemo.isInitialized) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Fusion model not initialized", Toast.LENGTH_SHORT).show()
            }
            return@coroutineScope
        }
        
        var lastPrediction = ""
        var stableCount = 0
        val STABILITY_THRESHOLD = 1  // Require only 1 consecutive prediction for testing
        var lastPredictionTime = 0L
        val minPredictionInterval = 200L // 200ms minimum between predictions to prevent crashes
        var patternCycleCounter = 0
        var lettersAdded = 0
        var loopIterations = 0
        
        withContext(Dispatchers.Main) {
            demoStatusText.text = "Demo: Running - Analyzing gestures..."
        }
        
        while (isDemoRunning) {
            try {
                // Check if coroutine is still active
                if (!isActive) {
                    android.util.Log.d("Demo", "Coroutine is not active, breaking demo loop")
                    break
                }
                
                // Check if demo is still running (user might have stopped it)
                if (!isDemoRunning) {
                    android.util.Log.d("Demo", "Demo stopped by user, breaking demo loop")
                    break
                }
                
                // Debug: Log that we're still in the main loop
                loopIterations++
                android.util.Log.d("Demo", "Main loop iteration #$loopIterations, isDemoRunning=$isDemoRunning, lettersAdded=$lettersAdded")
                
                // Cycle through patterns every 50 iterations to force different predictions
                patternCycleCounter++
                if (patternCycleCounter % 50 == 0) {
                    val patternIndex = (patternCycleCounter / 50) % 5
                    fusionDemo.jumpToPattern(patternIndex)
                    android.util.Log.d("Demo", "Cycling to pattern $patternIndex")
                }
                
                // Get next sensor reading from demo source
                val features = fusionDemo.nextFeatures()
                if (features == null) {
                    android.util.Log.w("Demo", "No features received from demo source")
                    kotlinx.coroutines.delay(100L)
                    continue
                }
                
                android.util.Log.d("Demo", "Received features: ${features.joinToString { "%.3f".format(it) }}")
                
                // Add to buffer
                sequenceBuffer.add(features)
                
                android.util.Log.d("Demo", "Buffer size: ${sequenceBuffer.size()}/${fusionClassifier.getSequenceLength()}, Letters added: $lettersAdded")
                
                // Only predict when buffer has enough data (at least 50% full)
                val bufferThreshold = fusionClassifier.getSequenceLength() / 2
                android.util.Log.d("Demo", "Buffer check: ${sequenceBuffer.size()}/$bufferThreshold (need $bufferThreshold for prediction)")
                
                if (sequenceBuffer.size() >= bufferThreshold) {
                    try {
                        // Get sequence from buffer
                        val sequence = sequenceBuffer.getSequence()
                        if (sequence == null) {
                            android.util.Log.w("Demo", "No sequence available from buffer")
                            continue
                        }
                        
                        android.util.Log.d("Demo", "Got sequence with ${sequence.size} timesteps")
                        
                        // Rate limiting to prevent crashes
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastPredictionTime < minPredictionInterval) {
                            kotlinx.coroutines.delay(minPredictionInterval - (currentTime - lastPredictionTime))
                        }
                        lastPredictionTime = currentTime
                        
                        // Run fusion prediction
                        android.util.Log.d("Demo", "Making prediction with ${sequence.size} timesteps...")
                        val pred = fusionClassifier.predict(sequence)
                        if (pred == null) {
                            android.util.Log.w("Demo", "Prediction returned null, skipping")
                            continue
                        }
                        
                        android.util.Log.d("Demo", "Got prediction: ${pred.label} (${(pred.probability * 100).toInt()}%)")
                    
                        android.util.Log.d("Demo", 
                            "Buffer: ${sequenceBuffer.size()}/${fusionClassifier.getSequenceLength()}, " +
                            "Prediction: $pred"
                        )
                        
                        // Update status with current prediction
                        try {
                            withContext(Dispatchers.Main) {
                                if (::demoStatusText.isInitialized) {
                                    demoStatusText.text = "Demo: Running - Last prediction: ${pred.label} (${(pred.probability * 100).toInt()}%)"
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("Demo", "Error updating status display: ${e.message}")
                        }
                        
                        // Stability check: only add prediction if it's stable
                        if (pred.label == lastPrediction) {
                            stableCount++
                        } else {
                            lastPrediction = pred.label
                            stableCount = 1
                        }
                        
                        // Debug logging
                        android.util.Log.d("Demo", 
                            "Stability check: pred=${pred.label}, last=$lastPrediction, stable=$stableCount/$STABILITY_THRESHOLD, conf=${(pred.probability * 100).toInt()}%"
                        )
                        
                    // Add to input if prediction is stable and confident
                    if (stableCount >= STABILITY_THRESHOLD && 
                        pred.probability >= 0.2f) {  // Even further reduced to 0.2f for testing
                        // Temporarily removed Neutral filter to test
                            
                            android.util.Log.d("Demo", "Adding letter to text: ${pred.label}")
                            
                            try {
                                withContext(Dispatchers.Main) {
                                    if (::inputEdit.isInitialized) {
                                        inputEdit.append(pred.label)
                                    }
                                    
                                    // Speak the prediction if TTS is enabled
                                    if (ttsEnabled) {
                                        tts.speak(pred.label, TextToSpeech.QUEUE_FLUSH, null, null)
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.w("Demo", "Error updating text input: ${e.message}")
                            }
                            
                            // Reset for next gesture
                            stableCount = 0
                            lastPrediction = ""
                            sequenceBuffer.clear()  // Clear buffer for next gesture
                            
                            // Cycle to next pattern after adding a letter
                            lettersAdded++
                            val nextPattern = (lettersAdded % 5)
                            fusionDemo.jumpToPattern(nextPattern)
                            android.util.Log.d("Demo", "Added letter ${pred.label} (#$lettersAdded), cycling to pattern $nextPattern")
                            
                            // Reset pattern cycle counter to ensure fresh start
                            patternCycleCounter = 0
                            
                            // Small delay to avoid rapid repeated predictions
                            kotlinx.coroutines.delay(200L)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("Demo", "Error during prediction: ${e.message}", e)
                        // Continue demo even if one prediction fails
                    }
                }
                
                // Small delay between samples (20 Hz = 50ms)
                kotlinx.coroutines.delay(50L)
                
            } catch (e: Exception) {
                android.util.Log.e("Demo", "Error in demo loop: ${e.message}", e)
                // If there's a critical error, stop the demo
                if (e.message?.contains("Fatal") == true || e.message?.contains("Critical") == true) {
                    android.util.Log.e("Demo", "Critical error, stopping demo")
                    withContext(Dispatchers.Main) {
                        stopDemo()
                    }
                    break
                }
                kotlinx.coroutines.delay(100L)
            }
        }
        
        // Demo finished
        withContext(Dispatchers.Main) {
            if (isDemoRunning) {
                demoStatusText.text = "Demo: Finished"
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == BLUETOOTH_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Bluetooth permission granted", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, BluetoothActivity::class.java))
            } else {
                Toast.makeText(this, "Bluetooth permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
