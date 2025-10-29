package com.example.seniorproject

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.seniorproject.data.UserManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager

/**
 * Activity for calibrating the glove sensors for a specific user.
 * Uses commands from ASL_BLE_FINAL_frfrfrfr firmware to perform calibration on the glove itself.
 * 
 * Calibration commands from ASL_BLE_FINAL_frfrfrfr.ino:
 *   - "cal" - Calibrate flex sensors (rest + max bend for each finger)
 *   - "imu_cal" - Calibrate accelerometer (place flat and still)
 *   - "detail" - Show current calibration values (JSON format)
 *   - "setuser <name>" - Switch to user profile
 *   - "savecal" - Manually save current calibration to NVS
 */
class CalibrationActivity : AppCompatActivity() {

    private lateinit var usernameText: TextView
    private lateinit var connectButton: Button
    private lateinit var skipButton: Button
    private lateinit var connectionStatusText: TextView
    
    private lateinit var userManager: UserManager
    private var username: String = ""
    private var isConnected = false
    private var bleService: BLEService? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme
        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        when (prefs.getString("theme", "Light")) {
            "Light" -> setTheme(R.style.Theme_SeniorProject_Light)
            "Dark" -> setTheme(R.style.Theme_SeniorProject_Dark)
            else -> setTheme(R.style.Theme_SeniorProject_Light)
        }
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibration)

        // Get username from intent
        username = intent.getStringExtra("username") ?: ""
        if (username.isEmpty()) {
            Toast.makeText(this, "Error: No username provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Initialize UserManager
        userManager = UserManager(this)

        // Initialize views
        usernameText = findViewById(R.id.usernameText)
        connectButton = findViewById(R.id.connectButton)
        skipButton = findViewById(R.id.skipButton)
        connectionStatusText = findViewById(R.id.connectionStatusText)
        val beginCalibrationButton = findViewById<Button>(R.id.beginCalibrationButton)

        // Set username
        usernameText.text = "User: $username"

        // Setup button listeners
        connectButton.setOnClickListener {
            connectToGlove()
        }
        
        beginCalibrationButton.setOnClickListener {
            // Navigate to BLE log screen for calibration
            val intent = Intent(this@CalibrationActivity, BLELogActivity::class.java)
            startActivity(intent)
        }

        skipButton.setOnClickListener {
            skipCalibration()
        }
        
        // Check for existing BLE connection
        lifecycleScope.launch(Dispatchers.IO) {
            checkExistingConnection()
        }
        
        // Check initial connection status and update display
        updateConnectionStatus()
    }
    
    /**
     * Check if there's an existing BLE connection and connect to it.
     */
    private suspend fun checkExistingConnection() {
        try {
            val prefs = withContext(Dispatchers.Main) {
                getSharedPreferences("BluetoothConnection", Context.MODE_PRIVATE)
            }
            val deviceAddress = prefs.getString("device_address", null)
            val isBLE = prefs.getBoolean("is_ble", false)
            
            if (isBLE && deviceAddress != null) {
                // Try to connect to existing BLE device
                connectToBLEDevice(deviceAddress)
            }
        } catch (e: Exception) {
            android.util.Log.e("Calibration", "Error checking existing connection", e)
        }
    }

    /**
     * Connect to the glove via Bluetooth.
     */
    private fun connectToGlove() {
        // Always go to Bluetooth selection screen to let user choose device
        val intent = Intent(this, BluetoothActivity::class.java)
        startActivityForResult(intent, REQUEST_CODE_BLUETOOTH)
    }
    
    /**
     * Connect to a BLE device.
     */
    private suspend fun connectToBLEDevice(address: String) {
        try {
            val bluetoothManager = withContext(Dispatchers.Main) {
                getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            }
            val adapter = bluetoothManager.adapter
            
            val device = adapter.getRemoteDevice(address)
            
            bleService = BLEService(this).apply {
                onConnectionStateChange = { connected ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        if (connected) {
                            isConnected = true
                            updateConnectionStatus()
                            Toast.makeText(this@CalibrationActivity, "BLE Connected!", Toast.LENGTH_SHORT).show()
                        } else {
                            isConnected = false
                            updateConnectionStatus()
                        }
                    }
                }
                
                onDataReceived = { data ->
                    android.util.Log.d("Calibration", "Received: $data")
                }
                
                connect(device)
            }
            
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@CalibrationActivity, "Connection failed: ${e.message}", Toast.LENGTH_LONG).show()
                android.util.Log.e("Calibration", "BLE connection failed", e)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_BLUETOOTH && resultCode == RESULT_OK) {
            // Check if BLE device was selected
            val prefs = getSharedPreferences("BluetoothConnection", Context.MODE_PRIVATE)
            val deviceAddress = prefs.getString("device_address", null)
            val isBLE = prefs.getBoolean("is_ble", false)
            
            if (isBLE && deviceAddress != null) {
                // Connect to BLE device
                lifecycleScope.launch(Dispatchers.IO) {
                    connectToBLEDevice(deviceAddress)
                }
            } else {
                // Classic Bluetooth connection
                isConnected = true
                updateConnectionStatus()
                Toast.makeText(this, "Connected! Ready to calibrate.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Update connection status display.
     */
    private fun updateConnectionStatus() {
        // Check if BLE service is connected
        val connected = bleService?.isConnected() == true || isConnected
        
        if (connected) {
            connectionStatusText.text = "Status: Connected ✓"
            connectionStatusText.setTextColor(getColor(android.R.color.holo_green_dark))
        } else {
            connectionStatusText.text = "Status: Not connected"
            connectionStatusText.setTextColor(getColor(android.R.color.holo_red_dark))
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Update connection status when returning to this screen
        updateConnectionStatus()
    }

    /**
     * Skip calibration and return to user selection.
     */
    private fun skipCalibration() {
        AlertDialog.Builder(this)
            .setTitle("Skip Calibration?")
            .setMessage("You can calibrate later. The glove may not work optimally without calibration.\n\n" +
                    "Continue without calibration?")
            .setPositiveButton("Skip") { _, _ ->
                // User is skipping calibration
                // Don't mark as calibrated
                setResult(RESULT_CANCELED)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Disconnect BLE
        bleService?.disconnect()
    }

    companion object {
        private const val REQUEST_CODE_BLUETOOTH = 2001
    }
}

