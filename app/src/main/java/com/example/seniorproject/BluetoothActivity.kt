package com.example.seniorproject

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class BluetoothActivity : AppCompatActivity() {

    private lateinit var buttonCheck: Button
    private lateinit var buttonTurnOn: Button
    private val PERMISSION_REQUEST_CODE = 1001
    private val REQUEST_ENABLE_BT = 2001

    private lateinit var bluetoothAdapter: BluetoothAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        when (prefs.getString("theme", "Light")) {
            "Light" -> setTheme(R.style.Theme_SeniorProject_Light)
            "Dark" -> setTheme(R.style.Theme_SeniorProject_Dark)
            else -> setTheme(R.style.Theme_SeniorProject_Light)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth)

        buttonCheck = findViewById(R.id.buttonCheckPermission)
        buttonTurnOn = findViewById(R.id.buttonTurnOnBluetooth)

        // Check Bluetooth Permission
        buttonCheck.setOnClickListener { checkBluetoothPermission() }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: run {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val backButton: Button = findViewById(R.id.buttonBack)
        backButton.setOnClickListener {
            finish() // closes BluetoothActivity and goes back to MainActivity
        }

        // Turn On Bluetooth
        buttonTurnOn.setOnClickListener {
            if (bluetoothAdapter == null) {
                Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Check if Bluetooth is already on
            if (!bluetoothAdapter.isEnabled) {
                // This is the block you asked about
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            } else {
                Toast.makeText(this, "Bluetooth is already ON", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permission = Manifest.permission.BLUETOOTH_CONNECT
            if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Bluetooth permission granted ✅", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Bluetooth permission NOT granted ❌", Toast.LENGTH_SHORT).show()
                ActivityCompat.requestPermissions(this, arrayOf(permission), PERMISSION_REQUEST_CODE)
            }
        } else {
            Toast.makeText(this, "Bluetooth permission automatically granted ✅", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted by user ✅", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permission denied by user ❌", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
