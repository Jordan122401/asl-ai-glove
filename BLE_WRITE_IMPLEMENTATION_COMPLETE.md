# BLE Write Implementation - Complete ✅

## Summary

Implemented complete BLE command writing functionality. The app can now send commands to the ASL glove via BLE GATT.

## What Was Added

### 1. **BLEService.kt** (New File)
A complete BLE service class that:
- ✅ Connects to BLE devices via GATT
- ✅ Discovers Nordic UART Service (NUS)
- ✅ Gets RX characteristic for writing commands
- ✅ Gets TX characteristic for receiving data
- ✅ Writes commands to the glove
- ✅ Receives notifications from the glove
- ✅ Manages connection state

**Key Methods:**
- `connect(device)` - Connect to BLE device
- `writeCommand(command)` - Send command to glove
- `disconnect()` - Disconnect from device
- `isConnected()` - Check connection status

### 2. **CalibrationActivity.kt** (Updated)
Integrated BLEService for command sending:

- ✅ Auto-connects to BLE device on startup (if previously connected)
- ✅ Sends commands via `writeCommand()` when user types commands
- ✅ Shows toast notifications for sent commands
- ✅ Displays data received from glove in sensor readings area
- ✅ Handles connection state changes
- ✅ Cleans up BLE connection on destroy

**How It Works:**
1. User selects BLE glove in BluetoothActivity
2. Device info saved to SharedPreferences
3. CalibrationActivity reads device info
4. Auto-connects to BLE device
5. Commands are sent via `bleService.writeCommand()`

## How to Use

### 1. Connect to Glove
1. Open CalibrationActivity
2. Tap "Connect to Glove"
3. Select your ASL-ESP32 device
4. Wait for "BLE Connected!" toast

### 2. Send Commands
1. Type command in text field (e.g., `cal`, `imu_cal`, `detail`)
2. Press Enter or tap "Send"
3. See "Command sent: cal" toast
4. Glove responds with data shown in sensor readings area

### 3. Available Commands
- `cal` - Calibrate flex sensors
- `imu_cal` - Calibrate IMU
- `detail` - View calibration values
- `stream:on` - Start streaming data
- `stream:off` - Stop streaming
- Any other command from ASL_BLE.ino

## Technical Details

### UUIDs Used
- **Service**: `6E400001-B5A3-F393-E0A9-E50E24DCCA9E` (NUS)
- **RX**: `6E400002-B5A3-F393-E0A9-E50E24DCCA9E` (app → glove)
- **TX**: `6E400003-B5A3-F393-E0A9-E50E24DCCA9E` (glove → app)

### Permission Required
- `BLUETOOTH_CONNECT` - For GATT operations
- Already declared in AndroidManifest.xml

### Write Type
- `WRITE_TYPE_NO_RESPONSE` - Faster writes without acknowledgement

### Command Format
- Commands sent with trailing `\n` for proper parsing
- Example: "cal\n" is sent to glove

## Testing

### 1. Check Logs
```bash
adb logcat | grep BLEService
```

Expected output:
```
D/BLEService: Connecting to BLE device: XX:XX:XX:XX:XX:XX
D/BLEService: Connected to GATT server
D/BLEService: Services discovered
D/BLEService: Found NUS service
D/BLEService: Found RX characteristic
D/BLEService: Writing command: cal (4 bytes) - Success: true
D/BLEService: Received: # CAL FLEX: Rest — keep hand relaxed for ~3 s
```

### 2. Test Commands
1. Type `detail` and press Send
2. Should see JSON response in sensor readings area
3. Type `cal` and press Send
4. Should see calibration prompts from glove

### 3. Troubleshooting

**Command not received?**
- Check logs for "Write successful"
- Verify connection status
- Try reconnecting to glove

**No data received?**
- Check if notifications are enabled
- Verify TX characteristic found
- Check glove is streaming

**Connection fails?**
- Verify device address in SharedPreferences
- Check Bluetooth is on
- Try manual reconnection

## Files Modified

1. **app/src/main/java/com/example/seniorproject/BLEService.kt** (NEW)
   - Complete BLE GATT implementation
   
2. **app/src/main/java/com/example/seniorproject/CalibrationActivity.kt** (UPDATED)
   - Added BLE connection logic
   - Integrated command sending
   - Added data reception

3. **app/src/main/AndroidManifest.xml** (UPDATED)
   - Added BLEService entry

## Success Indicators

✅ Commands appear in logcat when sent
✅ Toasts show "Command sent: <cmd>"
✅ Glove responds with calibration prompts
✅ Data appears in sensor readings area
✅ Connection state updates in UI

## Next Steps (Optional Enhancements)

1. **Error Recovery** - Auto-reconnect on disconnect
2. **Command Queue** - Queue commands if not connected
3. **Data Parsing** - Parse JSON responses from `detail` command
4. **Stream Handling** - Display streaming sensor data in real-time

## Status: READY TO USE 🎉

The BLE write implementation is complete and ready for testing with your ASL glove!
