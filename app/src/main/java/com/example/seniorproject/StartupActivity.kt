package com.example.seniorproject

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class StartupActivity : AppCompatActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_startup)

		val connectButton: Button = findViewById(R.id.buttonConnect)
		val usersButton: Button = findViewById(R.id.buttonUsers)

		connectButton.setOnClickListener {
			startActivity(Intent(this, BluetoothActivity::class.java))
		}

		usersButton.setOnClickListener {
			startActivity(Intent(this, UserSelectionActivity::class.java))
		}
	}
}
