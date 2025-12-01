# Quick Start Checklist - Fusion Model Integration

Use this checklist to complete your Model Three integration:

## ✅ Checklist

### 1. Model Files (MOST IMPORTANT)
- [ ] Download `LSTM_model.tflite` from Google Drive
  - Path: `MyDrive/Project 9/Models/LSTM_model.tflite`
- [ ] Download `xgb_model.json` from Google Drive  
  - Path: `MyDrive/Project 9/Models/xgb_model.json`
- [ ] Copy both files to: `app/src/main/assets/`
- [ ] Verify files are in assets folder (not subdirectories)

### 2. Build & Sync
- [ ] Open project in Android Studio
- [ ] Sync Gradle (File → Sync Project with Gradle Files)
- [ ] Wait for Gradle to download Gson dependency
- [ ] Build project (Build → Make Project)
- [ ] Check for any build errors

### 3. Test the App
- [ ] Connect Android device or start emulator
- [ ] Run the app
- [ ] Look for toast: "Fusion model loaded successfully"
- [ ] Check Logcat for inference logs: `adb logcat | grep FusionDemo`
- [ ] Test predictions appear in the input text field

### 4. (Optional) Configure Settings
- [ ] Adjust confidence threshold in `MainActivity.kt` (default: 0.5)
- [ ] Adjust stability threshold (default: 3 consecutive predictions)
- [ ] Change sampling rate if needed (default: 50ms = 20 Hz)

### 5. (Future) Connect Real Hardware
- [ ] Implement `BluetoothSensorSource` class
- [ ] Replace `FusionDemoSensorSource` with Bluetooth source
- [ ] Ensure Bluetooth sends 10 features per sample
- [ ] Test with real glove data

---

## Quick File Locations

### Model Files (YOU MUST COPY THESE)
```
Source (Google Drive):
  /MyDrive/Project 9/Models/LSTM_model.tflite
  /MyDrive/Project 9/Models/xgb_model.json

Destination (Android):
  C:\Users\Davan\StudioProjects\AI-Powered-Glove-for-ASL-Translation\
    app\src\main\assets\LSTM_model.tflite
    app\src\main\assets\xgb_model.json
```

### Code Files (ALREADY CREATED)
```
✓ app/build.gradle.kts (modified)
✓ app/src/main/java/com/example/seniorproject/MainActivity.kt (modified)
✓ app/src/main/java/com/example/seniorproject/data/SensorSource.kt (modified)
✓ app/src/main/java/com/example/seniorproject/ml/FusionASLClassifier.kt (new)
✓ app/src/main/java/com/example/seniorproject/ml/XGBoostPredictor.kt (new)
✓ app/src/main/java/com/example/seniorproject/data/SequenceBuffer.kt (new)
✓ app/src/main/java/com/example/seniorproject/data/FusionDemoSensorSource.kt (new)
```

---

## Troubleshooting Quick Fixes

### ❌ "Failed to load fusion model"
→ Model files missing from assets folder. Copy them from Google Drive.

### ❌ "Unexpected output shape"
→ Wrong LSTM model file. Re-export from Colab Cell 35.

### ❌ "Failed to load XGBoost model"  
→ Malformed JSON or wrong file. Re-export from Colab Cell 36.

### ❌ No predictions appearing
→ Lower confidence threshold from 0.5 to 0.3 in `startFusionStream()`

### ❌ Gradle sync failed
→ Check internet connection. Gradle needs to download Gson library.

---

## Testing Commands

```bash
# View all logs
adb logcat

# Filter for fusion model logs
adb logcat | grep -i "Fusion"

# Filter for prediction logs
adb logcat | grep "FusionDemo"

# Clear logs and start fresh
adb logcat -c
```

---

## Expected Log Output

When working correctly, you should see logs like:

```
D/FusionASLClassifier: Fusion model loaded: 5 classes, A, B, C, D, neutral
D/FusionDemo: Buffer: 75/75, Prediction: Prediction(label='A', prob=0.876, ...)
D/FusionDemo: Buffer: 75/75, Prediction: Prediction(label='A', prob=0.891, ...)
D/FusionDemo: Buffer: 75/75, Prediction: Prediction(label='A', prob=0.883, ...)
```

---

## Key Features Implemented

✅ **LSTM + XGBoost Fusion Model**
- Bidirectional LSTM for sequence learning
- XGBoost for final classification with residuals
- 75 timesteps × 10 features input

✅ **Sliding Window Buffer**
- Collects sensor data in real-time
- Maintains recent 75 timesteps
- Handles padding/truncation automatically

✅ **Stability Filtering**
- Requires 3 consecutive same predictions
- Confidence threshold: 50%
- Prevents spurious predictions

✅ **Demo Mode**
- Works out-of-box with synthetic data
- Can load real sensor data from CSV
- Easy to switch to Bluetooth later

---

## Need More Help?

See the full guide: `FUSION_MODEL_INTEGRATION_GUIDE.md`

---

**Remember:** The most important step is copying the two model files from Google Drive to the assets folder! 📁


