package com.example.seniorproject.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages user profiles and calibration data.
 * Uses SharedPreferences for persistent storage.
 */
class UserManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "UserProfiles"
        private const val KEY_USERS = "users"
        private const val KEY_CURRENT_USER = "current_user"
        private const val KEY_LAST_SELECTED_USER = "last_selected_user"
    }
    
    /**
     * Get all registered users.
     */
    fun getAllUsers(): List<User> {
        val usersJson = prefs.getString(KEY_USERS, "[]") ?: "[]"
        val jsonArray = JSONArray(usersJson)
        val users = mutableListOf<User>()
        
        for (i in 0 until jsonArray.length()) {
            try {
                val userJson = jsonArray.getJSONObject(i)
                users.add(User.fromJson(userJson))
            } catch (e: Exception) {
                android.util.Log.e("UserManager", "Error parsing user at index $i", e)
            }
        }
        
        return users
    }
    
    /**
     * Get a specific user by username.
     */
    fun getUser(username: String): User? {
        return getAllUsers().find { it.username.equals(username, ignoreCase = true) }
    }
    
    /**
     * Create a new user.
     * Returns true if successful, false if user already exists.
     */
    fun createUser(username: String): Boolean {
        if (username.isBlank()) {
            return false
        }
        
        // Check if user already exists
        if (getUser(username) != null) {
            return false
        }
        
        val users = getAllUsers().toMutableList()
        users.add(User(username = username.trim(), isCalibrated = false, calibrationData = null))
        saveUsers(users)
        
        return true
    }
    
    /**
     * Delete a user.
     */
    fun deleteUser(username: String): Boolean {
        val users = getAllUsers().toMutableList()
        val removed = users.removeIf { it.username.equals(username, ignoreCase = true) }
        
        if (removed) {
            saveUsers(users)
            
            // Clear current user if it was deleted
            if (getCurrentUser()?.username.equals(username, ignoreCase = true)) {
                setCurrentUser(null)
            }
        }
        
        return removed
    }
    
    /**
     * Update a user's calibration data.
     */
    fun updateUserCalibration(username: String, calibrationData: CalibrationData): Boolean {
        val users = getAllUsers().toMutableList()
        val userIndex = users.indexOfFirst { it.username.equals(username, ignoreCase = true) }
        
        if (userIndex == -1) {
            return false
        }
        
        // Update the user with new calibration data
        val updatedUser = users[userIndex].copy(
            isCalibrated = true,
            calibrationData = calibrationData
        )
        users[userIndex] = updatedUser
        saveUsers(users)
        
        // Update current user if it matches
        if (getCurrentUser()?.username.equals(username, ignoreCase = true)) {
            setCurrentUser(updatedUser)
        }
        
        return true
    }
    
    /**
     * Get the currently active user.
     */
    fun getCurrentUser(): User? {
        val currentUserJson = prefs.getString(KEY_CURRENT_USER, null) ?: return null
        return try {
            User.fromJson(JSONObject(currentUserJson))
        } catch (e: Exception) {
            android.util.Log.e("UserManager", "Error parsing current user", e)
            null
        }
    }
    
    /**
     * Set the current active user.
     */
    fun setCurrentUser(user: User?) {
        val editor = prefs.edit()
        if (user == null) {
            editor.remove(KEY_CURRENT_USER)
            editor.remove(KEY_LAST_SELECTED_USER)
        } else {
            editor.putString(KEY_CURRENT_USER, user.toJson().toString())
            editor.putString(KEY_LAST_SELECTED_USER, user.username)
        }
        editor.apply()
    }
    
    /**
     * Get the last selected username (for convenience).
     */
    fun getLastSelectedUsername(): String? {
        return prefs.getString(KEY_LAST_SELECTED_USER, null)
    }
    
    /**
     * Check if a username exists.
     */
    fun userExists(username: String): Boolean {
        return getUser(username) != null
    }
    
    /**
     * Save the list of users to SharedPreferences.
     */
    private fun saveUsers(users: List<User>) {
        val jsonArray = JSONArray()
        users.forEach { user ->
            jsonArray.put(user.toJson())
        }
        
        prefs.edit().putString(KEY_USERS, jsonArray.toString()).apply()
    }
    
    /**
     * Clear all user data (for testing/debugging).
     */
    fun clearAllUsers() {
        prefs.edit().clear().apply()
    }
}

