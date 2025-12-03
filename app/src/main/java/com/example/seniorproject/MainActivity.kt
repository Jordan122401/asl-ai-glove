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
import com.example.seniorproject.data.UserManager
import com.example.seniorproject.data.User
import com.example.seniorproject.data.CalibrationData
import com.example.seniorproject.data.BLESensorSource
import com.example.seniorproject.BLEService
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import kotlinx.coroutines.delay

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var editTextInput: EditText
    private lateinit var textViewResult: TextView
    private lateinit var buttonConnect: Button
    private lateinit var buttonSettings: Button
    private lateinit var clearTextButton: Button
    private lateinit var streamToggleButton: Button
    private lateinit var connectionStatusText: TextView
    private lateinit var currentUserText: TextView
    private lateinit var tts: TextToSpeech
    
    // Fusion classifier and sensor source
    private lateinit var fusionClassifier: FusionASLClassifier
    private var sensorSource: SensorSource? = null
    private lateinit var sequenceBuffer: SequenceBuffer
    private var bleSensorSource: BLESensorSource? = null
    private var bleService: BLEService? = null
    
    // User management
    private lateinit var userManager: UserManager
    private var currentUser: User? = null
    private var calibrationData: CalibrationData? = null
    
    private lateinit var inputEdit: EditText
    private var ttsEnabled = true
    private var fontSize = 20
    private val BLUETOOTH_PERMISSION_REQUEST = 1001

    private var inferenceJob: Job? = null
    private var statusJob: Job? = null
    private var isStreaming = false // Track if streaming is active

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        inputEdit = findViewById(R.id.inputText)
        
        // Initialize user management
        userManager = UserManager(this)
        loadCurrentUser()
        
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
        clearTextButton = findViewById(R.id.clearTextButton)
        streamToggleButton = findViewById(R.id.streamToggleButton)
        connectionStatusText = findViewById(R.id.connectionStatusText)
        currentUserText = findViewById(R.id.currentUserText)

        // Initialize TTS
        tts = TextToSpeech(this, this)

        // Connect Text button: keep your existing behavior (concat + TTS)
        buttonConnect.setOnClickListener {
            val lines = editTextInput.text.toString().split("\n")
            val connected = lines.joinToString(" ")
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

        
        // Clear Text button
        clearTextButton.setOnClickListener {
            editTextInput.text.clear()
            textViewResult.text = ""
        }
        
        // Stream toggle button
        streamToggleButton.setOnClickListener {
            if (isStreaming) {
                stopInferenceStream(true)
            } else {
                startInferenceStream()
            }
            updateStreamButtons()
        }
        
        // Display current user info
        updateUserDisplay()
        
        // Initialize BLE service and sensor source
        initBLEComponents()
        
        // Initial button state
        updateStreamButtons()
    }
    
    /**
     * Update the enabled state of stream buttons based on connection status.
     */
    private fun updateStreamButtons() {
        val isConnected = bleService?.isConnected() == true
        streamToggleButton.isEnabled = isConnected
        streamToggleButton.text = if (isStreaming) "Stop Stream" else "Start Stream"
    }
    
    /**
     * Initialize BLE service and sensor source for receiving glove data.
     */
    private fun initBLEComponents() {
        bleSensorSource = BLESensorSource()
        bleService = BLEService(this).apply {
            onConnectionStateChange = { connected ->
                runOnUiThread {
                    if (connected) {
                        updateConnectionStatus("Connected to glove")
                    } else {
                        updateConnectionStatus("Disconnected")
                        stopInferenceStream(false)
                    }
                    updateStreamButtons()
                }
            }
            onDataReceived = { data ->
                // Forward received data to sensor source for parsing
                bleSensorSource?.onDataReceived(data)
            }
        }

        // Attempt auto-reconnect to previously selected BLE device
        val prefs = getSharedPreferences("BluetoothConnection", Context.MODE_PRIVATE)
        val deviceAddress = prefs.getString("device_address", null)
        val isBLE = prefs.getBoolean("is_ble", false)
        if (deviceAddress != null && isBLE && bleService != null) {
            try {
                val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val adapter = bluetoothManager.adapter
                val device = adapter.getRemoteDevice(deviceAddress)
                if (bleService!!.connect(device)) {
                    updateConnectionStatus("Connecting...")
                } else {
                    updateConnectionStatus("Connection failed")
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Auto-reconnect failed", e)
                updateConnectionStatus("Connection failed")
            }
            updateStreamButtons()
        }
    }
    
    /**
     * Load the current user and their calibration data.
     */
    private fun loadCurrentUser() {
        currentUser = userManager.getCurrentUser()
        calibrationData = currentUser?.calibrationData
        
        if (currentUser != null) {
            android.util.Log.d("MainActivity", "Loaded user: ${currentUser?.username}")
            if (calibrationData != null) {
                android.util.Log.d("MainActivity", "Calibration data loaded for ${currentUser?.username}")
            }
        } else {
            android.util.Log.w("MainActivity", "No current user set! Redirecting to user selection...")
            // If no user is set, redirect to user selection
            val intent = Intent(this, UserSelectionActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
    
    /**
     * Update the UI to display current user information.
     */
    private fun updateUserDisplay() {
        currentUser?.let { user ->
            val statusMessage = "User: ${user.username}"
            currentUserText.text = statusMessage
            android.util.Log.d("MainActivity", statusMessage)
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
            android.util.Log.d("MainActivity", "Initializing fusion model...")
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
            
            android.util.Log.d("MainActivity", "Fusion model initialized successfully")
            
        } catch (e: OutOfMemoryError) {
            android.util.Log.e("MainActivity", "Out of memory loading model", e)
            runOnUiThread {
                Toast.makeText(this, "Model too large for device memory. Please use a smaller model.", Toast.LENGTH_LONG).show()
            }
            throw e
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to initialize fusion model", e)
            android.util.Log.e("MainActivity", "Error type: ${e.javaClass.simpleName}")
            android.util.Log.e("MainActivity", "Error message: ${e.message}")
            e.printStackTrace()
            runOnUiThread {
                val errorMsg = when {
                    e.message?.contains("too large", ignoreCase = true) == true -> 
                        "Model file is too large for this device"
                    e.message?.contains("not found", ignoreCase = true) == true -> 
                        "Model file not found. Please check assets folder."
                    e.message?.contains("parse", ignoreCase = true) == true -> 
                        "Model file format error. Please check the model file."
                    else -> "Failed to load model: ${e.message ?: "Unknown error"}"
                }
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            }
            throw e
        }
    }
    
    /**
     * Start real-time inference stream from the connected BLE sensor source.
     * 
     * With ASL_BLE_FINAL_frfrfrfr firmware, the glove automatically sends batches of exactly
     * 75 samples with a 1-second gap between batches. The app collects these
     * batches and runs inference on each one.
     */
    private fun startInferenceStream() {
        if (bleSensorSource == null || bleService == null || !bleService!!.isConnected()) {
            Toast.makeText(this, "Not connected to glove", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Send stream command to glove (ASL_BLE_FINAL_frfrfrfr uses "stream" command)
        // The glove will automatically send batches of 75 samples with 1-second gaps
        if (!bleService!!.writeCommand("stream")) {
            Toast.makeText(this, "Failed to start stream on glove", Toast.LENGTH_SHORT).show()
            return
        }
        
        isStreaming = true
        bleSensorSource?.setActive(true)
        sequenceBuffer.clear()
        
        updateConnectionStatus("Streaming...")
        // Start a lightweight status monitor so users see progress even if parsing is delayed
        statusJob?.cancel()
        statusJob = lifecycleScope.launch(Dispatchers.Main) {
            while (isActive && isStreaming) {
                val queued = try { bleSensorSource?.queueSize() ?: 0 } catch (_: Exception) { 0 }
                connectionStatusText.text = "Streaming... (queued: $queued)"
                delay(500)
            }
        }
        
        inferenceJob = lifecycleScope.launch(Dispatchers.IO) {
            val REQUIRED_READINGS = 75 // Glove sends exactly 75 samples per batch
            
            while (isActive && isStreaming) {
                try {
                    // Collect exactly 75 readings
                    val readings = mutableListOf<FloatArray>()
                    var readingsReceived = 0
                    
                    while (readingsReceived < REQUIRED_READINGS && isActive && isStreaming) {
                        // Get next sensor reading from BLE
                        var features = bleSensorSource?.nextFeatures()
                        
                        // Wait a bit if no data available yet
                        if (features == null) {
                            delay(50) // Small delay to avoid busy waiting
                            continue
                        }
                        
                        // Apply calibration if available
                        if (calibrationData != null) {
                            features = calibrationData!!.normalizeAllSensors(features)
                        }
                        
                        readings.add(features)
                        readingsReceived++
                        
                        // Update status to show progress
                        withContext(Dispatchers.Main) {
                            updateConnectionStatus("Collecting data: $readingsReceived/$REQUIRED_READINGS")
                        }
                    }
                    
                    if (readingsReceived < REQUIRED_READINGS) {
                        // Stream was stopped or connection lost
                        break
                    }
                    
                    // Note: Glove handles the 1-second gap between batches automatically,
                    // so we can process immediately after collecting 75 readings
                    withContext(Dispatchers.Main) {
                        updateConnectionStatus("Processing...")
                    }
                    
                    // Convert readings to sequence format
                    val sequence = readings.toTypedArray()
                    
                    // Run fusion prediction
                    val pred = fusionClassifier.predict(sequence)
                    if (pred == null) {
                        android.util.Log.w("Inference", "Prediction returned null, skipping")
                        withContext(Dispatchers.Main) {
                            updateConnectionStatus("Prediction failed")
                        }
                        continue
                    }
                    
                    android.util.Log.d("Inference", 
                        "Prediction: ${pred.label} (${(pred.probability * 100).toInt()}%)"
                    )
                    
                    // Update status with current prediction
                    withContext(Dispatchers.Main) {
                        updateConnectionStatus("${pred.label} (${(pred.probability * 100).toInt()}%)")
                    }
                    
                    // Append to text box for each 75-sample batch
                    withContext(Dispatchers.Main) {
                        when (pred.label.lowercase()) {
                            "backspace" -> {
                                // Remove the last character if text is not empty
                                val currentText = inputEdit.text.toString()
                                if (currentText.isNotEmpty()) {
                                    inputEdit.setText(currentText.dropLast(1))
                                    // Move cursor to end
                                    inputEdit.setSelection(inputEdit.text.length)
                                }
                            }
                            "neutral" -> {
                                inputEdit.append(" ")
                            }
                            else -> {
                                // Append the letter (A-Z)
                                inputEdit.append(pred.label)
                            }
                        }
                    }
                    
                    // Clear buffer for next cycle
                    sequenceBuffer.clear()
                    
                    // Cycle repeats automatically
                    
                } catch (e: Exception) {
                    android.util.Log.e("Inference", "Error in inference loop", e)
                    withContext(Dispatchers.Main) {
                        updateConnectionStatus("Error: ${e.message}")
                    }
                    delay(1000) // Wait before retrying
                }
            }
        }
    }
    
    /**
     * Stop the inference stream.
     */
    private fun stopInferenceStream(userInitiated: Boolean = false) {
        isStreaming = false
        inferenceJob?.cancel()
        inferenceJob = null
        statusJob?.cancel()
        statusJob = null
        bleSensorSource?.setActive(false)
        sequenceBuffer.clear()
        
        // Send stream:off command to glove
        bleService?.writeCommand("stream:off")
        
        runOnUiThread {
            if (bleService?.isConnected() == true) {
                updateConnectionStatus("Connected (stream stopped)")
            } else {
                updateConnectionStatus("Disconnected")
            }
            updateStreamButtons()
            if (userInitiated) {
                val lines = editTextInput.text.toString().split("\n")
                val connected = lines.joinToString(" ")
                textViewResult.text = connected
                textViewResult.textSize = fontSize.toFloat()
                if (ttsEnabled && connected.isNotBlank()) {
                    tts.speak(connected, TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }
        }
    }

    override fun onDestroy() {
        // Stop inference stream
        stopInferenceStream(false)
        
        // Close sensor source
        lifecycleScope.launch {
            sensorSource?.close()
            bleSensorSource?.close()
        }
        sensorSource = null
        bleSensorSource = null
        
        // Disconnect BLE service
        bleService?.disconnect()
        bleService = null

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
        if (requestCode == BLUETOOTH_CONNECT_REQUEST && resultCode == RESULT_OK) {
            // Connection details are stored in shared preferences by BluetoothActivity
            val prefs = getSharedPreferences("BluetoothConnection", Context.MODE_PRIVATE)
            val deviceAddress = prefs.getString("device_address", null)
            val isBLE = prefs.getBoolean("is_ble", false)
            
            if (deviceAddress != null && isBLE && bleService != null) {
                // Connect to the BLE device
                val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val adapter = bluetoothManager.adapter
                
                try {
                    val device = adapter.getRemoteDevice(deviceAddress)
                    if (bleService!!.connect(device)) {
                        updateConnectionStatus("Connecting...")
                        updateStreamButtons()
                    } else {
                        Toast.makeText(this, "Failed to initiate connection", Toast.LENGTH_SHORT).show()
                        updateConnectionStatus("Connection failed")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Failed to connect to device", e)
                    Toast.makeText(this, "Failed to connect: ${e.message}", Toast.LENGTH_SHORT).show()
                    updateConnectionStatus("Connection failed")
                }
            } else {
                updateConnectionStatus("Connected to glove")
            }
        }
    }
}
