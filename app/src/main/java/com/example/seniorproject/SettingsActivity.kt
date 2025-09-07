package com.example.seniorproject

import android.content.Context
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var fontSizeSpinner: Spinner
    private lateinit var ttsRadioGroup: RadioGroup
    private lateinit var saveButton: Button
    private lateinit var fontPreview: TextView
    private lateinit var ttsPreview: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Initialize views
        fontSizeSpinner = findViewById(R.id.fontSizeSpinner)
        ttsRadioGroup = findViewById(R.id.ttsRadioGroup)
        saveButton = findViewById(R.id.saveSettingsButton)
        fontPreview = findViewById(R.id.fontPreview)
        ttsPreview = findViewById(R.id.ttsPreview)

        // Font sizes
        val fontSizes = arrayOf("16", "18", "20", "24", "28")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, fontSizes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        fontSizeSpinner.adapter = adapter

        // Load saved settings
        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val savedFontSize = prefs.getInt("fontSize", 20)
        val ttsEnabled = prefs.getBoolean("ttsEnabled", true)

        // Set spinner and radio buttons
        fontSizeSpinner.setSelection(fontSizes.indexOf(savedFontSize.toString()))
        ttsRadioGroup.check(if (ttsEnabled) R.id.ttsOn else R.id.ttsOff)

        // Set initial previews
        fontPreview.textSize = savedFontSize.toFloat()
        ttsPreview.text = if (ttsEnabled) "TTS is ON" else "TTS is OFF"

        // Live preview for font size
        fontSizeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View, position: Int, id: Long) {
                fontPreview.textSize = fontSizes[position].toFloat()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Live preview for TTS
        ttsRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            ttsPreview.text = if (checkedId == R.id.ttsOn) "TTS is ON" else "TTS is OFF"
        }

        // Save settings
        saveButton.setOnClickListener {
            val selectedFont = fontSizes[fontSizeSpinner.selectedItemPosition].toInt()
            val ttsOn = ttsRadioGroup.checkedRadioButtonId == R.id.ttsOn

            prefs.edit().apply {
                putInt("fontSize", selectedFont)
                putBoolean("ttsEnabled", ttsOn)
                apply()
            }

            Toast.makeText(this, "Settings saved ✅", Toast.LENGTH_SHORT).show()
            finish() // return to MainActivity
        }
    }
}
