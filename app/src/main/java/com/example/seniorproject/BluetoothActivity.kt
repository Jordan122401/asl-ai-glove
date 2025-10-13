package com.example.seniorproject

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

class BluetoothActivity : AppCompatActivity() {

    private lateinit var buttonCheck: Button
    private lateinit var buttonTurnOn: Button
    private lateinit var buttonScan: Button
    private lateinit var buttonBack: Button
    private lateinit var textStatus: TextView
    private lateinit var listViewDevices: ListView
    
    private val PERMISSION_REQUEST_CODE = 1001
    private val REQUEST_ENABLE_BT = 2001
    
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var pairedDevices: Set<BluetoothDevice>? = null
    private var deviceList: ArrayList<String> = ArrayList()
    private var deviceAddressList: ArrayList<String> = ArrayList()
    private lateinit var adapter: ArrayAdapter<String>
    
    // Standard UUID for Serial Port Profile (SPP)
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        when (prefs.getString("theme", "Light")) {
            "Light" -> setTheme(R.style.Theme_SeniorProject_Light)
            "Dark" -> setTheme(R.style.Theme_SeniorProject_Dark)
            else -> setTheme(R.style.Theme_SeniorProject_Light)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth)

        // Initialize views
        buttonCheck = findViewById(R.id.buttonCheckPermission)
        buttonTurnOn = findViewById(R.id.buttonTurnOnBluetooth)
        buttonScan = findViewById(R.id.buttonScanDevices)
        buttonBack = findViewById(R.id.buttonBack)
        textStatus = findViewById(R.id.textStatus)
        listViewDevices = findViewById(R.id.listViewDevices)

