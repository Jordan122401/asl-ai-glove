# Troubleshooting Guide

## Error: "Failed to load fusion model: internal error"

### ✅ Solution Applied

I've added the **TensorFlow SELECT_TF_OPS** library which is required for the Bidirectional LSTM model.

### Steps to Fix:

1. **Sync Gradle** (most important!)
   - In Android Studio: File → Sync Project with Gradle Files
   - Wait for sync to complete (watch bottom progress bar)

2. **Clean and Rebuild**
   - Build → Clean Project
   - Build → Rebuild Project

3. **Run the App Again**
   - The model should now load successfully
   - Look for toast: "Fusion model loaded successfully"

---

## How to View Detailed Error Logs

### In Android Studio:

1. Click **Logcat** tab at bottom of screen
2. In filter box, type: `FusionASLClassifier` or `MainActivity`
3. Look for red error lines (E/ prefix)
4. Expand stack traces to see full error

### Common Logcat Filters:

```
# View fusion model logs
FusionASLClassifier

# View XGBoost logs  
XGBoostPredictor

# View all app logs
com.example.seniorproject

# View errors only
package:mine level:error
```

---

## Common Issues & Solutions

### Issue #1: "Failed to load TFLite model" ✅ FIXED

**Cause**: Bidirectional LSTM requires TensorFlow ops support

**Solution**: Switched to standard TensorFlow Lite with SELECT_TF_OPS support:
```kotlin
implementation("org.tensorflow:tensorflow-lite:2.17.0")
implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.17.0")
```

**Status**: ✅ Should be fixed now after Gradle sync

---

### Issue #2: "Failed to load XGBoost model"

**Symptoms**:
- Logcat shows "Failed to load XGBoost model"
- JSON parsing errors

**Solutions**:

A. **Check file exists**:
   ```
   app/src/main/assets/xgb_model.json
   ```

B. **Re-export from Colab**:
   ```python
   xgb_clf.save_model('/content/drive/MyDrive/Project 9/Models/xgb_model.json')
   ```
   Then re-copy to assets folder

C. **Verify JSON format**:
   - Open `xgb_model.json` in text editor
   - Should start with: `{"version":[3,0,5],"learner":{...`
   - File should be ~500KB to ~2MB in size

---

### Issue #3: "Unexpected output shape"

**Symptoms**:
- Error mentions shape mismatch
- Model loads but crashes on inference

**Causes**:
- LSTM model expects different input/output shape
- Model was trained with different parameters

**Solutions**:

A. **Check model shapes in Logcat**: Look for output like:
   ```
   D/FusionASLClassifier: LSTM input shape: [1, 75, 10]
   D/FusionASLClassifier: LSTM output shape: [1, X]
   ```

B. **Verify in notebook**: The model should have:
   - Input: [1, 75, 10]
   - Output: [1, 5] (for 5 classes)

C. **Re-export TFLite model**: Run Cell 35 in `Our_model.ipynb` again

---

### Issue #4: No predictions appearing

**Symptoms**:
- App runs without crashing
- Model loads successfully
- But no letters appear in text field

**Check these**:

1. **Buffer filling**: Logcat should show:
   ```
   D/FusionDemo: Buffer: X/75, Prediction: ...
   ```
   If stuck at low numbers (< 40), buffer might not be filling

2. **Confidence too low**: Check predictions in Logcat:
   ```
   Prediction(label='A', prob=0.345, ...)
   ```
   If probability < 0.5, it won't be added

3. **All predictions are "Neutral"**: Filter excludes neutral gestures

**Solutions**:

A. **Lower confidence threshold** in `MainActivity.kt`:
   ```kotlin
   // Change from:
   if (pred.probability >= 0.5f && pred.label != "Neutral")
   
   // To:
   if (pred.probability >= 0.3f && pred.label != "Neutral")
   ```

B. **Lower stability threshold**:
   ```kotlin
   // Change from:
   val STABILITY_THRESHOLD = 3
   
   // To:
   val STABILITY_THRESHOLD = 2
   ```

C. **Allow neutral predictions** (for testing):
   ```kotlin
   // Remove the neutral filter:
   if (pred.probability >= 0.5f) {  // Removed: && pred.label != "Neutral"
   ```

---

### Issue #5: App crashes on startup

