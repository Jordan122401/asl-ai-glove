# User Management System Guide

## Overview

The ASL Glove Translation app now includes a comprehensive user management system that allows multiple users to create profiles and store individual calibration data. This ensures that each user gets personalized and accurate gesture recognition based on their hand size and glove fit.

## Features

### 1. **User Selection Screen (Startup)**
- The app now starts with a user selection screen
- Users can:
  - View all existing user profiles
  - Select an existing user to continue
  - Create a new user profile
  - Delete unused profiles

### 2. **Individual Calibration Storage**
- Each user profile stores personalized calibration data including:
  - Sensor baseline values (rest position)
  - Sensor maximum values (full flex position)
  - Calibration timestamp
- Calibration data is automatically applied during gesture recognition
- Users can recalibrate at any time from Settings

### 3. **Seamless User Experience**
- No login required - just username selection
- Calibrated users can start using the app immediately
- New/uncalibrated users are prompted to calibrate
- Option to skip calibration and calibrate later

## User Flow

### First Time User
1. **App Launch** → User Selection Screen
2. **Enter Username** → Create new user profile
3. **Calibration Prompt** → Guided through calibration process
4. **Main Activity** → Start translating ASL gestures

### Returning User (Calibrated)
1. **App Launch** → User Selection Screen
2. **Select Username** → Profile loaded automatically
3. **Main Activity** → Start translating with personalized calibration

### Returning User (Not Calibrated)
1. **App Launch** → User Selection Screen
2. **Select Username** → Prompted to calibrate
3. **Choice**: Calibrate now or skip
4. **Main Activity** → Start translating (with option to calibrate later from Settings)

## Key Components

### Data Classes

#### `User.kt`
- Stores user profile information
- Fields:
  - `username: String` - Unique identifier
  - `isCalibrated: Boolean` - Calibration status
  - `calibrationData: CalibrationData?` - Sensor calibration values

#### `CalibrationData.kt`
- Stores sensor calibration parameters
- Fields:
  - `sensorBaselines: FloatArray` - Rest position values
  - `sensorMaximums: FloatArray` - Full flex values
  - `calibrationTimestamp: Long` - When calibration was performed
- Methods:
  - `normalizeSensorValue()` - Normalize individual sensor reading
  - `normalizeAllSensors()` - Normalize all sensor readings at once

#### `UserManager.kt`
- Manages user profiles and persistence
- Uses SharedPreferences for storage
- Key methods:
  - `getAllUsers()` - Get list of all users
  - `getUser(username)` - Get specific user
  - `createUser(username)` - Create new user
  - `deleteUser(username)` - Delete user profile
  - `updateUserCalibration()` - Save calibration data
  - `getCurrentUser()` - Get active user
  - `setCurrentUser()` - Set active user

### Activities

#### `UserSelectionActivity.kt`
- **Purpose**: Startup screen for user management
- **Features**:
  - Display list of existing users with calibration status
  - Create new user profiles
  - Select user to continue
  - Delete user profiles with confirmation
- **RecyclerView Adapter**: `UserAdapter` for displaying user list

#### `CalibrationActivity.kt`
- **Purpose**: Guide users through sensor calibration
- **Process**:
  1. **Connect to Glove** via Bluetooth
  2. **Calibrate Rest Position** - Hand relaxed, fingers straight
  3. **Calibrate Flex Position** - Hand making a fist, fingers fully flexed
  4. **Save Calibration** - Store values to user profile
- **Features**:
  - Real-time sensor readings display
  - Step-by-step instructions
  - Option to skip calibration
  - Validation and error handling

#### `MainActivity.kt` (Updated)
- **New Features**:
  - Loads current user on startup
  - Applies calibration data to sensor readings in real-time
  - Displays current user and calibration status
  - Redirects to user selection if no user is set
- **Calibration Integration**:
  - Raw sensor values are normalized using `calibrationData.normalizeAllSensors()`
  - Improves gesture recognition accuracy

#### `SettingsActivity.kt` (Updated)
- **New Features**:
  - Display current user information
  - **Recalibrate Button** - Re-run calibration for current user
  - **Switch User Button** - Return to user selection screen

### Layouts

#### `activity_user_selection.xml`
- Clean, modern UI with logo and branding
- Scrollable user list with RecyclerView
- New user creation section at bottom
- Shows calibration status for each user

#### `item_user.xml`
- User list item layout
- Shows username, calibration status, and delete button
- Clickable to select user

