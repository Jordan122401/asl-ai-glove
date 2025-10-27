# Simplified Calibration Using BLE Commands

## Overview

The ASL glove (from `ASL_BLE.ino`) has **built-in calibration** that runs on the device itself. Instead of the app collecting sensor data and calculating calibration values, we simply send commands to the glove to perform calibration.

## How It Works

### Current Approach (to be updated):
- App connects to glove
- App collects sensor readings over time
- App calculates baseline and maximum values
- App saves calibration data

### New Approach (using ASL_BLE firmware):
- App connects to glove via BLE
- App sends calibration command: `"cal"` or `"imu_cal"`
- **Glove performs calibration** and saves to its internal memory (NVS)
- App can query calibration status with `"detail"` command
- **No sensor data collection needed in the app!**

## Available Commands from ASL_BLE.ino

### Calibration Commands:
```bash
# Calibrate flex sensors (rest + max bend for each finger)
"cal"

# Calibrate IMU (accelerometer) - place glove flat and still
"imu_cal"

# View current calibration values (returns JSON)
"detail"

# Switch to user profile (loads their calibration)
"setuser <username>"

# Manually save current calibration to NVS
"savecal"
```

### Streaming Commands:
```bash
# Start continuous streaming (for real-time ASL recognition)
"stream:on"

# Stop streaming
"stream:off"

# Set sampling rate
"rate:30"
```

## Calibration Flow

1. **User opens CalibrationActivity**
   - Shows username
   - Shows connection status

2. **User connects to glove**
   - Taps "Connect to Glove"
   - Returns to calibration screen

3. **User calibrates flex sensors**
   - Taps "Calibrate Flex Sensors"
   - App sends `"cal"` command to glove
   - Glove prompts user (via audio/LED/buzzer or app displays instructions):
     - "Keep hand relaxed for 3 seconds" (rest position)
     - "Bend finger 1 to max" (3 seconds)
     - "Bend finger 2 to max" (3 seconds)
     - ... (repeats for all 5 fingers)
   - Glove saves calibration internally

4. **User calibrates IMU**
   - Taps "Calibrate IMU"
   - App sends `"imu_cal"` command
   - Glove prompts: "Place glove flat and still for 4 seconds"
   - Glove calculates and saves bias offsets

5. **User views calibration (optional)**
   - Taps "View Calibration"
   - App sends `"detail"` command
   - Glove returns JSON with calibration values
   - App displays values to user

6. **Done!**
   - Calibration saved on glove
   - No need to save in app database
   - User can proceed to recognition

## Implementation

### CalibrationActivity Changes:

**Remove:**
- Sensor data collection code
- Averaging calculations
- Calibration value storage in app
- `collectSensorReadings()` method

**Add:**
- BLE command sending function
- Button handlers that send commands
- Better instructions for user actions

### Button Actions:

```kotlin
// Button: "Calibrate Flex Sensors"
calibrateFlexButton.setOnClickListener {
    sendBleCommand("cal")
    updateStep(
        step = "Calibrating Flex Sensors...",
        instruction = "Follow glove prompts:\n1. Keep hand relaxed\n2. Bend each finger to max"
    )
}

// Button: "Calibrate IMU"
calibrateImuButton.setOnClickListener {
    sendBleCommand("imu_cal")
    updateStep(
        step = "Calibrating IMU...",
        instruction = "Place glove flat and still for 4 seconds"
    )
}

// Button: "View Calibration"
viewCalibrationButton.setOnClickListener {
    sendBleCommand("detail")
}
```

## User Profiles

The glove supports multiple user profiles:
- Each user gets their own calibration stored on the glove
- Switch users with: `"setuser username"`
- Calibrations are saved per-user in the glove's NVS storage

## Benefits

1. **Simpler app code** - No complex sensor data collection
2. **More accurate** - Calibration runs on-device with optimized timing
3. **User-friendly** - Glove can provide haptic/audio feedback
4. **Reliable** - No Bluetooth data transfer issues during calibration
5. **Persistent** - Calibration saved on glove, survives power cycles

## Next Steps

1. Remove trial_capture_logger.py (as you mentioned)
2. Simplify CalibrationActivity to send BLE commands only
3. Implement BLE command sending function
4. Update UI to show glove instructions
5. Test calibration flow
