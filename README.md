# AI-Powered Glove for ASL Translation

> ESP32 + sensor glove + Android app that streams hand-sensor data over Bluetooth and runs on-device ML to translate ASL gestures into text.

![Glove hero](docs/images/hero_glove.jpg)

## Quick links (start here)
- 📱 **Android app:** [`android/`](android/)
- 🧠 **Firmware (ESP32):** [`firmware/esp32/`](firmware/esp32/)
- 🧰 **Calibration tools (Python):** [`tools/`](tools/)
- 🧾 **Docs (BLE + setup + calibration):** [`docs/`](docs/)
- 🧩 **Hardware (schematics/PCB):** [`hardware/`](hardware/)

---

## Demo (add later)
- **Video:** [add link]
- **Photos:** (hero image above) + screenshots below

<p align="center">
  <img src="docs/images/signcast_logo.png" width="260" />
  <img src="docs/images/app_screenshot_2.png" width="260" />
</p>

---

## System overview (60 seconds)
**Goal:** Translate ASL gestures using wearable sensors + embedded streaming + mobile ML inference.

**Pipeline:**
1. **Glove sensors** (flex sensors + IMU) measure finger bend + motion
2. **ESP32 firmware** reads sensors and streams frames over Bluetooth
3. **Android app** receives frames, buffers a window, and runs on-device ML
4. App displays the predicted letter/gesture and can optionally do text-to-speech

![Architecture](docs/images/overallsystem.png)
![Architecture](docs/images/flowsystem.png)

---

## Hardware (schematics + PCB + build)
### Schematics
![Schematics](docs/images/schematics.png)

### PCB (if used)
![PCB](docs/images/pcb_glove-1.png)

### Bill of materials / wiring (add)
- BOM:
 ![BOM](hardware/bom/bom.png)
- Wiring diagram: 
![Wiring](docs/images/wiring%20diagram.png)
- Wiring Picture: 
![Wire_Pic](docs/images/wiring.png)

---

## Software components

### Android app (Kotlin)
- Location: [`android/`](android/)
- Purpose: Connects to the glove over Bluetooth, streams sensor data, runs on-device inference, and displays the predicted ASL output.
- What it does:
  - Bluetooth discovery/connection + live streaming
  - Buffers a **75-sample window** and runs the **BiLSTM + XGBoost fusion** model
  - Displays prediction (optional text-to-speech if enabled)
  - Loads model assets from `android/app/src/main/assets/`

**Run it**
1. Open **`android/`** in Android Studio (File → Open → `android`)
2. Place model files in `android/app/src/main/assets/`:
   - `TFLiteCompatible_LSTM.tflite`
   - `TFLiteCompatible_XGB.json`
   - `labels.txt` (optional)
3. Plug in Android phone → **Run**

> Detailed connection steps: [`docs/BLUETOOTH_SETUP_GUIDE.md`](docs/BLUETOOTH_SETUP_GUIDE.md)

---

### ESP32 firmware
- Location: [`firmware/esp32/`](firmware/esp32/)
- Purpose: Reads glove sensors and streams frames over Bluetooth at a steady rate.
- What it does:
  - Reads flex sensors + IMU values
  - Packages frames for the Android app
  - Streams continuously over Bluetooth

**Run it**
1. Open the main `.ino` in Arduino IDE
2. Select your ESP32 board + COM port
3. Upload

---

### Calibration tools (Python)
- Location: [`tools/`](tools/)
- Purpose: Helps collect/replay calibration data and generate calibration outputs used by the Android app.
- Typical use:
  - Capture baseline + max bend values
  - Export calibration results to .csv for consistent readings across sessions/users

## Fusion model (AI + ML)
- Model type: **Hybrid (stacked) BiLSTM + XGBoost**
  - LSTM learns sequence features from sensor windows
  - XGBoost refines the prediction using LSTM residual/error information
- Input window: **75 samples × 10 features**  
  `flex1–flex5, roll_deg, pitch_deg, ax_g, ay_g, az_g`
- Output classes: **28 total**  
  `A–Z + NEUTRAL + BACKSPACE`
- Label encoding: `A→0 ... Z→25, BACKSPACE→26, NEUTRAL→27`
- Where model files go:
  - `android/app/src/main/assets/`
    - `TFLiteCompatible_LSTM.tflite`
    - `TFLiteCompatible_XGB.json`
    - `labels.txt` (optional; one label per line in index order)

## Repo layout (for recruiters)
This repo is organized as a clean monorepo: :contentReference[oaicite:1]{index=1}

- `android/` – Android Studio project (open this folder)
- `firmware/esp32/` – ESP32 Arduino sketch
- `hardware/` – schematics/PCB exports/build files
- `docs/` – BLE + calibration + setup documentation
- `tools/` – Python scripts and helper utilities

---

## Documentation
Start here:
- Bluetooth: `docs/BLUETOOTH_SETUP_GUIDE.md`
- BLE commands: `docs/BLE_COMMAND_GUIDE.md`
- Calibration: `docs/CALIBRATION_SIMPLIFIED.md`

---

## Roadmap
- [ ] Improve dataset collection flow (more users, more sessions, better labeling + balancing)
- [ ] Expand gesture vocabulary and add “confusable letter” training (ex: **M vs N**, S vs A, etc.)
- [ ] **Hardware v2:** add more sensing points (additional flex sensors / fingertip pressure / better IMU placement) to improve accuracy on close hand shapes
- [ ] Reduce latency + improve stability filtering (smoother predictions, fewer flickers)
- [ ] Enclosure + strain relief + durability improvements (wearability + repeatable sensor placement)
- [ ] **VR integration (long-term):** adapt the glove as a VR input device (pose/gesture tracking + SDK integration)

## Credits
- **Team:** Jordan Wray, Madison Peterkin, Raul Chavez, Davaney Pierre, Mark Louis
- **Course:** Engineering Design 2 (Florida Atlantic University)
- **Related work / inspiration:** Cornell University ECE 4760 “Sign Language Translation” glove projects (sensor glove + ML translation concept)
