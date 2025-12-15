package com.example.seniorproject

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.seniorproject.data.User
import com.example.seniorproject.data.UserManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserSelectionActivity : AppCompatActivity() {

    private lateinit var userRecyclerView: RecyclerView
    private lateinit var usernameInput: EditText
    private lateinit var createUserButton: Button
    private lateinit var emptyUsersText: TextView
    
    private lateinit var userManager: UserManager
    private lateinit var userAdapter: UserAdapter
    private var bleService: BLEService? = null
    
    // Pre-populated users
    private val PRE_POPULATED_USERS = listOf("Jordan", "Madison", "Davaney", "Raul", "Marc")

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before calling super
        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        when (prefs.getString("theme", "Light")) {
            "Light" -> setTheme(R.style.Theme_SeniorProject_Light)
            "Dark" -> setTheme(R.style.Theme_SeniorProject_Dark)
            else -> setTheme(R.style.Theme_SeniorProject_Light)
        }
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_selection)

        // Initialize UserManager
        userManager = UserManager(this)
        
        // Initialize pre-populated users if they don't exist
        initializePrePopulatedUsers()

        // Initialize views
        userRecyclerView = findViewById(R.id.userRecyclerView)
        usernameInput = findViewById(R.id.usernameInput)
        createUserButton = findViewById(R.id.createUserButton)
        emptyUsersText = findViewById(R.id.emptyUsersText)
        
        // Hide the create user section since we're using pre-populated users
        findViewById<View>(R.id.newUserContainer).visibility = View.GONE

        // Setup RecyclerView
        userAdapter = UserAdapter(
            users = mutableListOf(),
            onUserClick = { user -> onUserSelected(user) },
            onDeleteClick = { user -> onDeleteUser(user) },
            showDeleteButton = false // Hide delete button for pre-populated users
        )
        userRecyclerView.layoutManager = LinearLayoutManager(this)
        userRecyclerView.adapter = userAdapter
        
        // Connect to BLE service to send setuser command
        lifecycleScope.launch(Dispatchers.IO) {
            connectToBleService()
        }

        // Load and display users
        refreshUserList()
    }

    /**
     * Refresh the list of users from UserManager.
     */
    private fun refreshUserList() {
        val users = userManager.getAllUsers()
        userAdapter.updateUsers(users)
        
        // Show/hide empty state
        if (users.isEmpty()) {
            emptyUsersText.visibility = View.VISIBLE
            userRecyclerView.visibility = View.GONE
        } else {
            emptyUsersText.visibility = View.GONE
            userRecyclerView.visibility = View.VISIBLE
        }
    }

    /**
     * Handle user selection.
     */
    private fun onUserSelected(user: User) {
        userManager.setCurrentUser(user)
        
        // Send setuser command to the glove via BLE
        lifecycleScope.launch(Dispatchers.IO) {
            sendSetUserCommand(user.username)
        }
        
        // All users are pre-calibrated on the ESP32, so go directly to MainActivity
        Toast.makeText(this, "Welcome, ${user.username}!", Toast.LENGTH_SHORT).show()
        navigateToMainActivity()
    }


    /**
     * Handle user deletion.
     */
    private fun onDeleteUser(user: User) {
        AlertDialog.Builder(this)
            .setTitle("Delete User")
            .setMessage("Are you sure you want to delete ${user.username}? This will remove all calibration data.")
            .setPositiveButton("Delete") { _, _ ->
                if (userManager.deleteUser(user.username)) {
                    Toast.makeText(this, "${user.username} deleted", Toast.LENGTH_SHORT).show()
                    refreshUserList()
                } else {
                    Toast.makeText(this, "Failed to delete user", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Initialize pre-populated users if they don't exist.
     * Also removes old "User1-5" users if they exist.
     */
    private fun initializePrePopulatedUsers() {
        // Remove old "User1-5" users if they exist
        for (i in 1..5) {
            val oldUsername = "User$i"
            if (userManager.userExists(oldUsername)) {
                userManager.deleteUser(oldUsername)
            }
        }
        
        // Create new pre-populated users if they don't exist
        for (username in PRE_POPULATED_USERS) {
            if (!userManager.userExists(username)) {
                userManager.createUser(username)
            }
        }
    }
    
    /**
     * Connect to BLE service to send commands to the glove.
     */
    private suspend fun connectToBleService() {
        try {
            val prefs = getSharedPreferences("BluetoothConnection", Context.MODE_PRIVATE)
            val deviceAddress = prefs.getString("device_address", null)
            val isBLE = prefs.getBoolean("is_ble", false)
            
            if (isBLE && deviceAddress != null) {
                val bluetoothManager = withContext(Dispatchers.Main) {
                    getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                }
                val adapter = bluetoothManager.adapter
                val device = adapter.getRemoteDevice(deviceAddress)
                
                bleService = BLEService(this@UserSelectionActivity).apply {
                    onConnectionStateChange = { connected ->
                        android.util.Log.d("UserSelection", "BLE connection state: $connected")
                    }
                    
                    onDataReceived = { data ->
                        android.util.Log.d("UserSelection", "BLE data received: $data")
                    }
                    
                    connect(device)
                }
                android.util.Log.d("UserSelection", "BLE service initialized")
            } else {
                android.util.Log.d("UserSelection", "No BLE device available")
            }
        } catch (e: Exception) {
            android.util.Log.e("UserSelection", "Failed to connect to BLE service", e)
        }
    }
    
    /**
     * Send setuser command to the glove.
     * Format: setuser "name" (without quotes in the actual command)
     */
    private suspend fun sendSetUserCommand(username: String) {
        val service = bleService
        if (service != null && service.isConnected()) {
            // Send command as: setuser Jordan (not setuser "Jordan")
            val success = service.writeCommand("setuser $username")
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(this@UserSelectionActivity, "Set user to $username on glove", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@UserSelectionActivity, "Failed to set user on glove", Toast.LENGTH_SHORT).show()
                }
            }
            android.util.Log.d("UserSelection", "Sent setuser $username command - Success: $success")
        } else {
            android.util.Log.d("UserSelection", "BLE not connected - cannot send setuser command")
            withContext(Dispatchers.Main) {
                Toast.makeText(this@UserSelectionActivity, "Glove not connected - Set user on glove manually", Toast.LENGTH_SHORT).show()
            }
        }
    }


    /**
     * Navigate to main activity.
     */
    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cleanup BLE service
        bleService?.disconnect()
        bleService = null
    }
}

/**
 * RecyclerView adapter for displaying users.
 */
class UserAdapter(
    private var users: MutableList<User>,
    private val onUserClick: (User) -> Unit,
    private val onDeleteClick: (User) -> Unit,
    private val showDeleteButton: Boolean = true
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val usernameButton: Button = itemView.findViewById(R.id.usernameButton)

        fun bind(user: User) {
            usernameButton.text = user.username
            usernameButton.setOnClickListener { onUserClick(user) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(users[position])
    }

    override fun getItemCount(): Int = users.size

    fun updateUsers(newUsers: List<User>) {
        users.clear()
        users.addAll(newUsers)
        notifyDataSetChanged()
    }
}

