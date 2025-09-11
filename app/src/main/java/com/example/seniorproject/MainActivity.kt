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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import com.example.seniorproject.data.DemoSensorSource

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var editTextInput: EditText
    private lateinit var textViewResult: TextView
    private lateinit var buttonConnect: Button
    private lateinit var buttonSettings: Button
    private lateinit var bluetoothButton: Button
    private lateinit var tts: TextToSpeech
    private lateinit var classifier: ASLClassifier
    private lateinit var inputEdit: EditText
    private lateinit var demo: DemoSensorSource
    private var ttsEnabled = true
    private var fontSize = 20
    private val BLUETOOTH_PERMISSION_REQUEST = 1001

    private var streamJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        classifier = ASLClassifier(this)
        // CHANGED: Demo source emits exactly the 2-feature vectors your POC model expects
        demo = DemoSensorSource(this, periodMs = 300L, csvAsset = "demo_samples.csv") // will fallback if CSV missing
        inputEdit = findViewById(R.id.inputText)

        // CHANGED: start a single demo stream on app launch; remove the extra test block below
        streamJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                val features2 = demo.nextFeatures() ?: continue // length 2 from DemoSensorSource
                val pred = classifier.predict(features2)

                android.util.Log.d("ASLDemo", "feat=${features2.contentToString()} -> ${pred.label} p=${pred.probability}")

                if (pred.probability >= 0.3f) { // tune threshold as needed
                    withContext(Dispatchers.Main) {
                        inputEdit.append(pred.label)
                    }
                }
            }
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

    override fun onDestroy() {
        streamJob?.cancel() // stop the coroutine loop
        streamJob = null

        tts.stop()
        tts.shutdown()
        classifier.close()
        lifecycleScope.launch {
            demo.close()
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
