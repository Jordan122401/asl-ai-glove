# Bluetooth Glove Setup Guide

## 📱 Overview

This guide explains how to connect your ASL translation glove to the mobile app via Bluetooth.

## 🔧 Prerequisites

1. **Android Device** with Bluetooth 4.0+ support
2. **ASL Glove** with Bluetooth module configured and powered on
3. **Bluetooth Permissions** granted to the app (will be prompted)

---

## 🚀 Connection Steps

### Step 1: Power On Your Glove

Make sure your ASL glove is powered on and in discovery/pairing mode. Most gloves will automatically enter this mode when powered on.

### Step 2: Launch the App

1. Open the **ASL Translation App**
2. The main screen shows:
   - Input text field (for recognized letters)
   - Connected text display
   - Connection status: "Not connected"
   - Various control buttons

### Step 3: Connect to Glove

1. **Tap "Connect to Glove"** button
2. **Grant Bluetooth permissions** if prompted
3. You'll be taken to the **Bluetooth Connection Screen**

### Step 4: Scan and Select Your Glove

In the Bluetooth screen:

1. **Check Bluetooth Status** - should show "ON" (tap "Turn On Bluetooth" if needed)
2. **Grant permissions** if prompted (tap "Check Bluetooth Permission" if needed)
3. **Tap "Scan for Devices"** to discover nearby Bluetooth devices
4. **Wait for scan to complete** - the app will discover all nearby devices
5. **Look for your glove** in the device list (it may show as "Unknown Device" or have its Bluetooth name)
6. **Tap on your glove** to connect
7. **Wait for connection** - status will update
8. **Return to main screen** when connected successfully

> **Note:** The app can now discover devices that aren't previously paired! No need to pair in Android Settings first.

---

## 📊 Expected Data Format

The glove must send sensor data in the following format:

### Data Structure
```
flex1,flex2,flex3,flex4,flex5,roll,pitch,ax,ay,az
```

### Example Data
```
0.75,0.23,0.12,0.15,0.08,25.3,12.1,0.05,-0.02,1.01
```

### Field Descriptions

| Field | Description | Units | Range |
|-------|-------------|-------|-------|
| `flex1-5` | Flex sensor values (5 fingers) | Normalized | 0.0 - 1.0 |
| `roll` | Hand roll angle | Degrees | -180 to 180 |
| `pitch` | Hand pitch angle | Degrees | -90 to 90 |
| `ax` | X-axis acceleration | G-force | -2.0 to 2.0 |
| `ay` | Y-axis acceleration | G-force | -2.0 to 2.0 |
| `az` | Z-axis acceleration | G-force | -2.0 to 2.0 |

### Sampling Rate
- **Recommended**: 20 Hz (50ms intervals)
- **Minimum**: 10 Hz (100ms intervals)
- **Maximum**: 50 Hz (20ms intervals)

---

## 🔍 How It Works

### Real-time Inference Flow

```
1. Glove sends sensor data → Bluetooth
2. BluetoothSensorSource receives data
3. Data added to SequenceBuffer (75 timesteps)
4. When buffer is 50%+ full:
   a. Extract sequence
   b. Run LSTM model
   c. Run XGBoost fusion
   d. Get prediction + confidence
5. Stability check (3 consecutive same predictions)
6. If stable + confident:
   a. Add letter to text field
   b. Speak via TTS (if enabled)
   c. Clear buffer for next gesture
```

### Prediction Thresholds

- **Confidence**: Minimum 50% to accept prediction
- **Stability**: Requires 3 consecutive identical predictions
- **Buffer Size**: Needs at least 37 timesteps (50% of 75)
- **Neutral Filter**: "Neutral" gestures not added to text

---

## 🛠️ Troubleshooting

### ❌ "Bluetooth not supported"
**Solution**: Your device doesn't have Bluetooth hardware. Try a different device.

### ❌ "No paired devices found"
**Solution**: 
1. Go to Android Settings → Bluetooth
2. Pair your glove manually first
3. Return to app and scan again

### ❌ "Connection failed"
**Possible causes**:
1. **Glove is off or out of range** - Turn it on and move closer
2. **Already connected to another device** - Disconnect from other device
3. **Wrong UUID** - Ensure glove uses SPP UUID: `00001101-0000-1000-8000-00805F9B34FB`
4. **Permission denied** - Grant Bluetooth permissions in app settings

### ❌ "Permission denied"
**Solution**:
1. Open Android Settings → Apps → ASL Translation App → Permissions
2. Enable "Nearby devices" (Android 12+) or "Location" (Android 11-)
3. Restart the app

