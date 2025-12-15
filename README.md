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
  <img src="docs/images/app_screenshot_1.png" width="260" />
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
- BOM: [add link/file]
- Wiring diagram: [add link/file]
- Build notes: [add link/file]

---

## Software components

### Android app (Kotlin)
- Location: [`android/`](android/)
- What it does:
  - Bluetooth connection + streaming
  - User/profile management (if applicable)
  - Prediction UI + optional text-to-speech
  - Loads ML assets from `android/app/src/main/assets/`

**Run it**
1. Open **`android/`** in Android Studio (File → Open → `android`)
2. Add model assets to: `android/app/src/main/assets/`
3. Plug in phone → Run

> Detailed Bluetooth steps: see `docs/BLUETOOTH_SETUP_GUIDE.md`

### ESP32 firmware
- Location: [`firmware/esp32/`](firmware/esp32/)
- What it does:
  - Reads sensor values
  - Packages sensor frames
  - Streams over Bluetooth at a steady rate

**Run it**
1. Open the main `.ino` in Arduino IDE
2. Select ESP32 board + COM port
3. Upload

### Calibration tools (Python)
- Location: [`tools/`](tools/)
- Purpose:
  - Capture/replay calibration data
  - Generate calibration outputs for the app
  - Assist with dataset collection

---

## ML model (add details)
- Model type: [LSTM / XGBoost / fusion — fill in]
- Input window: [fill in]
- Output classes: [fill in]
- Where model files go:
  - `android/app/src/main/assets/` (not committed if large)

**Model download (recommended)**
- Put model files in GitHub Releases and document the steps here.

---

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

## Roadmap (add later)
- [ ] Improve dataset collection flow
- [ ] Expand gesture vocabulary
- [ ] Reduce latency + improve stability filtering
- [ ] Enclosure/strain relief + durability improvements

---

## Credits
Team members / advisors / course: [add]
