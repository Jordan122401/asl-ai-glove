#!/usr/bin/env python3
"""
Calibration Capture Script for ASL Glove
Modified from trial_capture_logger.py to collect calibration data
and save it in Android-compatible JSON format.
"""

import argparse
import re
import sys
import time
import threading
import json
import numpy as np
from pathlib import Path
from typing import List, Dict, Optional

try:
    import serial
    from serial.tools import list_ports
except ImportError:
    print("Please install pyserial: pip install pyserial")
    sys.exit(1)

class GloveCalibrationCapture:
    def __init__(self, port: str = None, baudrate: int = 115200):
        self.port = port
        self.baudrate = baudrate
        self.serial_connection = None
        self.num_sensors = 5  # flex1-flex5
        self.calibration_data = []
        self.is_collecting = False
        
    def connect(self) -> bool:
        """Connect to the glove via serial."""
        try:
            if self.port is None:
                self.port = self._auto_detect_port()
                if self.port is None:
                    print("❌ Could not auto-detect serial port")
                    return False
            
            self.serial_connection = serial.Serial(
                port=self.port,
                baudrate=self.baudrate,
                timeout=0.5
            )
            time.sleep(2)  # Wait for connection to stabilize
            print(f"✅ Connected to glove on {self.port}")
            return True
        except Exception as e:
            print(f"❌ Failed to connect: {e}")
            return False
    
    def _auto_detect_port(self) -> Optional[str]:
        """Auto-detect the serial port."""
        ports = list_ports.comports()
        for port in ports:
            name = (port.device or "").lower()
            desc = (port.description or "").lower()
            if any(k in name for k in ["com", "ttyusb", "ttyacm"]) or "usb" in desc:
                return port.device
        return ports[0].device if ports else None
    
    def disconnect(self):
        """Disconnect from the glove."""
        if self.serial_connection and self.serial_connection.is_open:
            self.serial_connection.close()
            print("✅ Disconnected from glove")
    
    def send_command(self, command: str):
        """Send a command to the ESP32."""
        if self.serial_connection and self.serial_connection.is_open:
            try:
                cmd = command + "\n"
                self.serial_connection.write(cmd.encode("utf-8"))
                self.serial_connection.flush()
                print(f"📤 Sent: {command}")
            except Exception as e:
                print(f"❌ Failed to send command: {e}")
    
    def start_data_collection(self):
        """Start data collection by sending 'start' command."""
        self.calibration_data = []
        self.is_collecting = True
        self.send_command("start")
        print("📊 Started data collection...")
    
    def stop_data_collection(self):
        """Stop data collection by sending 'stop' command."""
        self.is_collecting = False
        self.send_command("stop")
        print("🛑 Stopped data collection")
    
    def collect_calibration_data(self, duration: float = 3.0) -> List[List[float]]:
        """
        Collect sensor data for calibration.
        
        Args:
            duration: Duration to collect data (seconds)
            
        Returns:
            List of sensor readings (each reading is a list of 5 flex values)
        """
        print(f"📊 Collecting data for {duration} seconds...")
        
        self.start_data_collection()
        start_time = time.time()
        
        while time.time() - start_time < duration:
            try:
                raw = self.serial_connection.readline()
                if raw:
                    line = raw.decode("utf-8", errors="replace").strip()
                    
                    # Skip comments and empty lines
                    if line.startswith("#") or not line:
                        continue
                    
                    # Parse sensor data (expecting: t_s,flex1,flex2,flex3,flex4,flex5,...)
                    try:
                        values = [float(x) for x in line.split(',')]
                        if len(values) >= 5:  # At least flex1-flex5
                            flex_values = values[1:6]  # Extract flex1-flex5
                            self.calibration_data.append(flex_values)
                            print(f"  Sample {len(self.calibration_data)}: {[f'{x:.2f}' for x in flex_values]}")
                    except ValueError:
                        continue  # Skip invalid lines
                        
            except Exception as e:
                print(f"⚠️ Error reading data: {e}")
                break
        
        self.stop_data_collection()
        
        if not self.calibration_data:
            raise Exception("No sensor data collected!")
        
        print(f"✅ Collected {len(self.calibration_data)} samples")
        return self.calibration_data
    
    def calculate_calibration_values(self, data: List[List[float]]) -> Dict[str, List[float]]:
        """Calculate baseline and maximum values from collected data."""
        data_array = np.array(data)
        
        # Calculate statistics
        baseline_values = np.mean(data_array, axis=0).tolist()
        maximum_values = np.max(data_array, axis=0).tolist()
        minimum_values = np.min(data_array, axis=0).tolist()
        
        print(f"📈 Baseline (mean): {[f'{x:.2f}' for x in baseline_values]}")
        print(f"📈 Maximum: {[f'{x:.2f}' for x in maximum_values]}")
        print(f"📈 Minimum: {[f'{x:.2f}' for x in minimum_values]}")
        
        return {
            "sensorBaselines": baseline_values,
            "sensorMaximums": maximum_values,
            "sensorMinimums": minimum_values,
            "calibrationTimestamp": int(time.time() * 1000)
        }
    
    def calibrate_rest_position(self) -> List[List[float]]:
        """Calibrate the rest position (baseline values)."""
        print("\n🖐️  REST POSITION CALIBRATION")
        print("Please relax your hand completely (fingers straight)")
        print("Press Enter when ready...")
        input()
        
        return self.collect_calibration_data(duration=3.0)
    
    def calibrate_flex_position(self) -> List[List[float]]:
        """Calibrate the flex position (maximum values)."""
        print("\n✊ FLEX POSITION CALIBRATION")
        print("Please make a tight fist (all fingers fully flexed)")
        print("Press Enter when ready...")
        input()
        
        return self.collect_calibration_data(duration=3.0)
    
    def run_full_calibration(self, username: str) -> Dict:
        """
        Run the complete calibration process.
        
        Args:
            username: Username for the calibration
            
        Returns:
            Calibration data dictionary compatible with Android app
        """
        print(f"\n🎯 Starting calibration for user: {username}")
        print("=" * 50)
        
        # Connect to glove
        if not self.connect():
            raise Exception("Failed to connect to glove")
        
        try:
            # Calibrate rest position
            rest_data = self.calibrate_rest_position()
            rest_calibration = self.calculate_calibration_values(rest_data)
            
            # Calibrate flex position
            flex_data = self.calibrate_flex_position()
            flex_calibration = self.calculate_calibration_values(flex_data)
            
            # Create final calibration data
            calibration_data = {
                "username": username,
                "isCalibrated": True,
                "calibrationData": {
                    "sensorBaselines": rest_calibration["sensorBaselines"],
                    "sensorMaximums": flex_calibration["sensorMaximums"],
                    "calibrationTimestamp": int(time.time() * 1000)
                }
            }
            
            print("\n✅ Calibration completed successfully!")
            print(f"📊 Rest baseline: {[f'{x:.2f}' for x in rest_calibration['sensorBaselines']]}")
            print(f"📊 Flex maximum: {[f'{x:.2f}' for x in flex_calibration['sensorMaximums']]}")
            
            return calibration_data
            
        finally:
            self.disconnect()
    
    def save_calibration(self, calibration_data: Dict, filename: str = None):
        """Save calibration data to a JSON file."""
        if filename is None:
            username = calibration_data["username"]
            filename = f"calibration_{username}.json"
        
        with open(filename, 'w') as f:
            json.dump(calibration_data, f, indent=2)
        
        print(f"💾 Calibration saved to: {filename}")
    
    def test_calibration(self, calibration_data: Dict):
        """Test the calibration by showing normalized values."""
        print("\n🧪 Testing calibration...")
        print("Move your hand and watch the normalized values:")
        print("(Press Ctrl+C to stop)")
        
        baseline = np.array(calibration_data["calibrationData"]["sensorBaselines"])
        maximum = np.array(calibration_data["calibrationData"]["sensorMaximums"])
        
        try:
            while True:
                try:
                    raw = self.serial_connection.readline()
                    if raw:
                        line = raw.decode("utf-8", errors="replace").strip()
                        
                        if line.startswith("#") or not line:
                            continue
                        
                        try:
                            values = [float(x) for x in line.split(',')]
                            if len(values) >= 5:
                                flex_values = np.array(values[1:6])
                                
                                # Normalize the data
                                normalized = (flex_values - baseline) / (maximum - baseline)
                                normalized = np.clip(normalized, 0, 1)
                                
                                print(f"Raw: {[f'{x:.2f}' for x in flex_values]} | "
                                      f"Normalized: {[f'{x:.2f}' for x in normalized]}")
                        except ValueError:
                            continue
                except Exception as e:
                    print(f"⚠️ Error: {e}")
                    break
                
                time.sleep(0.1)
        except KeyboardInterrupt:
            print("\n🛑 Test stopped")

def main():
    parser = argparse.ArgumentParser(description="Glove Calibration Capture Tool")
    parser.add_argument("--port", "-p", help="Serial port (e.g., COM5)")
    parser.add_argument("--baudrate", "-b", type=int, default=115200, help="Baud rate")
    parser.add_argument("--username", "-u", required=True, help="Username for calibration")
    parser.add_argument("--test", "-t", help="Test existing calibration file")
    parser.add_argument("--output", "-o", help="Output filename")
    
    args = parser.parse_args()
    
    calibrator = GloveCalibrationCapture(port=args.port, baudrate=args.baudrate)
    
    try:
        if args.test:
            # Test existing calibration
            with open(args.test, 'r') as f:
                calibration_data = json.load(f)
            calibrator.connect()
            calibrator.test_calibration(calibration_data)
        else:
            # Run new calibration
            calibration_data = calibrator.run_full_calibration(args.username)
            calibrator.save_calibration(calibration_data, args.output)
            
    except Exception as e:
        print(f"❌ Error: {e}")
    finally:
        calibrator.disconnect()

if __name__ == "__main__":
    main()
