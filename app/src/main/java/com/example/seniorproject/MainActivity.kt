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
            // UPDATED: Initialize fusion classifier with TFLite-compatible models
            // These model files were generated from the Jupyter notebook after fixing TFLite compatibility
            fusionClassifier = FusionASLClassifier(
                context = this,
                lstmModelFileName = "TFLiteCompatible_LSTM.tflite",  // NEW: TFLite-compatible LSTM model
                xgbModelFileName = "TFLiteCompatible_XGB.json"       // NEW: XGBoost model with robust JSON parsing
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
            
            // Start inference stream
            startFusionStream()
            
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
