package com.example.seniorproject

import android.content.Context
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import com.example.seniorproject.data.UserManager


class SettingsActivity : AppCompatActivity() {

    private lateinit var fontSizeSpinner: Spinner
    private lateinit var ttsRadioGroup: RadioGroup
    private lateinit var saveButton: Button
    private lateinit var fontPreview: TextView
    private lateinit var ttsPreview: TextView

    private lateinit var themeSpinner: Spinner

    private lateinit var speechRateSeekBar: SeekBar
    private lateinit var speechRatePreview: TextView

    private lateinit var languageSpinner: Spinner
    
    // User management
    private lateinit var currentUserDisplay: TextView
    private lateinit var recalibrateButton: Button
    private lateinit var switchUserButton: Button
    private lateinit var connectGloveButton: Button
    private lateinit var userManager: UserManager

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        when (prefs.getString("theme", "Light")) {
            "Light" -> setTheme(R.style.Theme_SeniorProject_Light)
            "Dark" -> setTheme(R.style.Theme_SeniorProject_Dark)
            else -> setTheme(R.style.Theme_SeniorProject_Light)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Initialize UserManager
        userManager = UserManager(this)

        // Initialize views
        fontSizeSpinner = findViewById(R.id.fontSizeSpinner)
        ttsRadioGroup = findViewById(R.id.ttsRadioGroup)
        saveButton = findViewById(R.id.saveSettingsButton)
        fontPreview = findViewById(R.id.fontPreview)
        ttsPreview = findViewById(R.id.ttsPreview)
        themeSpinner = findViewById(R.id.themeSpinner)
        currentUserDisplay = findViewById(R.id.currentUserDisplay)
        recalibrateButton = findViewById(R.id.recalibrateButton)
        switchUserButton = findViewById(R.id.switchUserButton)
        connectGloveButton = findViewById(R.id.connectGloveButton)

        // Theme
        val themes = arrayOf("Light", "Dark")
        val themeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, themes)
        themeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        themeSpinner.adapter = themeAdapter

        // Speech Rate
        speechRateSeekBar = findViewById(R.id.speechRateSeekBar)
        speechRatePreview = findViewById(R.id.speechRatePreview)

        // Language
        languageSpinner = findViewById(R.id.languageSpinner)
        val languages = arrayOf("English", "Spanish", "French") // List supported
        val langAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
        langAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        languageSpinner.adapter = langAdapter

        // Font sizes
        val fontSizes = arrayOf("16", "18", "20", "24", "28")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, fontSizes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        fontSizeSpinner.adapter = adapter

        // Load saved settings
        val savedFontSize = prefs.getInt("fontSize", 20)
        val ttsEnabled = prefs.getBoolean("ttsEnabled", true)
        val savedTheme = prefs.getString("theme", "Light")
        val savedSpeechRate = prefs.getFloat("speechRate", 1.0f)
        val savedLanguage = prefs.getString("language", "English")

// Set spinner and radio buttons for old and new settings
        fontSizeSpinner.setSelection(fontSizes.indexOf(savedFontSize.toString()))
        ttsRadioGroup.check(if (ttsEnabled) R.id.ttsOn else R.id.ttsOff)
        themeSpinner.setSelection(themes.indexOf(savedTheme))
        speechRateSeekBar.progress = (savedSpeechRate * 100).toInt()
        languageSpinner.setSelection(languages.indexOf(savedLanguage))

// Set initial previews for old and new
        fontPreview.textSize = savedFontSize.toFloat()
        ttsPreview.text = if (ttsEnabled) "TTS is ON" else "TTS is OFF"
        speechRatePreview.text = "Speech Rate: $savedSpeechRate"

// Live preview for font size
        fontSizeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: android.view.View,
                position: Int,
                id: Long
            ) {
                fontPreview.textSize = fontSizes[position].toFloat()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

// Live preview for TTS
        ttsRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            ttsPreview.text = getString(
                if (checkedId == R.id.ttsOn) R.string.tts_is_on else R.string.tts_is_off
            )}


// Live preview for Speech Rate
        speechRateSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                val rate = value / 100f
                speechRatePreview.text = "Speech Rate: $rate"
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

// Save settings
        saveButton.setOnClickListener {
            val selectedFont = fontSizes[fontSizeSpinner.selectedItemPosition].toInt()
            val ttsOn = ttsRadioGroup.checkedRadioButtonId == R.id.ttsOn
            val selectedTheme = themeSpinner.selectedItem.toString()
            val speechRate = speechRateSeekBar.progress / 100f
            val selectedLanguage = languageSpinner.selectedItem.toString()

            prefs.edit().apply {
                putInt("fontSize", selectedFont)
                putBoolean("ttsEnabled", ttsOn)
                putString("theme", selectedTheme)
                putFloat("speechRate", speechRate)
                putString("language", selectedLanguage)
                apply()
            }

            Toast.makeText(this, "Settings saved ✅", Toast.LENGTH_SHORT).show()
            // Restart MainActivity to apply saved settings (especially theme)
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finishAffinity() // return to MainActivity
        }
        
        // Display current user
        updateCurrentUserDisplay()
        
        // Recalibrate button (now navigates to Command Log)
        recalibrateButton.setOnClickListener {
            val intent = Intent(this, BLELogActivity::class.java)
            startActivity(intent)
        }
        
        // Switch user button
        switchUserButton.setOnClickListener {
            // Clear current user and go to user selection
            val intent = Intent(this, UserSelectionActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finishAffinity()
        }

        // Connect to Glove from Settings
        connectGloveButton.setOnClickListener {
            startActivity(Intent(this, BluetoothActivity::class.java))
        }
    }
    
    /**
     * Update the display of the current user.
     */
    private fun updateCurrentUserDisplay() {
        val currentUser = userManager.getCurrentUser()
        if (currentUser != null) {
            currentUserDisplay.text = "Current User: ${currentUser.username}"
        } else {
            currentUserDisplay.text = "Current User: None"
        }
    }
}