#### `activity_calibration.xml`
- Clear instructions and step-by-step guidance
- Connection status display
- Real-time sensor readings (for debugging/feedback)
- Action buttons for each calibration step
- Skip option for flexibility

## Calibration Process Details

### Why Calibration is Important
- Hand sizes vary between users
- Glove fit affects sensor readings
- Different finger flexibility affects sensor ranges
- Calibration normalizes sensor values to [0, 1] range for consistent ML predictions

### How Calibration Works

1. **Rest Position (Baseline)**
   - User relaxes hand completely
   - App samples sensors 30 times over 3 seconds
   - Averages readings to get baseline values
   - These represent the "zero" point for each sensor

2. **Flex Position (Maximum)**
   - User makes a tight fist
   - App samples sensors 30 times over 3 seconds
   - Averages readings to get maximum values
   - These represent the "maximum" point for each sensor

3. **Normalization During Use**
   - For each sensor reading during gesture recognition:
   - `normalized_value = (raw_value - baseline) / (maximum - baseline)`
   - Clamped to [0, 1] range
   - Ensures consistent input to ML model regardless of hand size

## Settings Integration

### User Management Section
Located in Settings activity with:
- **Current User Display**: Shows active user and calibration status
- **Recalibrate Button**: Opens calibration for current user
- **Switch User Button**: Returns to user selection screen (logs out current user)

## Data Persistence

### SharedPreferences Storage
- **Preference File**: `UserProfiles`
- **Stored Data**:
  - List of all user profiles (JSON array)
  - Current active user (JSON object)
  - Last selected username (string)

### JSON Structure

#### User Profile
```json
{
  "username": "John",
  "isCalibrated": true,
  "calibrationData": {
    "sensorBaselines": [150.2, 145.8, 160.3, 155.1, 148.9],
    "sensorMaximums": [890.5, 885.3, 910.2, 895.7, 880.4],
    "calibrationTimestamp": 1698765432000
  }
}
```

## Best Practices

### For Users
1. **Create a personal profile** - Don't share profiles as calibration is personalized
2. **Calibrate in a comfortable position** - Sit or stand as you normally would
3. **Recalibrate if needed** - If the glove fit changes or accuracy decreases
4. **Use descriptive usernames** - Makes it easy to identify your profile

### For Developers
1. **Sensor Count Configuration** - Update `numSensors` in `CalibrationActivity` based on your glove
2. **Sampling Parameters** - Adjust `samplesCount` and `delayMs` in `collectSensorReadings()` if needed
3. **Stability Threshold** - Tune `STABILITY_THRESHOLD` in MainActivity for prediction stability
4. **Error Handling** - All user operations include proper error handling and user feedback

## Future Enhancements

Potential improvements for the user management system:

1. **User Authentication** - Add optional PIN/password protection
2. **Cloud Sync** - Sync user profiles across devices
3. **Calibration History** - Track calibration history and accuracy metrics
4. **Export/Import** - Allow users to backup and restore profiles
5. **Multi-Glove Support** - Store calibration for different gloves per user
6. **Advanced Calibration** - Gesture-specific calibration for improved accuracy
7. **User Statistics** - Track usage patterns and gesture accuracy per user

## Troubleshooting

### User can't be selected
- **Cause**: No calibration data or corrupted profile
- **Solution**: Try recalibrating or delete and recreate user

### Calibration fails
- **Cause**: Bluetooth connection lost or sensor data unavailable
- **Solution**: Reconnect to glove and try again

### Gesture recognition inaccurate
- **Cause**: Outdated or incorrect calibration
- **Solution**: Recalibrate from Settings → Recalibrate button

### App redirects to user selection on launch
- **Cause**: No current user is set (expected behavior for first launch)
- **Solution**: Select or create a user to continue

## Technical Notes

### Thread Safety
- All SharedPreferences operations are synchronized
- UserManager uses proper locking for concurrent access

### Memory Management
- User data is loaded on demand
- Calibration data is only kept in memory for current user
- RecyclerView uses efficient view recycling

### Performance
- User list loads instantly (SharedPreferences is fast)
- Calibration sampling runs on IO dispatcher (non-blocking)
- Real-time sensor processing optimized for minimal latency

## Contact & Support

For questions or issues with the user management system:
- Check app logs for detailed error messages
- Verify Bluetooth connection is stable during calibration
- Ensure proper glove fit before calibrating

---

**Version**: 1.0  
**Last Updated**: October 2025  
**Compatibility**: Android API 24+ (Android 7.0+)

