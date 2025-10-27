# BLE Command Guide for ASL Glove

## Overview

The ASL glove firmware (`ASL_BLE.ino`) accepts commands via BLE using the Nordic UART Service (NUS). The app can now send these commands directly to control and interact with the glove.

## How to Send Commands

### In CalibrationActivity

1. **Connect to the glove** first using "Connect to Glove" button
2. **Type your command** in the command input field
3. **Press "Send" button** or press Enter on keyboard
4. **Command is sent** to the glove via BLE

## Available Commands

### Calibration Commands

#### `cal`
Calibrate flex sensors (rest + max bend for each finger)

**What it does:**
- Measures rest position for all 5 fingers (3 seconds)
- Then measures max bend for each finger individually (3 seconds each)
- Saves calibration values to glove's internal memory (NVS)
- Returns JSON with calibration values

**When to use:** First time setup or when glove needs recalibration

**Example:** Type `cal` and press Send

---

#### `imu_cal`
Calibrate IMU (accelerometer) by measuring bias

**What it does:**
- Collects ~120 accelerometer samples over 4 seconds
- Calculates bias offsets for X, Y, Z axes
- Saves offsets to internal memory

**When to use:** When accelerometer readings seem off

**Example:** Type `imu_cal` and press Send

---

#### `detail`
View current calibration values (JSON format)

**What it does:**
- Returns current flex sensor calibration (R_rest, R_max)
- Returns current IMU calibration (ax_off, ay_off, az_off)
- Returns gamma curve value
- Shows which user profile is active

**Example:** Type `detail` and press Send

---

### User Profile Commands

#### `setuser <name>`
Switch to a different user profile

**What it does:**
- Loads calibration data for specified user
- Switches active user profile
- Loads that user's saved calibration from NVS

**Example:** 
- Type `setuser john` and press Send
- Type `setuser alice` to switch users

---

#### `whoami`
Display currently active user

**What it does:**
- Shows which user profile is currently loaded
- Useful to verify which user's calibration is active

**Example:** Type `whoami` and press Send

---

#### `listusers`
Show all registered user profiles

**What it does:**
- Lists all users stored in glove's memory
- Shows comma-separated list of usernames

**Example:** Type `listusers` and press Send

---

### Data Collection Commands

#### `start`
Begin trial data collection (2.5 seconds)

**What it does:**
- Records 75 samples (at 30 Hz)
- Uses current label (set with `label:`)
- Auto-increments trial_id
- Outputs CSV: time, flex1-5, roll, pitch, ax, ay, az, label, trial_id

**When to use:** Collecting training data for a specific gesture

**Example:** 
1. Set label: Type `label:A` and press Send
2. Start collecting: Type `start` and press Send
3. Glove records for 2.5 seconds

---

#### `stop`
Manually stop data collection

**What it does:**
- Immediately stops recording
- Useful if you need to cancel mid-trial

**Example:** Type `stop` and press Send

---

#### `label:<text>` or just `<letter>`
Set the gesture label for data collection

**What it does:**
- Sets label for next trial
- Can use full text or just a letter
- Letter is automatically uppercased (e.g., "a" becomes "A")

**Examples:**
- Type `label:hello` and press Send
- Type `a` and press Send (sets label to "A")
- Type `B` and press Send (sets label to "B")

---

#### `trial:<n>`
Manually set trial ID counter

**What it does:**
- Sets the trial number to specific value
- Usually auto-increments, but can be reset manually

**Example:** Type `trial:1` and press Send

---

### Streaming Commands

#### `stream:on`
Start continuous data streaming

**What it does:**
- Begins sending sensor data continuously (30 Hz)
- Format: time, flex1-5, roll, pitch, ax, ay, az
- No labels or trial IDs (just raw data)
- Used for real-time AI recognition

**When to use:** When you want to see live sensor data or run real-time ASL recognition

**Example:** Type `stream:on` and press Send

---

#### `stream:off`
Stop continuous data streaming

**What it does:**
- Stops the continuous data stream
- Glove stops sending sensor data

**Example:** Type `stream:off` and press Send

---

### Configuration Commands

#### `rate:<Hz>`
Set sampling rate (1-100 Hz)

**What it does:**
- Changes how often glove reads sensors
- Applies to both trial recording and streaming
- Default is 30 Hz
- Higher = more data but more power consumption

**Examples:**
- Type `rate:30` and press Send (default, 30 Hz)
- Type `rate:20` and press Send (slower, less power)
- Type `rate:50` and press Send (faster, more power)

---

#### `setgamma <value>`
Adjust gamma curve for flex sensor response (0.2-5.0)

**What it does:**
- Controls how sensitive flex sensors are to bending
- gamma < 1.0: More sensitive (small bends register larger)
- gamma = 1.0: Linear response (default)
- gamma > 1.0: Less sensitive (more bending required)

**Default:** 1.0

**Examples:**
- Type `setgamma 0.8` and press Send (more sensitive)
- Type `setgamma 1.2` and press Send (less sensitive)

---

#### `savecal`
Manually save current calibration to NVS

**What it does:**
- Forces save of current calibration values
- Usually auto-saves after `cal` or `imu_cal`, but can be triggered manually

**Example:** Type `savecal` and press Send

---

## Quick Reference

### First Time Setup
```
1. setuser default      # Switch to default user
2. cal                  # Calibrate flex sensors
3. imu_cal              # Calibrate accelerometer
```

### Start Data Collection
```
1. stream:on            # Start streaming (optional, to see data)
2. stream:off           # Stop streaming when ready
3. label:A              # Set label to "A"
4. start                # Collect 2.5 seconds of data
5. label:B              # Change label
6. start                # Collect more data
```

### View Settings
```
1. detail               # See all calibration values
2. whoami               # Check current user
3. listusers            # See all users
```

### Adjust Settings
```
1. rate:20              # Change to 20 Hz sampling
2. setgamma 1.2         # Adjust flex sensor sensitivity
```

## Response Format

The glove responds to commands via BLE with messages prefixed with `#`:

- `# CAL FLEX: Rest — keep hand relaxed for ~3 s`
- `# CAL FLEX: done.`
- `# CAL IMU OK: ax_off=0.0023 ...`
- `# ERROR: Run 'cal' first.`

## Troubleshooting

### Command not working?
- Make sure you're connected to the glove
- Check that the command is spelled correctly
- Look for error messages in the sensor readings display

### No response from glove?
- Verify connection is active
- Try simpler commands first (like `detail`)
- Check that glove firmware is running

### Want to see what the glove receives?
- Commands are logged in logcat with tag "CalibrationCommand"
- Use `adb logcat | grep CalibrationCommand` to view

## Integration in App

The command input in CalibrationActivity allows you to:
- Type any command and send it
- See immediate feedback in the sensor readings area
- Keep a history of sent commands (last 10)
- Use keyboard Enter key for quick sending

## Next Steps

To fully implement command sending, you'll need to:
1. Connect to BLE GATT service when device is selected
2. Write commands to RX characteristic UUID: `6E400002-B5A3-F393-E0A9-E50E24DCCA9E`
3. Read responses from TX characteristic UUID: `6E400003-B5A3-F393-E0A9-E50E24DCCA9E`

The foundation is in place - just need to add actual BLE GATT operations!
