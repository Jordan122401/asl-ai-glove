# AI-Powered-Glove-for-ASL-Translation

Senior Design Project Group 9: Mobile app for ASL translations using real-time sensor data from a Bluetooth glove.

## 📱 Overview

This Android application uses machine learning to translate American Sign Language (ASL) gestures into text in real-time. The app connects to a custom hardware glove equipped with flex sensors and an IMU (Inertial Measurement Unit) to capture hand movements.

## 🎯 Features

- **Real-time ASL Recognition**: Translates hand gestures to letters as you sign
- **Bluetooth Connectivity**: Wirelessly connects to the ASL glove
- **Fusion ML Model**: Combines LSTM and XGBoost for accurate predictions
- **Text-to-Speech**: Speaks recognized letters aloud
- **Confidence Indicators**: Shows prediction confidence percentages
- **Clean UI**: Simple, intuitive interface with connection status

## 🔧 Technology Stack

### Machine Learning
- **LSTM Model**: Bidirectional LSTM for sequence learning (75 timesteps × 10 features)
- **XGBoost Classifier**: Gradient boosting for final classification with residuals
- **TensorFlow Lite**: On-device inference for real-time performance

### Android
- **Language**: Kotlin
- **Min SDK**: API 24 (Android 7.0)
- **Target SDK**: API 34 (Android 14)
- **Architecture**: MVVM with Coroutines

### Connectivity
- **Bluetooth**: SPP (Serial Port Profile) for glove communication
- **Data Format**: CSV over Bluetooth serial

## 🚀 Getting Started

### Prerequisites

1. **Android Studio** (latest version)
2. **Android device** with Bluetooth 4.0+
3. **ASL Glove** with Bluetooth module
4. **Model files** (see setup below)

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/your-repo/AI-Powered-Glove-for-ASL-Translation.git
   cd AI-Powered-Glove-for-ASL-Translation
   ```

2. **Add model files**
   
   Download the following files from Google Drive and place them in `app/src/main/assets/`:
   - `TFLiteCompatible_LSTM.tflite`
   - `TFLiteCompatible_XGB.json`

3. **Open in Android Studio**
   - File → Open → Select project directory
   - Wait for Gradle sync to complete

4. **Build and run**
   - Connect your Android device
   - Click Run (▶️) button
   - Grant necessary permissions

### Model Files Setup

The app requires two ML model files:

```
app/src/main/assets/
  ├── TFLiteCompatible_LSTM.tflite    (LSTM sequence model)
  ├── TFLiteCompatible_XGB.json       (XGBoost classifier)
  └── demo_samples.csv                 (Sample data - optional)
```

**Important**: The app will not work without these model files!

## 📖 Usage

### Connecting to the Glove

1. **Pair your glove** with your Android device via Settings → Bluetooth
2. **Open the app**
3. **Tap "Connect to Glove"**
4. **Grant Bluetooth permissions** when prompted
5. **Scan for devices** in the Bluetooth screen
6. **Select your glove** from the paired devices list
7. **Wait for connection** confirmation

See [BLUETOOTH_SETUP_GUIDE.md](BLUETOOTH_SETUP_GUIDE.md) for detailed instructions.

### Using the App

1. **Make ASL gestures** with your glove
2. **Watch real-time predictions** in the connection status
3. **Letters appear** in the input field when stable and confident
4. **Use "Connect Text"** to combine letters into words
5. **Use "Clear Text"** to start over
6. **Adjust settings** for font size and TTS preferences

## 🏗️ Project Structure

```
app/src/main/
├── java/com/example/seniorproject/
│   ├── MainActivity.kt              # Main screen with inference
│   ├── BluetoothActivity.kt         # Bluetooth pairing/connection
│   ├── SettingsActivity.kt          # App settings
│   ├── data/
│   │   ├── SensorSource.kt          # Interface for sensor data
│   │   ├── BluetoothSensorSource.kt # Bluetooth data receiver
│   │   └── SequenceBuffer.kt        # Sliding window buffer
│   └── ml/
│       ├── FusionASLClassifier.kt   # Main ML classifier
│       ├── XGBoostPredictor.kt      # XGBoost inference
│       └── SimpleXGBoostPredictor.kt # Fallback predictor
├── res/
│   ├── layout/                       # UI layouts
│   ├── values/                       # Strings, colors, themes
│   └── drawable/                     # Images and icons
└── assets/
    └── *.tflite, *.json             # ML model files
