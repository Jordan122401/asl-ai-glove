# AI-Powered Glove for ASL Translation

An Android + ESP32 system that translates American Sign Language (ASL) gestures into text using on-glove sensors, Bluetooth streaming, and on-device machine learning.

## Quick start
1. **Clone**
   ```bash
   git clone <repo-url>
   cd asl-ai-glove
   ```
2. **Android app**
   - Open the `android/` folder directly in Android Studio (File → Open → `android`).
   - Add the model assets to `android/app/src/main/assets/`:
     - `TFLiteCompatible_LSTM.tflite`
     - `TFLiteCompatible_XGB.json`
   - Connect an Android device and click **Run**.
3. **Firmware**
   - Open `firmware/esp32/ASL_FINAL__(.ino` in Arduino IDE.
   - Select your ESP32 board + COM port, then upload.
4. **Calibration + BLE helpers**
   - From `tools/`, set up a virtual environment and install dependencies:
     ```bash
     cd tools
     python -m venv .venv
     source .venv/bin/activate
     pip install -r calibration_requirements.txt
     ```
   - Capture or replay calibration data with `python calibration_capture.py` or `python python_calibration.py`.
   - Windows helpers (`run_calibration.bat`, `copy_calibration_to_android.bat`) are also in `tools/`.

## Architecture overview
- **Hardware**: Flex sensors + IMU on the glove feed raw values into an ESP32.
- **Firmware (ESP32)**: Streams sensor readings over Bluetooth to the mobile app.
- **Mobile app (Android/Kotlin)**:
  - Buffers sensor frames, runs the LSTM + XGBoost fusion model, and filters predictions for stability.
  - Manages Bluetooth connections, user profiles, and text-to-speech output.
- **Calibration utilities**: Python scripts to collect, replay, and transfer calibration datasets between the glove and the Android app.

## Folder map
- `android/` – Android Studio project (`app/`, Gradle wrapper/config). Open this folder in Android Studio.
- `firmware/esp32/` – ESP32 Arduino sketch for the glove.
- `hardware/` – 3D prints, PCB exports, and hardware documentation.
- `docs/` – BLE guides, calibration notes, quick-start checklists, and images.
- `tools/` – Python calibration scripts, requirements, and Windows helper BAT files.

## Run the Android app
1. Open `android/` in Android Studio to trigger Gradle sync from the correct project root.
2. Ensure the model assets are present in `android/app/src/main/assets/`.
3. Pair the glove via system Bluetooth settings, then launch the app and select the device in the Bluetooth screen.
4. See detailed Bluetooth steps in [`docs/BLUETOOTH_SETUP_GUIDE.md`](docs/BLUETOOTH_SETUP_GUIDE.md) and BLE command references in the `docs/BLE_*.md` files.

## Run the firmware
1. Connect the ESP32 board and open `firmware/esp32/ASL_FINAL__(.ino` in Arduino IDE.
2. Configure the correct board/port, then upload.
3. Refer to [`docs/FIRMWARE_UPDATE_SUMMARY.md`](docs/FIRMWARE_UPDATE_SUMMARY.md) for notes on firmware changes.

## Use the calibration tools
1. Install Python dependencies from `tools/calibration_requirements.txt` (see Quick start above).
2. Capture live glove data with `python calibration_capture.py`.
3. Process or replay calibration datasets with `python python_calibration.py`.
4. Transfer calibration outputs to the Android device using the helper BAT scripts when working on Windows.

## Documentation
Key references live in `docs/`:
- BLE and Bluetooth flows: `docs/BLE_COMMAND_GUIDE.md`, `docs/BLUETOOTH_SETUP_GUIDE.md`, `docs/BLE_CONNECTION_SUMMARY.md`.
- Calibration: `docs/CALIBRATION_SIMPLIFIED.md`, `docs/PYTHON_CALIBRATION_INTEGRATION.md`, `docs/QUICK_START_CHECKLIST.md`.
- User management and release notes: `docs/USER_MANAGEMENT_GUIDE.md`, `docs/CHANGES_SUMMARY.md`, `docs/FIRMWARE_UPDATE_SUMMARY.md`.
