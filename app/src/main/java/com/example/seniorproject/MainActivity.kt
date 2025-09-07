package com.example.seniorproject

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.Intent

import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // Declare views as properties
    private lateinit var editTextInput: EditText
    private lateinit var textViewResult: TextView
    private lateinit var buttonConnect: Button
    private lateinit var buttonSettings: Button

    private lateinit var tts: TextToSpeech

    private var ttsEnabled = true
    private var fontSize = 20

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

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

        // Initialize TextToSpeech
        tts = TextToSpeech(this, this)

        // Connect Text button logic
        buttonConnect.setOnClickListener {
            val lines = editTextInput.text.toString().split("\n")
            val connected = lines.joinToString(" ") { it.replace(" ", "") }

            // Set text and font size
            textViewResult.text = connected
            textViewResult.textSize = fontSize.toFloat()

            // Speak text only if TTS is enabled
            if (ttsEnabled) {
                tts.speak(connected, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }

        // Settings button placeholder
        buttonSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }
    override fun onResume() {
        super.onResume()

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        fontSize = prefs.getInt("fontSize", 20)
        ttsEnabled = prefs.getBoolean("ttsOn", true)

        // Apply font size immediately to connectedText
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
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}
