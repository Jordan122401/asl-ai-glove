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

class UserSelectionActivity : AppCompatActivity() {

    private lateinit var userRecyclerView: RecyclerView
    private lateinit var usernameInput: EditText
    private lateinit var createUserButton: Button
    private lateinit var emptyUsersText: TextView
    
    private lateinit var userManager: UserManager
    private lateinit var userAdapter: UserAdapter

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

        // Initialize views
        userRecyclerView = findViewById(R.id.userRecyclerView)
        usernameInput = findViewById(R.id.usernameInput)
        createUserButton = findViewById(R.id.createUserButton)
        emptyUsersText = findViewById(R.id.emptyUsersText)

        // Setup RecyclerView
        userAdapter = UserAdapter(
            users = mutableListOf(),
            onUserClick = { user -> onUserSelected(user) },
            onDeleteClick = { user -> onDeleteUser(user) }
        )
        userRecyclerView.layoutManager = LinearLayoutManager(this)
        userRecyclerView.adapter = userAdapter

        // Create user button
        createUserButton.setOnClickListener {
            createNewUser()
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
        
        if (user.isCalibrated) {
            // User has calibration, go to MainActivity
            Toast.makeText(this, "Welcome back, ${user.username}!", Toast.LENGTH_SHORT).show()
            navigateToMainActivity()
        } else {
            // User needs to calibrate
            showCalibrationPrompt(user)
        }
    }

    /**
     * Show a dialog prompting the user to calibrate.
     */
    private fun showCalibrationPrompt(user: User) {
        AlertDialog.Builder(this)
            .setTitle("Calibration Required")
            .setMessage("${user.username} hasn't calibrated yet. Would you like to calibrate now?")
            .setPositiveButton("Calibrate") { _, _ ->
                navigateToCalibration(user)
            }
            .setNegativeButton("Skip") { _, _ ->
                // Allow user to skip calibration and go to main activity
                Toast.makeText(this, "You can calibrate later from Settings", Toast.LENGTH_LONG).show()
                navigateToMainActivity()
            }
            .setCancelable(false)
            .show()
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
     * Create a new user.
     */
    private fun createNewUser() {
        val username = usernameInput.text.toString().trim()
        
        if (username.isEmpty()) {
            Toast.makeText(this, "Please enter a username", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (username.length < 2) {
            Toast.makeText(this, "Username must be at least 2 characters", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (userManager.createUser(username)) {
            Toast.makeText(this, "User '$username' created!", Toast.LENGTH_SHORT).show()
            usernameInput.text.clear()
            refreshUserList()
            
            // Automatically select the new user
            val newUser = userManager.getUser(username)
            if (newUser != null) {
                onUserSelected(newUser)
            }
        } else {
            Toast.makeText(this, "User '$username' already exists", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Navigate to calibration activity.
     */
    private fun navigateToCalibration(user: User) {
        val intent = Intent(this, CalibrationActivity::class.java)
        intent.putExtra("username", user.username)
        startActivityForResult(intent, REQUEST_CODE_CALIBRATION)
    }

    /**
     * Navigate to main activity.
     */
    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_CALIBRATION && resultCode == RESULT_OK) {
            // Calibration completed, refresh list and go to main activity
            refreshUserList()
            navigateToMainActivity()
        }
    }

    companion object {
        private const val REQUEST_CODE_CALIBRATION = 1001
    }
}

/**
 * RecyclerView adapter for displaying users.
 */
class UserAdapter(
    private var users: MutableList<User>,
    private val onUserClick: (User) -> Unit,
    private val onDeleteClick: (User) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val usernameText: TextView = itemView.findViewById(R.id.usernameText)
        val calibrationStatusText: TextView = itemView.findViewById(R.id.calibrationStatusText)
        val deleteButton: Button = itemView.findViewById(R.id.deleteUserButton)

        fun bind(user: User) {
            usernameText.text = user.username
            
            if (user.isCalibrated) {
                calibrationStatusText.text = "✓ Calibrated"
                calibrationStatusText.visibility = View.VISIBLE
            } else {
                calibrationStatusText.text = "Not calibrated"
                calibrationStatusText.visibility = View.VISIBLE
                calibrationStatusText.alpha = 0.5f
            }

            itemView.setOnClickListener {
                onUserClick(user)
            }

            deleteButton.setOnClickListener {
                onDeleteClick(user)
            }
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