        // Initialize Bluetooth adapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: run {
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_SHORT).show()
            textStatus.text = "Bluetooth Status: Not Supported"
            finish()
            return
        }

        // Setup ListView adapter
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceList)
        listViewDevices.adapter = adapter

        // Check Bluetooth Permission
        buttonCheck.setOnClickListener { checkBluetoothPermission() }

        // Turn On Bluetooth
        buttonTurnOn.setOnClickListener {
            if (!bluetoothAdapter.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            } else {
                Toast.makeText(this, "Bluetooth is already ON", Toast.LENGTH_SHORT).show()
                updateStatus("Bluetooth Status: ON")
            }
        }
        
        // Scan for paired devices
        buttonScan.setOnClickListener {
            scanForPairedDevices()
        }

        // Back button
        buttonBack.setOnClickListener {
            finish()
        }
        
        // Device list item click - connect to selected device
        listViewDevices.setOnItemClickListener { _, _, position, _ ->
            if (position < deviceAddressList.size) {
                val deviceAddress = deviceAddressList[position]
                val deviceName = deviceList[position]
                connectToDevice(deviceAddress, deviceName)
            }
        }
        
        // Update initial status
        updateBluetoothStatus()
    }
    
    private fun updateBluetoothStatus() {
        val status = if (bluetoothAdapter.isEnabled) {
            "Bluetooth Status: ON"
        } else {
            "Bluetooth Status: OFF"
        }
        updateStatus(status)
    }
    
    private fun updateStatus(message: String) {
        textStatus.text = message
    }

    private fun checkBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permission = Manifest.permission.BLUETOOTH_CONNECT
            if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Bluetooth permission granted ✅", Toast.LENGTH_SHORT).show()
                updateStatus("Bluetooth Status: Permission Granted")
            } else {
                Toast.makeText(this, "Bluetooth permission NOT granted ❌", Toast.LENGTH_SHORT).show()
                ActivityCompat.requestPermissions(this, arrayOf(permission), PERMISSION_REQUEST_CODE)
            }
        } else {
            Toast.makeText(this, "Bluetooth permission automatically granted ✅", Toast.LENGTH_SHORT).show()
            updateStatus("Bluetooth Status: Permission Granted")
        }
    }
    
    private fun scanForPairedDevices() {
        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Please turn on Bluetooth first", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Check permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) 
                != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Bluetooth permission required", Toast.LENGTH_SHORT).show()
                checkBluetoothPermission()
                return
            }
        }
        
        try {
            pairedDevices = bluetoothAdapter.bondedDevices
            
            deviceList.clear()
            deviceAddressList.clear()
            
            if (pairedDevices.isNullOrEmpty()) {
                Toast.makeText(this, "No paired devices found", Toast.LENGTH_SHORT).show()
                updateStatus("No paired devices. Please pair your glove first.")
            } else {
                for (device in pairedDevices!!) {
                    deviceList.add("${device.name}\n${device.address}")
                    deviceAddressList.add(device.address)
                }
                adapter.notifyDataSetChanged()
                updateStatus("Found ${pairedDevices!!.size} paired device(s). Tap to connect.")
                Toast.makeText(this, "Found ${pairedDevices!!.size} paired devices", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, "Permission denied: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun connectToDevice(address: String, name: String) {
        updateStatus("Connecting to $name...")
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val device = bluetoothAdapter.getRemoteDevice(address)
                
                // Create a socket connection
                val socket: BluetoothSocket = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(this@BluetoothActivity, Manifest.permission.BLUETOOTH_CONNECT) 
                        != PackageManager.PERMISSION_GRANTED) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@BluetoothActivity, "Bluetooth permission required", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }
                    device.createRfcommSocketToServiceRecord(SPP_UUID)
                } else {
                    device.createRfcommSocketToServiceRecord(SPP_UUID)
                }
                
                // Cancel discovery to save resources
                if (bluetoothAdapter.isDiscovering) {
                    bluetoothAdapter.cancelDiscovery()
                }
                
                // Connect to the device
                withContext(Dispatchers.Main) {
                    updateStatus("Establishing connection...")
                }
                
                socket.connect()
                
                withContext(Dispatchers.Main) {
                    updateStatus("Connected to $name!")
                    Toast.makeText(this@BluetoothActivity, "Successfully connected to $name", Toast.LENGTH_LONG).show()
                    
                    // Store connection info in shared preferences for MainActivity to use
                    val prefs = getSharedPreferences("BluetoothConnection", Context.MODE_PRIVATE)
                    prefs.edit().apply {
                        putString("device_address", address)
                        putString("device_name", name)
                        putBoolean("is_connected", true)
                        apply()
                    }
                    
                    // Return success to MainActivity
                    setResult(RESULT_OK)
                    
                    // Important: We'll pass the socket back via a singleton or service
                    // For now, just indicate success and return
                    Toast.makeText(
                        this@BluetoothActivity,
                        "Connection ready! Returning to main screen...",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    // TODO: Implement proper socket handoff to MainActivity
                    // Options:
                    // 1. Use a Bluetooth service to manage the connection
                    // 2. Use a singleton to hold the socket
                    // 3. Pass via static reference (not recommended)
                    
                    finish()
                }
                
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    updateStatus("Connection failed: ${e.message}")
                    Toast.makeText(
                        this@BluetoothActivity,
                        "Failed to connect: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: SecurityException) {
                withContext(Dispatchers.Main) {
                    updateStatus("Permission error: ${e.message}")
                    Toast.makeText(
                        this@BluetoothActivity,
                        "Permission denied: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted by user ✅", Toast.LENGTH_SHORT).show()
                updateStatus("Bluetooth Status: Permission Granted")
            } else {
                Toast.makeText(this, "Permission denied by user ❌", Toast.LENGTH_SHORT).show()
                updateStatus("Bluetooth Status: Permission Denied")
            }
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Bluetooth enabled", Toast.LENGTH_SHORT).show()
                updateStatus("Bluetooth Status: ON")
            } else {
                Toast.makeText(this, "Bluetooth not enabled", Toast.LENGTH_SHORT).show()
                updateStatus("Bluetooth Status: OFF")
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateBluetoothStatus()
    }
}
