# BLE Command Writing - Implementation Needed

## Current Status

**Problem:** The app can detect and connect to the BLE glove, but commands sent from `CalibrationActivity` are **not actually being transmitted** to the glove.

**Why:** The app currently stores BLE device info in SharedPreferences but doesn't implement the actual BLE GATT write operation to send commands.

## What Needs to Be Implemented

### 1. BLE GATT Connection Service

You need to create a service (or add to MainActivity) that:

1. **Connects to BLE device** using `BluetoothGatt`
2. **Discovers services** to find the Nordic UART Service (NUS)
3. **Gets RX characteristic** for writing commands
4. **Stores these in memory** for use by the app

### 2. Command Writing Method

Add a method to write commands to the RX characteristic:

```kotlin
private fun writeCommandToGlove(command: String) {
    val rxCharacteristic = // Get from GATT
    val bytes = command.toByteArray()
    rxCharacteristic.value = bytes
    rxCharacteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
    gatt?.writeCharacteristic(rxCharacteristic)
}
```

### 3. UUIDs Needed

From `ASL_BLE.ino`:

- **Service UUID**: `6E400001-B5A3-F393-E0A9-E50E24DCCA9E` (NUS)
- **RX Characteristic**: `6E400002-B5A3-F393-E0A9-E50E24DCCA9E` (app writes commands here)
- **TX Characteristic**: `6E400003-B5A3-F393-E0A9-E50E24DCCA9E` (glove sends data here)

## Temporary Workaround (For Testing)

Since BLE implementation is complex, you can:

1. **Use Serial Monitor** on ESP32 to send `cal` command directly
2. **Use a BLE terminal app** like "BLE Terminal" to send commands
3. **Test with Serial connection** first before implementing BLE

## Next Steps

1. Create `BluetoothGattHelper` class
2. Implement GATT connection logic
3. Add characteristic discovery
4. Implement write method in `CalibrationActivity`
5. Test with actual glove

## Why It's Not Working Now

Looking at `CalibrationActivity.kt` line ~293:

```kotlin
// TODO: Actually send the command via BLE RX characteristic
// For now, just log it and handle known commands
handleCommand(command)
```

The code shows a `TODO` comment - the BLE write is **not yet implemented**. It's just logging the command, not actually sending it to the glove.
