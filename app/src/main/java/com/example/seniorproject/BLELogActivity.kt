package com.example.seniorproject

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager

/**
 * Activity that displays real-time BLE command logs from the glove.
 * Shows all incoming data and messages received from the connected glove.
 */
class BLELogActivity : AppCompatActivity() {

    private lateinit var logText: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var clearButton: Button
    private lateinit var connectionStatusText: TextView
    private lateinit var sendCommandButton: Button
    private lateinit var commandInput: EditText
    private lateinit var calibrateFlexButton: Button
    private lateinit var calibrateImuButton: Button
    
    private var bleService: BLEService? = null
    private val logEntries = mutableListOf<String>()
    private var maxLogEntries = 1000 // Keep last 1000 entries
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme
        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        when (prefs.getString("theme", "Light")) {
            "Light" -> setTheme(R.style.Theme_SeniorProject_Light)
            "Dark" -> setTheme(R.style.Theme_SeniorProject_Dark)
            else -> setTheme(R.style.Theme_SeniorProject_Light)
        }
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ble_log)
        
        // Initialize views
        logText = findViewById(R.id.logText)
        scrollView = findViewById(R.id.logScrollView)
        clearButton = findViewById(R.id.clearLogButton)
        connectionStatusText = findViewById(R.id.connectionStatusText)
        sendCommandButton = findViewById(R.id.sendCommandButton)
        commandInput = findViewById(R.id.commandInput)
        calibrateFlexButton = findViewById(R.id.calibrateFlexButton)
        calibrateImuButton = findViewById(R.id.calibrateImuButton)
        
        // Setup clear button
        clearButton.setOnClickListener {
            clearLog()
        }

        // Setup send button
        sendCommandButton.setOnClickListener {
            sendCommand()
        }
        
        // Setup calibration buttons
        calibrateFlexButton.setOnClickListener {
            sendCalibrationCommand("cal")
        }
        
        calibrateImuButton.setOnClickListener {
            sendCalibrationCommand("imu_cal")
        }

        // Send on IME action
        commandInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                (event != null && event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                sendCommand()
                true
            } else false
        }
        
        // Auto-connect to BLE device if available
        lifecycleScope.launch(Dispatchers.IO) {
            connectToGlove()
        }
        
        // Initial status
        updateConnectionStatus("Connecting...", false)
    }
    
    /**
     * Send the command typed by the user to the glove via BLE.
     */
    private fun sendCommand() {
        val cmd = commandInput.text.toString().trim()
        if (cmd.isEmpty()) {
            addLogEntry("⚠️ Please enter a command")
            return
        }

        if (bleService == null || !bleService!!.isConnected()) {
            addLogEntry("⚠️ Not connected - cannot send: $cmd")
            return
        }

        val ok = bleService!!.writeCommand(cmd)
        if (ok) {
            addLogEntry(">> $cmd")
            commandInput.setText("")
        } else {
            addLogEntry("❌ Failed to send: $cmd")
        }
    }
    
    /**
     * Send a calibration command to the glove.
     */
    private fun sendCalibrationCommand(command: String) {
        if (bleService == null || !bleService!!.isConnected()) {
            addLogEntry("⚠️ Not connected - cannot send: $command")
            return
        }
        
        val ok = bleService!!.writeCommand(command)
        if (ok) {
            addLogEntry(">> $command")
            when (command) {
                "cal" -> {
                    addLogEntry("📝 Starting flex sensor calibration...")
                    addLogEntry("📝 Follow glove prompts: relax hand, then bend each finger")
                }
                "imu_cal" -> {
                    addLogEntry("📝 Starting IMU calibration...")
                    addLogEntry("📝 Place glove FLAT and STILL for ~4 seconds")
                }
            }
        } else {
            addLogEntry("❌ Failed to send: $command")
        }
    }

    /**
     * Connect to the glove using stored connection info.
     */
    private suspend fun connectToGlove() {
        try {
            val prefs = getSharedPreferences("BluetoothConnection", Context.MODE_PRIVATE)
            val deviceAddress = prefs.getString("device_address", null)
            val isBLE = prefs.getBoolean("is_ble", false)
            
            if (!isBLE || deviceAddress == null) {
                withContext(Dispatchers.Main) {
                    updateConnectionStatus("No BLE device connected", false)
                    addLogEntry("⚠️ No BLE device found. Please connect to a glove from the Calibration screen.")
                }
                return
            }
            
            val bluetoothManager = withContext(Dispatchers.Main) {
                getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            }
            val adapter = bluetoothManager.adapter
            val device = adapter.getRemoteDevice(deviceAddress)
            
            bleService = BLEService(this@BLELogActivity).apply {
                onConnectionStateChange = { connected ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        if (connected) {
                            updateConnectionStatus("Connected ✓", true)
                            addLogEntry("✅ Connected to glove: $deviceAddress")
                            sendCommandButton.isEnabled = true
                            calibrateFlexButton.isEnabled = true
                            calibrateImuButton.isEnabled = true
                        } else {
                            updateConnectionStatus("Disconnected", false)
                            addLogEntry("❌ Disconnected from glove")
                            sendCommandButton.isEnabled = false
                            calibrateFlexButton.isEnabled = false
                            calibrateImuButton.isEnabled = false
                        }
                    }
                }
                
                onDataReceived = { data ->
                    // Add received data to log
                    lifecycleScope.launch(Dispatchers.Main) {
                        addLogEntry(data)
                    }
                }
                
                connect(device)
            }
            
            withContext(Dispatchers.Main) {
                updateConnectionStatus("Connecting...", false)
                addLogEntry("🔌 Attempting to connect to: $deviceAddress")
                addLogEntry("📡 Listening for BLE data...")
            }
            
        } catch (e: Exception) {
            lifecycleScope.launch(Dispatchers.Main) {
                updateConnectionStatus("Connection failed", false)
                addLogEntry("❌ Connection error: ${e.message}")
                android.util.Log.e("BLELog", "Connection failed", e)
            }
        }
    }
    
    /**
     * Add a log entry to the display.
     */
    private fun addLogEntry(entry: String) {
        val timestamp = System.currentTimeMillis()
        val timeString = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
        
        // Format entry - handle multi-line entries (split by newlines)
        val lines = entry.split("\n", "\r\n", "\r")
        lines.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) {
                val formattedEntry = "[$timeString] $trimmed"
                logEntries.add(formattedEntry)
            }
        }
        
        // Limit log size
        while (logEntries.size > maxLogEntries) {
            logEntries.removeAt(0)
        }
        
        // Update display (show last 500 entries for performance)
        val displayEntries = logEntries.takeLast(500)
        logText.text = displayEntries.joinToString("\n")
        
        // Auto-scroll to bottom
        scrollView.post {
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }
    
    /**
     * Clear all log entries.
     */
    private fun clearLog() {
        logEntries.clear()
        logText.text = ""
        addLogEntry("📋 Log cleared")
    }
    
    /**
     * Update connection status display.
     */
    private fun updateConnectionStatus(status: String, connected: Boolean) {
        connectionStatusText.text = "Status: $status"
        if (connected) {
            connectionStatusText.setTextColor(getColor(android.R.color.holo_green_dark))
        } else {
            connectionStatusText.setTextColor(getColor(android.R.color.holo_red_dark))
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Disconnect BLE when leaving
        bleService?.disconnect()
    }
    
    companion object {
        /**
         * Create an intent to start this activity.
         */
        fun createIntent(context: Context): Intent {
            return Intent(context, BLELogActivity::class.java)
        }
    }
}

