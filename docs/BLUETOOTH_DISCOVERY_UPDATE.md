

## Notes

- Discovery typically takes 5-15 seconds
- Devices may appear slowly as they're discovered
- Previously paired devices will show "(Paired)" next to their name
- Discovery automatically stops after ~60 seconds or when connecting to a device

## ASL Glove BLE Details

### From ASL_BLE.ino Firmware

The ASL glove uses:
- **BLE Device Name**: "ASL-ESP32"
- **Service**: Nordic UART Service (NUS)
- **NUS Service UUID**: `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`
- **RX Characteristic**: `6E400002-B5A3-F393-E0A9-E50E24DCCA9E` (for sending commands to glove)
- **TX Characteristic**: `6E400003-B5A3-F393-E0A9-E50E24DCCA9E` (for receiving data from glove)

### Data Format

The glove sends sensor data in CSV format:
```
flex1,flex2,flex3,flex4,flex5,roll_deg,pitch_deg,ax_g,ay_g,az_g
```

Example streaming data:
```
0.7523,0.2341,0.1234,0.1543,0.0832,25.34,12.10,0.0523,-0.0231,1.0123
```

### Commands to Send to Glove

The app can send these commands to configure the glove:
- `start` - Begin data collection trial
- `stream:on` - Start continuous streaming (for real-time ASL recognition)
- `stream:off` - Stop streaming
- `rate:30` - Set sampling rate (default 30 Hz)
- `cal` - Calibrate flex sensors
- `imu_cal` - Calibrate IMU

For complete command list, see ASL_BLE.ino comments.
