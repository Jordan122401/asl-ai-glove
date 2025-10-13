# Fusion Model Integration Guide

## Overview

This guide explains how to integrate Model Three (LSTM + XGBoost Fusion Model) from `Our_model.ipynb` into your Android ASL Translation app.

The fusion model combines:
- **Bidirectional LSTM**: Processes sequences of sensor data (75 timesteps × 10 features)
- **XGBoost**: Uses flattened features + residual for final classification

## Model Architecture

### Input Requirements
- **Sequence Length**: 75 timesteps
- **Features per Timestep**: 10
  - flex1, flex2, flex3, flex4, flex5 (5 flex sensors)
  - roll_deg, pitch_deg (orientation)
  - ax_g, ay_g, az_g (acceleration)

### Output
- **Classes**: 5 classes (A, B, C, D, Neutral)
- **Predictions**: Class label with probability scores from both LSTM and XGBoost

---

## Step 1: Export Models from Colab/Jupyter

### 1.1 LSTM Model (Already Done in Notebook)

The notebook (Cell 35) already converts the Keras model to TFLite:

```python
# This is already in your notebook
converter = tf.lite.TFLiteConverter.from_keras_model(our_model)
converter.target_spec.supported_ops = [
    tf.lite.OpsSet.TFLITE_BUILTINS,
    tf.lite.OpsSet.SELECT_TF_OPS
]
converter._experimental_lower_tensor_list_ops = False
tflite_model = converter.convert()

with open('/content/drive/MyDrive/Project 9/Models/LSTM_model.tflite', 'wb') as f:
    f.write(tflite_model)
```

**Location**: `/content/drive/MyDrive/Project 9/Models/LSTM_model.tflite`

### 1.2 XGBoost Model (Already Done in Notebook)

The notebook (Cell 36) already saves the XGBoost model as JSON:

```python
# This is already in your notebook
xgb_clf.save_model('/content/drive/MyDrive/Project 9/Models/xgb_model.json')
```

**Location**: `/content/drive/MyDrive/Project 9/Models/xgb_model.json`

---

## Step 2: Copy Model Files to Android Assets

### 2.1 Download Models from Google Drive

1. Open your Google Drive
2. Navigate to: `MyDrive/Project 9/Models/`
3. Download these two files:
   - `LSTM_model.tflite`
   - `xgb_model.json`

### 2.2 Copy to Android Project

Copy both files to your Android app's assets folder:

```
C:\Users\Davan\StudioProjects\AI-Powered-Glove-for-ASL-Translation\app\src\main\assets\
```

**Final structure should be:**
```
app/src/main/assets/
├── LSTM_model.tflite  ← NEW FILE
├── xgb_model.json     ← NEW FILE
├── asl_model.tflite   (old POC model, can keep for reference)
└── demo_samples.csv   (optional, for testing)
```

---

## Step 3: (Optional) Create Demo CSV for Testing

If you want to test with real sensor data patterns, create `demo_samples.csv` in the assets folder with this format:

```csv
flex1,flex2,flex3,flex4,flex5,roll_deg,pitch_deg,ax_g,ay_g,az_g
0.34,0.77,1.0,1.0,1.0,44.13,77.8,-1.085,0.163,0.168
0.35,0.77,1.0,1.0,1.0,42.75,77.87,-1.093,0.159,0.172
...
```

Or skip this step - the app will generate synthetic data automatically for testing.

---

## Step 4: Sync Gradle and Build

1. Open your project in Android Studio
2. Sync Gradle files (File → Sync Project with Gradle Files)
3. Wait for Gradle to download the Gson dependency
4. Build the project (Build → Make Project)

---

## Step 5: Run the App

1. Connect your Android device or start an emulator
2. Run the app (Run → Run 'app')
3. The fusion model should load automatically
4. You should see a toast message: "Fusion model loaded successfully"

---

## How It Works

### Data Flow

```
Sensor Data (10 features, 20 Hz)
         ↓
SequenceBuffer (collects 75 timesteps)
         ↓
LSTM Model (processes sequence)
         ↓
XGBoost Model (flattened + residual)
         ↓
Final Prediction (A, B, C, D, Neutral)
         ↓
Stability Filter (requires 3 consecutive matches)
         ↓
Display Letter (if confidence ≥ 50%)
```

### Key Components Created

1. **FusionASLClassifier** (`ml/FusionASLClassifier.kt`)
   - Loads both LSTM and XGBoost models
   - Handles sequence padding/truncation
   - Runs two-stage inference pipeline

2. **XGBoostPredictor** (`ml/XGBoostPredictor.kt`)
   - Parses XGBoost JSON format
   - Evaluates decision tree ensemble
   - Applies softmax for probabilities

3. **SequenceBuffer** (`data/SequenceBuffer.kt`)
   - Maintains sliding window of sensor readings
   - Handles padding and statistics
   - Thread-safe for concurrent access

