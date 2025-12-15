# Firmware Update Summary - ASL_BLE_FINAL

## Overview
Updated project to use `ASL_BLE_FINAL_frfr.ino` firmware which includes improved streaming with automatic windowed batches.

## Key Changes in New Firmware

### 1. Streaming Command Simplified
- **Old:** `stream:on` / `stream:off`
- **New:** `stream` / `stream:off`
- **Update:** MainActivity now uses `stream` command

### 2. Windowed Streaming Mode
The new firmware automatically manages streaming in windowed batches:
- Sends exactly **75 samples per batch**
- Automatically inserts **1-second gap** between batches
- Sends CSV header before each batch: `flex1,flex2,flex3,flex4,flex5,roll_deg,pitch_deg,ax_g,ay_g,az_g`
- Announces batches with: `# BATCH N BEGIN,nsamp=75,rate=30`

### 3. Stream Output Format
- **Old:** Included `t_s` (time) prefix in streaming mode
- **New:** Stream format is just 10 sensor values (no time prefix)
- **Update:** BLESensorSource updated to handle new format (already supports both)

### 4. New Commands Available
- `stream` - Start windowed streaming (75-sample batches with 1s gaps)
- `detail` - Show current calibration values in JSON format
- `whoami` - Display current active user
- `listusers` - Show all registered users

## Code Updates Made

### MainActivity.kt
- ✅ Changed `stream:on` command to `stream`
- ✅ Updated comments to reflect new firmware behavior
- ✅ Note that glove handles windowing automatically

### BLESensorSource.kt
- ✅ Updated documentation to reflect new windowed batch format
- ✅ Already handles both formats (with/without time prefix)
- ✅ Automatically skips header lines

### CalibrationActivity.kt
- ✅ Updated to accept both `stream` and legacy `stream:on` commands

## Benefits of New Firmware

1. **Automatic Batch Management**: No need for app to manually count samples or manage timing
2. **Consistent Batches**: Guaranteed exactly 75 samples per batch
3. **Reliable Timing**: 1-second gaps handled on glove side
4. **Better Debugging**: Batch announcements and headers make it easier to track data flow
5. **Simplified App Logic**: App just collects batches and runs inference

## Compatibility

The app remains compatible with both firmware versions:
- Automatically detects and handles 10-value format (new firmware)
- Can still handle 11-value format with time prefix (legacy support)
- Headers are automatically skipped in both cases

## Testing Recommendations

1. Test streaming with new firmware
2. Verify batches arrive correctly (75 samples each)
3. Confirm 1-second gaps between batches
4. Test inference accuracy with new batch format
5. Verify calibration commands still work (`cal`, `imu_cal`, `detail`)

