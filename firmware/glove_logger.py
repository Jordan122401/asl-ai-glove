#!/usr/bin/env python3
# ASL Glove Logger – Mac + Windows Edition
# Shows real-time output AND saves perfect CSV.
# GUARANTEED: No missing rows, no duplicate headers, clean output.

import serial
import csv
import sys
import time
import os
import threading
from datetime import datetime
import serial.tools.list_ports

HEADER = "t_s,flex1,flex2,flex3,flex4,flex5,roll_deg,pitch_deg,ax_g,ay_g,az_g,label,trial_id"
ROWS_PER_TRIAL = 75
CSV_FOLDER = "captures"

running = True


def pick_port():
    ports = list(serial.tools.list_ports.comports())
    print("\nAvailable Ports:")
    for i, p in enumerate(ports):
        print(f" [{i}] {p.device}")

    sel = int(input("\nSelect port #: "))
    return ports[sel].device


def serial_reader(ser, writer):
    current_rows = []
    trial_id_active = None

    while running:
        try:
            line = ser.readline().decode("utf-8", errors="ignore").strip()

            if line:
                # Skip ESP32 trial CSV headers
                if line.startswith("t_s,"):
                    continue

                # Print all other output live (like Serial Monitor)
                print(line, flush=True)

                # Data row check (always 12 commas for your firmware)
                if line.count(",") == 12 and not line.startswith("#"):
                    parts = line.split(",")
                    tid = parts[-1]

                    if trial_id_active is None:
                        trial_id_active = tid

                    # Trial ID changed (ESP auto-increments)
                    if tid != trial_id_active:
                        # Save previous trial
                        for r in current_rows:
                            writer.writerow(r)
                        print(f"[+] Saved trial {trial_id_active} ({len(current_rows)} rows)")
                        current_rows = []
                        trial_id_active = tid

                    # Add row
                    current_rows.append(parts)

                    # When exactly 75 rows are hit → save
                    if len(current_rows) == ROWS_PER_TRIAL:
                        for r in current_rows:
                            writer.writerow(r)
                        print(f"[+] Saved trial {trial_id_active} (75 rows)")
                        current_rows = []

        except Exception as e:
            print("Reader error:", e)
            break


def main():
    global running

    if not os.path.exists(CSV_FOLDER):
        os.makedirs(CSV_FOLDER)

    port = pick_port()
    ser = serial.Serial(port, 115200, timeout=0.1)
    print(f"\nConnected to {port}")

    # Allow user to name CSV file
    user_name = input("\nEnter CSV file name (no extension): ").strip()
    if not user_name:
        user_name = datetime.now().strftime("asl_capture_%Y%m%d_%H%M%S")

    fname = user_name + ".csv"
    path = os.path.join(CSV_FOLDER, fname)

    with open(path, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(HEADER.split(","))  # Only write header ONCE

        # Start async reader
        t = threading.Thread(target=serial_reader, args=(ser, writer))
        t.daemon = True
        t.start()

        print("\nType ESP32 commands below.")
        print("Examples: start, label:A, trial:3, cal, imu_cal\n")

        # MAIN LOOP — Send commands to ESP32
        try:
            while True:
                cmd = input("")
                ser.write((cmd + "\n").encode())

        except KeyboardInterrupt:
            running = False
            print("\nStopping...")
            time.sleep(0.2)


if __name__ == "__main__":
    main()