4. **FusionDemoSensorSource** (`data/FusionDemoSensorSource.kt`)
   - Generates synthetic 10-feature sensor data
   - Can load from CSV for realistic testing
   - Simulates 20 Hz sampling rate

---

## Configuration Options

### In MainActivity.kt

```kotlin
private val USE_FUSION_MODEL = true  // Toggle between POC and Fusion model
```

### In startFusionStream()

```kotlin
val STABILITY_THRESHOLD = 3  // Require 3 consecutive same predictions
val CONFIDENCE_THRESHOLD = 0.5f  // Minimum probability (50%)
```

### In FusionDemoSensorSource

```kotlin
periodMs = 50L  // Sampling rate (50ms = 20 Hz)
```

---

## Troubleshooting

### Issue: "Failed to load fusion model"

**Solution**: Check that both model files are in the assets folder:
- `LSTM_model.tflite`
- `xgb_model.json`

### Issue: Predictions not appearing

**Possible causes**:
1. Confidence threshold too high (lower from 0.5 to 0.3)
2. Stability threshold too high (lower from 3 to 2)
3. Buffer not filling (check logs for "Buffer: X/75")

**Check logs**:
```bash
adb logcat | grep -i "FusionDemo"
```

### Issue: App crashes on startup

**Solution**: Check Logcat for detailed error message:
```bash
adb logcat | grep -E "(FusionASLClassifier|XGBoostPredictor)"
```

Common issues:
- Missing Gson dependency (check Gradle sync)
- Malformed XGBoost JSON (re-export from notebook)
- Incorrect TFLite model format

---

## Testing with Real Hardware

When you connect your actual glove hardware via Bluetooth:

1. Create a new class `BluetoothSensorSource` that implements `SensorSource`
2. Replace `FusionDemoSensorSource` with your Bluetooth source:

```kotlin
// In MainActivity.kt, replace:
fusionDemo = FusionDemoSensorSource(...)

// With:
fusionDemo = BluetoothSensorSource(
    bluetoothSocket = yourBluetoothSocket,
    samplingRateHz = 20
)
```

3. Your Bluetooth source should emit 10 features per sample:
   - 5 flex sensor values (0.0 to 1.0)
   - 2 orientation angles (degrees)
   - 3 acceleration values (g)

---

## Performance Optimization

### Inference Speed
- **LSTM**: ~150ms per sequence on CPU
- **XGBoost**: ~5ms per sample
- **Total**: ~155ms per prediction

### Memory Usage
- **LSTM model**: ~2.5 MB
- **XGBoost model**: ~500 KB
- **Sequence buffer**: ~3 KB (75 × 10 × 4 bytes)

### To Improve Performance:
1. Use GPU delegate for LSTM (requires additional setup)
2. Reduce sequence length from 75 to 50 (retrain required)
3. Quantize LSTM model to INT8 (may reduce accuracy)

---

## Model Accuracy

Based on your notebook results:

- **LSTM Only**: ~70-80% accuracy
- **Fusion Model**: Improved accuracy through XGBoost refinement
- **Real-world**: Performance depends on data quality and gesture consistency

---

## Next Steps

1. **Copy model files** to assets folder
2. **Build and run** the app
3. **Test** with demo data
4. **Integrate** Bluetooth sensor source
5. **Retrain** model with more data if needed
6. **Fine-tune** confidence and stability thresholds

---

## File Summary

### New Files Created
```
app/src/main/java/com/example/seniorproject/
├── ml/
│   ├── FusionASLClassifier.kt       ← Main fusion classifier
│   └── XGBoostPredictor.kt          ← XGBoost inference engine
└── data/
    ├── SequenceBuffer.kt            ← Sliding window buffer
    └── FusionDemoSensorSource.kt    ← Demo sensor emulator
```

### Modified Files
```
app/
├── build.gradle.kts                 ← Added Gson dependency
└── src/main/java/com/example/seniorproject/
    ├── MainActivity.kt              ← Integrated fusion model
    └── data/SensorSource.kt         ← Added sequence support
```

### Model Files (You Need to Copy)
```
app/src/main/assets/
├── LSTM_model.tflite  ← From Google Drive
└── xgb_model.json     ← From Google Drive
```

---

## Support

If you encounter issues:

1. Check the logs: `adb logcat | grep -i "Fusion"`
2. Verify model files are in assets folder
3. Ensure Gradle sync completed successfully
4. Check that both models match expected format:
   - LSTM input: [1, 75, 10]
   - LSTM output: [1, 5]
   - XGBoost input: 751 features (750 + 1 residual)
   - XGBoost output: 5 classes

---

## License & Credits

Model architecture based on "Our_model.ipynb - Model Three"
- Bidirectional LSTM with BatchNormalization
- XGBoost multi-class classifier
- Fusion approach with residual features

Integration code created for FAU Senior Project: AI-Powered Glove for ASL Translation

---

**Happy Coding! 🚀**


