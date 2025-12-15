# Python Calibration Integration Guide

This guide explains how to use your existing `trial_capture_logger.py` script with the Android app for glove calibration.

## 🔧 **Integration Overview**

The system now supports two calibration methods:
1. **Direct Android Calibration** - Use the app's built-in calibration
2. **Python Script Calibration** - Use your existing Python script and import the results

## 📁 **Files Created**

### Python Scripts
- `calibration_capture.py` - Modified version of your trial_capture_logger.py for calibration
- `run_calibration.bat` - Windows batch script to easily run calibration
- `calibration_requirements.txt` - Python dependencies

### Android Integration
- `CalibrationImporter.kt` - Android service to import Python calibration data
- Updated `CalibrationActivity.kt` - Added import functionality

## 🚀 **Quick Start**

### Method 1: Using the Batch Script (Easiest)

1. **Connect your glove** to your computer via USB/Serial
2. **Run the batch script**:
   ```cmd
   run_calibration.bat
   ```
3. **Follow the prompts**:
   - Enter serial port (e.g., COM5)
   - Enter username
4. **Follow calibration instructions**:
   - Rest position (relaxed hand)
   - Flex position (tight fist)
5. **Copy the generated JSON file** to your Android device

### Method 2: Using Python Script Directly

1. **Install dependencies**:
   ```cmd
   pip install pyserial numpy
   ```

2. **Run calibration**:
   ```cmd
   python calibration_capture.py --port COM5 --username john
   ```

3. **Test calibration** (optional):
   ```cmd
   python calibration_capture.py --port COM5 --test calibration_john.json
   ```

## 📱 **Android App Integration**

### Importing Calibration Data

1. **Copy JSON file** to Android device:
   - Use ADB: `adb push calibration_john.json /sdcard/`
   - Or copy via USB file transfer

2. **In the Android app**:
   - Go to Calibration screen
   - Tap "Import from Python Script"
   - Select your calibration file
   - Calibration is automatically applied to the user

### File Locations

**Python Script Output:**
- `calibration_username.json` - Generated calibration file

**Android App Storage:**
- `/data/data/com.example.seniorproject/files/calibrations/` - Imported calibration files

## 🔄 **Workflow Options**

### Option A: Python-First Workflow
1. **Run Python calibration** → Generate JSON file
2. **Copy to Android** → Import into app
3. **Use app** → Personalized gesture recognition

### Option B: Android-First Workflow
1. **Use Android app** → Built-in calibration
2. **Direct integration** → No file copying needed

### Option C: Hybrid Workflow
1. **Use Python for data collection** → More control over data collection
2. **Import into Android** → Use app's user management
3. **Best of both worlds** → Python flexibility + Android convenience

## 📊 **Data Format**

### Python Script Output (JSON)
```json
{
  "username": "john",
  "isCalibrated": true,
  "calibrationData": {
    "sensorBaselines": [150.2, 145.8, 160.3, 155.1, 148.9],
    "sensorMaximums": [890.5, 885.3, 910.2, 895.7, 880.4],
    "calibrationTimestamp": 1698765432000
  }
}
```

### Android Integration
- Automatically converts to `CalibrationData` object
- Stores in user profile via `UserManager`
- Applies normalization during gesture recognition

## 🛠️ **Customization**

### Modifying the Python Script

If you want to customize the calibration process:

1. **Edit `calibration_capture.py`**:
   - Change `num_sensors` if you have more/fewer sensors
   - Modify `collect_calibration_data()` duration or sample rate
   - Add additional calibration steps

2. **Update data parsing**:
   - Modify the sensor data parsing in `collect_calibration_data()`
   - Adjust the flex sensor extraction logic

### Modifying the Android Integration

1. **Edit `CalibrationImporter.kt`**:
   - Add validation for additional sensor types
   - Modify file import logic
   - Add support for different JSON formats

2. **Update `CalibrationActivity.kt`**:
   - Add more import options
   - Customize the import dialog
   - Add file management features

## 🔍 **Troubleshooting**

### Python Script Issues

**"No serial ports found"**
- Check USB connection
- Install correct drivers
- Try different port (COM3, COM4, etc.)

**"Failed to connect"**
- Check baud rate (default: 115200)
- Verify port is not in use by another program
- Try different timeout values

**"No sensor data collected"**
- Check ESP32 is sending data in correct format
- Verify data format matches expected: `t_s,flex1,flex2,flex3,flex4,flex5,...`
- Check serial communication

### Android Import Issues

**"No calibration files found"**
- Copy JSON file to correct location
- Check file permissions
- Verify JSON format is correct

**"Failed to import calibration"**
- Check JSON structure matches expected format
- Verify sensor arrays have correct length
- Check for non-finite values in calibration data

## 📈 **Advanced Features**

### Batch Calibration
```cmd
# Calibrate multiple users
python calibration_capture.py --port COM5 --username user1
python calibration_capture.py --port COM5 --username user2
python calibration_capture.py --port COM5 --username user3
```

### Calibration Validation
```cmd
# Test calibration quality
python calibration_capture.py --port COM5 --test calibration_john.json
```

### Data Export
The Python script can be modified to export data in different formats:
- CSV for analysis
- Binary format for faster loading
- Multiple user profiles in one file

## 🔧 **Technical Details**

### Serial Communication
- **Baud Rate**: 115200 (configurable)
- **Data Format**: `t_s,flex1,flex2,flex3,flex4,flex5,roll_deg,pitch_deg,ax_g,ay_g,az_g`
- **Commands**: `start`, `stop`, `A`, `label:B`, etc.

### Data Processing
- **Sampling**: 10 Hz during calibration
- **Duration**: 3 seconds per position
- **Normalization**: Maps raw values to [0, 1] range
- **Validation**: Checks for finite values and logical ranges

### Android Integration
- **File Format**: JSON with specific structure
- **Storage**: Internal app storage
- **Validation**: Comprehensive data validation
- **Error Handling**: Graceful error handling and user feedback

## 🎯 **Best Practices**

### For Data Collection
1. **Consistent hand position** during calibration
2. **Multiple calibration sessions** for different users
3. **Regular recalibration** if glove fit changes
4. **Test calibration** before using in production

### For Android Integration
1. **Validate data** before importing
2. **Backup calibration files** regularly
3. **Test imported calibrations** with real gestures
4. **Monitor calibration quality** over time

## 📞 **Support**

If you encounter issues:

1. **Check logs** in Android Studio for detailed error messages
2. **Verify data format** matches expected structure
3. **Test with simple calibration** first
4. **Check serial communication** independently

## 🔄 **Future Enhancements**

Potential improvements:
- **Real-time calibration** via Bluetooth
- **Cloud calibration sync** across devices
- **Advanced calibration algorithms** (multiple positions, dynamic ranges)
- **Calibration quality metrics** and recommendations
- **Automated calibration** based on usage patterns

---

**Version**: 1.0  
**Last Updated**: October 2025  
**Compatibility**: Python 3.7+, Android API 24+
