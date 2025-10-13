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
import com.example.seniorproject.data.SensorSource
import com.example.seniorproject.data.SequenceBuffer

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var editTextInput: EditText
    private lateinit var textViewResult: TextView
    private lateinit var buttonConnect: Button
    private lateinit var buttonSettings: Button
    private lateinit var bluetoothButton: Button
    private lateinit var clearTextButton: Button
    private lateinit var connectionStatusText: TextView
    private lateinit var tts: TextToSpeech
    
    // Fusion classifier and sensor source
    private lateinit var fusionClassifier: FusionASLClassifier
    private var sensorSource: SensorSource? = null
    private lateinit var sequenceBuffer: SequenceBuffer
    
    private lateinit var inputEdit: EditText
    private var ttsEnabled = true
    private var fontSize = 20
    private val BLUETOOTH_PERMISSION_REQUEST = 1001

    private var inferenceJob: Job? = null
    private var isConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        inputEdit = findViewById(R.id.inputText)
        
        // Initialize fusion model
        initFusionModel()

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
        connectionStatusText = findViewById(R.id.connectionStatusText)

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

        // Bluetooth button - Connect to glove
        bluetoothButton.setOnClickListener {
            checkBluetoothPermissionAndConnect()
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
            // Initialize fusion classifier
            fusionClassifier = FusionASLClassifier(
                context = this,
                lstmModelFileName = "TFLiteCompatible_LSTM.tflite",
                xgbModelFileName = "TFLiteCompatible_XGB.json"
            )
            
            // Initialize sequence buffer
            sequenceBuffer = SequenceBuffer(
                maxLength = fusionClassifier.getSequenceLength(),
                numFeatures = fusionClassifier.getNumFeatures()
            )
            
            Toast.makeText(this, "Fusion model loaded successfully", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to initialize fusion model", e)
            Toast.makeText(this, "Failed to load fusion model: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Start real-time inference stream from the connected sensor source.
     */
    private fun startInferenceStream() {
        if (sensorSource == null) {
            Toast.makeText(this, "No sensor source connected", Toast.LENGTH_SHORT).show()
            return
        }
        
        inferenceJob = lifecycleScope.launch(Dispatchers.IO) {
            var lastPrediction = ""
            var stableCount = 0
            val STABILITY_THRESHOLD = 3  // Require 3 consecutive same predictions
            
            while (isActive && isConnected) {
                try {
                    // Get next sensor reading from glove
                    val features = sensorSource?.nextFeatures()
                    
                    if (features == null) {
                        android.util.Log.w("Inference", "No features received, checking connection...")
                        withContext(Dispatchers.Main) {
                            updateConnectionStatus("Connection lost")
                            stopInferenceStream()
                        }
                        break
                    }
                    
                    // Add to buffer
                    sequenceBuffer.add(features)
                    
                    // Only predict when buffer has enough data (at least 50% full)
                    if (sequenceBuffer.size() >= fusionClassifier.getSequenceLength() / 2) {
                        // Get sequence from buffer
                        val sequence = sequenceBuffer.getSequence()
                        
                        // Run fusion prediction
                        val pred = fusionClassifier.predict(sequence)
                        if (pred == null) {
                            android.util.Log.w("Inference", "Prediction returned null, skipping")
                            continue
                        }
                        
                        android.util.Log.d("Inference", 
                            "Buffer: ${sequenceBuffer.size()}/${fusionClassifier.getSequenceLength()}, " +
                            "Prediction: ${pred.label} (${(pred.probability * 100).toInt()}%)"
                        )
                        
                        // Update status with current prediction
                        withContext(Dispatchers.Main) {
                            updateConnectionStatus("Connected - ${pred.label} (${(pred.probability * 100).toInt()}%)")
                        }
                        
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
                                
                                // Speak the prediction if TTS is enabled
                                if (ttsEnabled) {
                                    tts.speak(pred.label, TextToSpeech.QUEUE_FLUSH, null, null)
                                }
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
                    android.util.Log.e("Inference", "Error in inference loop", e)
                }
            }
        }
    }
    
    /**
     * Stop the inference stream.
     */
    private fun stopInferenceStream() {
        inferenceJob?.cancel()
        inferenceJob = null
        isConnected = false
        sequenceBuffer.clear()
    }

    override fun onDestroy() {
        // Stop inference stream
        stopInferenceStream()
        
        // Close sensor source
        lifecycleScope.launch {
            sensorSource?.close()
        }
        sensorSource = null

        // Shutdown TTS
        tts.stop()
        tts.shutdown()
        
        // Close classifier
        fusionClassifier.close()
        
        super.onDestroy()
    }

    private fun checkBluetoothPermissionAndConnect() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }

        if (hasPermission) {
            // Permission granted, go to BluetoothActivity to connect to glove
            startActivityForResult(Intent(this, BluetoothActivity::class.java), BLUETOOTH_CONNECT_REQUEST)
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
    
    /**
     * Update the connection status display.
     */
    private fun updateConnectionStatus(status: String) {
        connectionStatusText.text = status
    }
    
    companion object {
        const val BLUETOOTH_CONNECT_REQUEST = 2001
        const val EXTRA_BLUETOOTH_SOCKET = "bluetooth_socket"
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
                checkBluetoothPermissionAndConnect()
            } else {
                Toast.makeText(this, "Bluetooth permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // This will be called when returning from BluetoothActivity with a connection
        // The BluetoothActivity will pass back connection details when ready
        if (requestCode == BLUETOOTH_CONNECT_REQUEST && resultCode == RESULT_OK) {
            // Connection established - will be handled via shared preferences or intent extras
            updateConnectionStatus("Connected to glove")
        }
    }
}
