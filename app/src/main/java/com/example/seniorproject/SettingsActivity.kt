package com.example.seniorproject

import android.content.Context
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var fontSizeSpinner: Spinner
    private lateinit var ttsRadioGroup: RadioGroup
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        fontSizeSpinner = findViewById(R.id.fontSizeSpinner)
        ttsRadioGroup = findViewById(R.id.ttsRadioGroup)
        saveButton = findViewById(R.id.saveSettingsButton)

        // Font size options
        val fontSizes = arrayOf("16", "18", "20", "24", "28", "32")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, fontSizes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        fontSizeSpinner.adapter = adapter

        // Load saved preferences
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        fontSizeSpinner.setSelection(fontSizes.indexOf(prefs.getInt("fontSize", 20).toString()))
        if (prefs.getBoolean("ttsOn", true)) {
            ttsRadioGroup.check(R.id.ttsOn)
        } else {
            ttsRadioGroup.check(R.id.ttsOff)
        }

        // Save button click
        saveButton.setOnClickListener {
            val editor = prefs.edit()
            editor.putInt("fontSize", fontSizeSpinner.selectedItem.toString().toInt())
            editor.putBoolean("ttsOn", ttsRadioGroup.checkedRadioButtonId == R.id.ttsOn)
            editor.apply()
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            finish() // Return to MainActivity
        }
    }
}