### ❌ "No predictions appearing"
**Possible causes**:
1. **Wrong data format** - Check glove sends correct CSV format
2. **Connection lost** - Check Bluetooth connection
3. **Low confidence** - Predictions below 50% are filtered out
4. **Buffer not full** - Needs at least 37 timesteps to predict

### ❌ "Connection lost" during use
**Solution**:
1. **Check battery** - Low glove battery can cause disconnections
2. **Reduce distance** - Stay within 10 meters
3. **Avoid interference** - Move away from WiFi routers, microwaves
4. **Reconnect** - Go back to Bluetooth screen and reconnect

---

## 💡 Tips for Best Performance

### 1. Optimal Positioning
- **Hold hand steady** for 1-2 seconds per gesture
- **Keep hand visible** and away from body
- **Avoid rapid movements** between gestures

### 2. Connection Stability
- **Keep device nearby** (within 5 meters recommended)
- **Avoid obstacles** between glove and phone
- **Use fresh batteries** in the glove

### 3. Accuracy Improvement
- **Make distinct gestures** - Clear differences between letters
- **Hold position briefly** - Allows stability check to work
- **Wait for feedback** - TTS will confirm recognized letter

### 4. Battery Conservation
- **Disconnect when not in use** - Saves both phone and glove battery
- **Lower sampling rate** if possible (20 Hz is optimal)

---

## 🔐 Required Permissions

The app requires the following Android permissions:

### Android 12+ (API 31+)
- `BLUETOOTH_CONNECT` - Connect to paired devices
- `BLUETOOTH_SCAN` - Discover new devices (future feature)

### Android 11 and below
- `BLUETOOTH` - Basic Bluetooth operations
- `BLUETOOTH_ADMIN` - Manage Bluetooth connections
- `ACCESS_FINE_LOCATION` - Required for Bluetooth scanning

---

## 🎯 Next Steps After Connection

Once connected:

1. **Connection status updates** to show current prediction
2. **Make ASL gestures** with your glove
3. **Watch predictions appear** in real-time status
4. **Letters are added** to text field when stable
5. **TTS speaks** recognized letters (if enabled)
6. **Use "Connect Text"** button to combine letters
7. **Use "Clear Text"** button to start over

---

## 📱 Connection Indicators

### Status Messages

| Status | Meaning |
|--------|---------|
| `Not connected` | No active Bluetooth connection |
| `Connected - A (85%)` | Connected, predicting letter A with 85% confidence |
| `Connection lost` | Bluetooth disconnected unexpectedly |

### What They Mean

- **High confidence (70-100%)**: Very confident prediction
- **Medium confidence (50-69%)**: Acceptable prediction  
- **Low confidence (<50%)**: Prediction filtered out (not shown)

---

## 🔄 Reconnection

If connection is lost during use:

1. **App will auto-detect** disconnection
2. **Status updates** to "Connection lost"
3. **Inference stream stops** automatically
4. **Tap "Connect to Glove"** again to reconnect
5. **Select glove** from device list
6. **Resume using** the app

---

## ⚙️ Advanced Configuration

### Adjust Prediction Thresholds

Edit `MainActivity.kt` to customize:

```kotlin
// Line ~169: Stability threshold
val STABILITY_THRESHOLD = 3  // Change to 1, 2, 3, 4, or 5

// Line ~219: Confidence threshold  
pred.probability >= 0.5f  // Change to 0.3f - 0.9f
```

### Modify Buffer Size

Edit `MainActivity.kt`:

```kotlin
// Line ~189: Buffer fill threshold
fusionClassifier.getSequenceLength() / 2  // Change /2 to /3 or /4
```

---

## 📚 Technical Details

### Bluetooth Protocol
- **Profile**: Serial Port Profile (SPP)
- **UUID**: `00001101-0000-1000-8000-00805F9B34FB`
- **Mode**: RFCOMM socket connection
- **Data**: Text-based CSV over serial

### Data Reception
- **Class**: `BluetoothSensorSource`
- **Method**: Asynchronous coroutine-based reading
- **Error Handling**: Automatic reconnection detection

---

## 🆘 Getting Help

If you encounter issues not covered here:

1. **Check logcat** for detailed error messages:
   ```bash
   adb logcat | grep -i "bluetooth\|inference"
   ```

2. **Verify glove firmware** is sending correct data format

3. **Test with a Bluetooth terminal app** to see raw data

4. **Check model files** are properly loaded in assets folder

---

**Ready to connect! Follow the steps above to start using your ASL glove.** 🎉

