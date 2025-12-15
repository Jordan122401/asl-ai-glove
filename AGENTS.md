# Contributor / Codex Instructions

## Goals
Make the repo easy for employers to understand and easy to run.

## Repo layout
- android/        -> Android Studio project (open this folder)
- firmware/esp32/ -> ESP32 firmware
- hardware/       -> KiCad/PCB and hardware docs
- docs/           -> BLE + calibration + setup guides + images
- tools/          -> Python + helper scripts

## Rules
- Use `git mv` for file moves.
- Do NOT commit IDE/build artifacts (.idea/, .kotlin/, build/, .gradle/, local.properties).
- Keep changes scoped to organization + documentation unless a small fix is required to keep builds working.
- README must include: project summary, architecture, quick start, folder map, how to run.