```

## 🔬 How It Works

### Data Flow

```
ASL Glove → Bluetooth → BluetoothSensorSource → SequenceBuffer
                                                      ↓
                                              FusionASLClassifier
                                              (LSTM + XGBoost)
                                                      ↓
                                            Prediction + Confidence
                                                      ↓
                                            Stability Filter (3x)
                                                      ↓
                                            Text Field + TTS
```

### Sensor Data Format

The glove sends 10 features per timestep:
```
flex1, flex2, flex3, flex4, flex5, roll, pitch, ax, ay, az
```

- **flex1-5**: Flex sensor values (0.0 - 1.0)
- **roll, pitch**: Hand orientation (degrees)
- **ax, ay, az**: Acceleration (G-force)

### Model Architecture

1. **LSTM Layer**: Processes sequences of 75 timesteps
2. **Dense Layers**: Extracts features from sequence
3. **XGBoost**: Final classification with residual learning
4. **Output**: 5 classes (A, B, C, D, neutral) with confidence scores

### Prediction Logic

- **Buffer Size**: 75 timesteps (3.75 seconds at 20 Hz)
- **Minimum Fill**: 50% (37 timesteps) before prediction
- **Stability**: 3 consecutive identical predictions required
- **Confidence**: 50% minimum to accept prediction
- **Filtering**: "neutral" gestures excluded from text

## 📊 Performance

- **Inference Speed**: ~50-100ms per prediction
- **Accuracy**: 85-95% on trained gestures (varies by user)
- **Latency**: ~2-4 seconds from gesture to text (including stability check)
- **Battery**: ~4-6 hours continuous use

## ⚙️ Configuration

### Adjustable Parameters (in `MainActivity.kt`)

```kotlin
// Stability threshold (consecutive predictions)
val STABILITY_THRESHOLD = 3  // Line ~169

// Confidence threshold (minimum probability)
pred.probability >= 0.5f  // Line ~219

// Buffer fill requirement
fusionClassifier.getSequenceLength() / 2  // Line ~189
```

### Settings Screen

- **Font Size**: Adjust text size (16-32sp)
- **Text-to-Speech**: Enable/disable spoken letters
- **Theme**: Light or Dark mode

## 🛠️ Troubleshooting

### Common Issues

| Issue | Solution |
|-------|----------|
| "Failed to load fusion model" | Add model files to `assets/` folder |
| "Bluetooth not supported" | Use a different Android device |
| "No paired devices found" | Pair glove in Android Settings first |
| "Connection failed" | Check glove is on and in range |
| "No predictions appearing" | Verify data format and connection |

See [BLUETOOTH_SETUP_GUIDE.md](BLUETOOTH_SETUP_GUIDE.md) for detailed troubleshooting.

## 📚 Documentation

- [BLUETOOTH_SETUP_GUIDE.md](BLUETOOTH_SETUP_GUIDE.md) - Complete Bluetooth setup instructions
- [QUICK_START_CHECKLIST.md](QUICK_START_CHECKLIST.md) - Model integration checklist

## 🔐 Permissions

### Required Permissions

- **Bluetooth**: Connect to glove
- **Bluetooth Connect** (Android 12+): Pair and connect
- **Location** (Android 11-): Required for Bluetooth on older versions

### Optional Permissions

- **Microphone**: For TTS (granted automatically)
- **Notifications**: For background connection status (future feature)

## 🧪 Testing

### Without Hardware

The app requires a physical Bluetooth glove. For development without hardware:

1. Use a Bluetooth serial terminal app to simulate data
2. Send CSV data in the correct format
3. Test connection and parsing logic

### With Hardware

1. Power on glove
2. Pair with Android device
3. Connect via app
4. Make test gestures (A, B, C, D)
5. Verify predictions appear

## 🚧 Future Enhancements

- [ ] Support for full alphabet (26 letters)
- [ ] Word-level predictions
- [ ] Gesture recording and training
- [ ] Multi-user profiles
- [ ] Cloud sync for conversation history
- [ ] Bluetooth LE support
- [ ] Background service for persistent connection

## 👥 Contributors

**Senior Design Project Group 9**
- Team members and roles

## 📄 License

This project is part of a senior design course and is for educational purposes.

## 🙏 Acknowledgments

- TensorFlow Lite team for mobile ML framework
- Android Bluetooth documentation
- ASL community for gesture references

---

**For questions or issues, please refer to the documentation or contact the development team.**