**Symptoms**:
- App immediately closes after launch
- Logcat shows crash before model loads

**Solutions**:

A. **Check model files exist**:
   - `app/src/main/assets/LSTM_model.tflite`
   - `app/src/main/assets/xgb_model.json`

B. **File permissions**: Make sure files aren't read-only

C. **File corruption**: Re-download from Google Drive

D. **Gradle sync failed**: 
   - File → Invalidate Caches / Restart
   - Re-sync Gradle

---

### Issue #6: Gradle sync fails

**Symptoms**:
- "Failed to resolve: litert-select-tf-ops"
- Build errors about missing dependencies

**Solutions**:

A. **Check internet connection**: Gradle needs to download libraries

B. **Clear Gradle cache**:
   - File → Invalidate Caches / Restart
   - Delete `.gradle` folder in project root
   - Sync again

C. **Check version catalog**: Verify `libs.versions.toml` has:
   ```toml
   litert-select-tf-ops = { module = "com.google.ai.edge.litert:litert-select-tf-ops", version.ref = "litert" }
   ```

D. **Manual dependency** (fallback): Replace in `build.gradle.kts`:
   ```kotlin
   implementation(libs.litert.select.tf.ops)
   
   // With:
   implementation("com.google.ai.edge.litert:litert-select-tf-ops:1.4.0")
   ```

---

## Verification Checklist

After fixing issues, verify everything works:

- [ ] Gradle sync completes without errors
- [ ] Project builds successfully
- [ ] App launches on device/emulator
- [ ] Toast shows: "Fusion model loaded successfully"
- [ ] Logcat shows: `D/FusionASLClassifier: Fusion model loaded: 5 classes`
- [ ] Logcat shows buffer filling: `D/FusionDemo: Buffer: X/75`
- [ ] Logcat shows predictions: `Prediction(label='A', prob=0.876, ...)`
- [ ] Letters appear in input field after predictions

---

## Getting More Help

### View Complete Logs:

```bash
# If ADB is available:
adb logcat | findstr /i "Fusion"

# Or in PowerShell:
adb logcat | Select-String -Pattern "Fusion"
```

### In Android Studio:

1. Run app
2. Logcat → Click "Wrap text" icon (top right)
3. Screenshot any errors
4. Look for red lines starting with `E/`

---

## Model File Checklist

Verify these files exist and have reasonable sizes:

| File | Location | Expected Size | Status |
|------|----------|---------------|--------|
| LSTM_model.tflite | app/src/main/assets/ | 2-3 MB | ✅ Present |
| xgb_model.json | app/src/main/assets/ | 0.5-2 MB | ✅ Present |
| demo_samples.csv | app/src/main/assets/ | < 1 KB | ⭕ Optional |

---

## Next Steps

After fixing the error:

1. ✅ Sync Gradle (File → Sync Project with Gradle Files)
2. ✅ Clean & Rebuild (Build → Clean Project, then Build → Rebuild)
3. ✅ Run app on device/emulator
4. ✅ Check for success toast: "Fusion model loaded successfully"
5. ✅ Monitor Logcat for predictions
6. 🎯 Test with real sensor data when hardware is ready

---

## Model Performance Tuning

If model works but predictions are poor:

### Option 1: Adjust Thresholds
```kotlin
// In MainActivity.kt - startFusionStream()

val STABILITY_THRESHOLD = 2  // Lower = faster but less stable
pred.probability >= 0.4f      // Lower = more predictions
```

### Option 2: Retrain with More Data
- Collect more gesture samples
- Run training notebook again
- Export new models
- Replace in assets folder

### Option 3: Change Sampling Rate
```kotlin
// In FusionDemoSensorSource
periodMs = 50L  // Default: 20 Hz

// Try:
periodMs = 100L // 10 Hz (slower, less CPU usage)
periodMs = 33L  // 30 Hz (faster, more data)
```

---

## Emergency Fallback

If fusion model still won't work, temporarily switch to demo mode:

```kotlin
// In MainActivity.kt
private val USE_FUSION_MODEL = false  // Disable fusion model
```

Then implement simple gesture detection to test your hardware while debugging the fusion model separately.

---

**Last Updated**: After adding SELECT_TF_OPS support
**Status**: Issue should be resolved after Gradle sync ✅

