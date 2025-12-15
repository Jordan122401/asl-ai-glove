package com.example.seniorproject

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var pairedDevices: Set<BluetoothDevice>? = null
    private var deviceList: ArrayList<String> = ArrayList()
    private var deviceAddressList: ArrayList<String> = ArrayList()
    private lateinit var adapter: DeviceListAdapter
    
    // Standard UUID for Serial Port Profile (SPP) - Classic Bluetooth
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    
    // Nordic UART Service UUID for BLE (from ASL_BLE_FINAL_frfrfrfr.ino)
    private val NUS_SERVICE_UUID = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
    
    // For device discovery
    private var isDiscovering = false
    private val discoveredDevices = HashMap<String, BluetoothDevice>()
    private val deviceNames = HashMap<String, String>() // Store device names separately
    private val bleScanCallback = BLEScanCallback()

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
        
        // Initialize BLE Scanner
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        // Setup ListView adapter with custom layout
        adapter = DeviceListAdapter(this, deviceList, deviceAddressList)
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
        
        // Scan for devices (both paired and discovered)
        buttonScan.setOnClickListener {
            startBLEScan()
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
        
        // Register for Bluetooth discovery events
        registerBluetoothReceiver()
        
        // Update initial status
        updateBluetoothStatus()
    }
    
    inner class BLEScanCallback : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = result.scanRecord?.deviceName ?: device.name ?: "Unknown Device"
            
            // Check if it's an ASL glove (contains "ASL" in name or has NUS service)
            val isASLGlove = deviceName.contains("ASL", ignoreCase = true) || 
                           result.scanRecord?.serviceUuids?.any { 
                               it.uuid.toString().contains("6E400001", ignoreCase = true) 
                           } == true
            
            if (isASLGlove) {
                discoveredDevices[device.address] = device
                deviceNames[device.address] = deviceName
                updateDeviceList()
            }
        }
        
        override fun onBatchScanResults(results: List<ScanResult>) {
            for (result in results) {
                // Use callback type 1 for batch scan results
                onScanResult(1, result)
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            runOnUiThread {
                Toast.makeText(this@BluetoothActivity, "BLE Scan failed: $errorCode", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun startBLEScan() {
        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Please turn on Bluetooth first", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Check permissions
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasConnect = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            val hasScan = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            hasConnect && hasScan
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
        
        if (!hasPermission) {
            Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_SHORT).show()
            checkBluetoothPermission()
            return
        }
        
        try {
            // Clear previous results
            discoveredDevices.clear()
            deviceNames.clear()
            deviceList.clear()
            deviceAddressList.clear()
            adapter.notifyDataSetChanged()
            
            // Stop any previous scan
            bluetoothLeScanner?.stopScan(bleScanCallback)
            
            // Configure scan settings for low latency
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            
            // Optional: Filter for NUS service UUID
            val nusUUID = ParcelUuid.fromString(NUS_SERVICE_UUID)
            val scanFilter = ScanFilter.Builder()
                .setServiceUuid(nusUUID)
                .build()
            
            // Start BLE scan
            bluetoothLeScanner?.startScan(listOf(scanFilter), scanSettings, bleScanCallback)
            
            updateStatus("Scanning for BLE devices...")
            Toast.makeText(this, "Starting BLE scan for ASL glove...", Toast.LENGTH_SHORT).show()
            
            // Auto-stop scan after 10 seconds
            textStatus.postDelayed({
                bluetoothLeScanner?.stopScan(bleScanCallback)
                updateStatus("Scan complete. Found ${deviceList.size} device(s).")
            }, 10000)
            
        } catch (e: SecurityException) {
            Toast.makeText(this, "Permission denied: ${e.message}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Scan failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun registerBluetoothReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        registerReceiver(bluetoothReceiver, filter)
    }
    
    // Broadcast receiver for Bluetooth discovery
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    
                    device?.let {
                        discoveredDevices[it.address] = it
                        deviceNames[it.address] = it.name ?: "Unknown Device"
                        updateDeviceList()
                    }
                }
                
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    isDiscovering = true
                    updateStatus("Scanning for devices...")
                    Toast.makeText(this@BluetoothActivity, "Scanning for devices...", Toast.LENGTH_SHORT).show()
                }
                
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    isDiscovering = false
                    val totalDevices = deviceList.size
                    updateStatus("Found $totalDevices device(s). Tap to connect.")
                    Toast.makeText(
                        this@BluetoothActivity,
                        "Scan complete. Found $totalDevices device(s)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    private fun updateDeviceList() {
        runOnUiThread {
            deviceList.clear()
            deviceAddressList.clear()
            
            // Add discovered devices
            for ((address, device) in discoveredDevices) {
                val deviceName = deviceNames[address] ?: device.name ?: "Unknown Device"
                val displayText = if (device.bondState == BluetoothDevice.BOND_BONDED) {
                    "$deviceName (Paired)"
                } else {
                    deviceName
                }
                deviceList.add(displayText)
                deviceAddressList.add(address)
            }
            
            adapter.notifyDataSetChanged()
        }
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
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            
            val allGranted = permissions.all { permission ->
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            }
            
            if (allGranted) {
                Toast.makeText(this, "All Bluetooth permissions granted ✅", Toast.LENGTH_SHORT).show()
                updateStatus("Bluetooth Status: All Permissions Granted")
            } else {
                ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
            }
        } else {
            // For older Android versions, just check location permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Location permission granted ✅", Toast.LENGTH_SHORT).show()
                updateStatus("Bluetooth Status: Permission Granted")
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_CODE)
            }
        }
    }
    
    private fun connectToDevice(address: String, name: String) {
        updateStatus("Connecting to $name...")
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val device = bluetoothAdapter.getRemoteDevice(address)
                
                // Check if this is a BLE device
                val isBLE = device.type == BluetoothDevice.DEVICE_TYPE_LE || 
                           device.type == BluetoothDevice.DEVICE_TYPE_DUAL
                
                if (isBLE) {
                    // For BLE, we need to use BluetoothGatt
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@BluetoothActivity,
                            "BLE device detected. Connection in progress...",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        // Store device info for BLE connection
                        val prefs = getSharedPreferences("BluetoothConnection", Context.MODE_PRIVATE)
                        prefs.edit().apply {
                            putString("device_address", address)
                            putString("device_name", name)
                            putBoolean("is_connected", true)
                            putBoolean("is_ble", true)
                            apply()
                        }
                        
                        setResult(RESULT_OK)
                        Toast.makeText(
                            this@BluetoothActivity,
                            "Returning to main screen...",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        finish()
                    }
                } else {
                    // Classic Bluetooth connection (existing code)
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
                    
                    if (bluetoothAdapter.isDiscovering) {
                        bluetoothAdapter.cancelDiscovery()
                    }
                    
                    withContext(Dispatchers.Main) {
                        updateStatus("Establishing connection...")
                    }
                    
                    socket.connect()
                    
                    withContext(Dispatchers.Main) {
                        updateStatus("Connected to $name!")
                        Toast.makeText(this@BluetoothActivity, "Successfully connected to $name", Toast.LENGTH_LONG).show()
                        
                        val prefs = getSharedPreferences("BluetoothConnection", Context.MODE_PRIVATE)
                        prefs.edit().apply {
                            putString("device_address", address)
                            putString("device_name", name)
                            putBoolean("is_connected", true)
                            apply()
                        }
                        
                        setResult(RESULT_OK)
                        Toast.makeText(
                            this@BluetoothActivity,
                            "Connection ready! Returning to main screen...",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        finish()
                    }
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
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "All permissions granted by user ✅", Toast.LENGTH_SHORT).show()
                updateStatus("Bluetooth Status: All Permissions Granted")
            } else {
                Toast.makeText(this, "Some permissions denied by user ❌", Toast.LENGTH_SHORT).show()
                updateStatus("Bluetooth Status: Some Permissions Denied")
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
    
    override fun onDestroy() {
        super.onDestroy()
        // Stop BLE scan
        bluetoothLeScanner?.stopScan(bleScanCallback)
        
        // Cancel discovery if active
        if (bluetoothAdapter.isDiscovering) {
            try {
                bluetoothAdapter.cancelDiscovery()
            } catch (e: Exception) {
                // Ignore
            }
        }
        // Unregister receiver
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            // Receiver may not be registered
        }
    }
    
    // Custom adapter for device list
    private class DeviceListAdapter(
        context: Context,
        private val deviceList: ArrayList<String>,
        private val deviceAddressList: ArrayList<String>
    ) : ArrayAdapter<String>(context, R.layout.item_device, deviceList) {
        
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_device, parent, false)
            
            val deviceNameText = view.findViewById<TextView>(R.id.deviceNameText)
            val deviceAddressText = view.findViewById<TextView>(R.id.deviceAddressText)
            
            if (position < deviceList.size && position < deviceAddressList.size) {
                val deviceName = deviceList[position]
                val deviceAddress = deviceAddressList[position]
                
                deviceNameText.text = deviceName
                deviceAddressText.text = "$deviceAddress [BLE]"
            }
            
            return view
        }
    }
}
