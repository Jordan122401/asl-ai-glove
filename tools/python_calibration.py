#!/usr/bin/env python3
"""
Glove Calibration Script for ASL Translation App
This script collects calibration data from the glove and saves it in a format
compatible with the Android app's CalibrationData structure.
"""

import serial
import time
import json
import numpy as np
from typing import List, Tuple, Optional
import argparse
import os

class GloveCalibrator:
    def __init__(self, port: str = None, baudrate: int = 9600):
        """
        Initialize the glove calibrator.
        
        Args:
            port: Serial port (e.g., 'COM3' on Windows, '/dev/ttyUSB0' on Linux)
            baudrate: Serial communication baud rate
        """
        self.port = port
        self.baudrate = baudrate
        self.serial_connection = None
        self.num_sensors = 5  # flex1-flex5
        
    def connect(self) -> bool:
        """Connect to the glove via serial."""
        try:
            if self.port is None:
                # Auto-detect port
                self.port = self._auto_detect_port()
                if self.port is None:
                    print("❌ Could not auto-detect serial port")
                    return False
            
            self.serial_connection = serial.Serial(
                port=self.port,
                baudrate=self.baudrate,
                timeout=1
            )
            time.sleep(2)  # Wait for connection to stabilize
            print(f"✅ Connected to glove on {self.port}")
            return True
        except Exception as e:
            print(f"❌ Failed to connect: {e}")
            return False
    
    def _auto_detect_port(self) -> Optional[str]:
        """Auto-detect the serial port."""
        import serial.tools.list_ports
        
        ports = serial.tools.list_ports.comports()
        for port in ports:
            if "USB" in port.description or "Serial" in port.description:
                return port.device
        return None
    
    def disconnect(self):
        """Disconnect from the glove."""
        if self.serial_connection and self.serial_connection.is_open:
            self.serial_connection.close()
            print("✅ Disconnected from glove")
    
    def read_sensor_data(self) -> Optional[List[float]]:
        """
        Read sensor data from the glove.
        Expected format: "flex1,flex2,flex3,flex4,flex5,roll,pitch,ax,ay,az"
        """
        if not self.serial_connection or not self.serial_connection.is_open:
            return None
        
        try:
            line = self.serial_connection.readline().decode('utf-8').strip()
            if line:
                # Parse the sensor data
                values = [float(x) for x in line.split(',')]
                if len(values) >= self.num_sensors:
                    # Return only flex sensor values (first 5)
                    return values[:self.num_sensors]
        except Exception as e:
            print(f"⚠️ Error reading sensor data: {e}")
        
        return None
    
    def collect_calibration_data(self, duration: float = 3.0, sample_rate: float = 10.0) -> List[float]:
        """
        Collect sensor data for calibration.
        
        Args:
            duration: Duration to collect data (seconds)
            sample_rate: Samples per second
            
        Returns:
            List of averaged sensor values
        """
        print(f"📊 Collecting data for {duration} seconds...")
        
        samples = []
        start_time = time.time()
        sample_interval = 1.0 / sample_rate
        
        while time.time() - start_time < duration:
            data = self.read_sensor_data()
            if data is not None:
                samples.append(data)
                print(f"  Sample {len(samples)}: {[f'{x:.2f}' for x in data]}")
            
            time.sleep(sample_interval)
        
        if not samples:
            raise Exception("No sensor data collected!")
        
        # Calculate average values
        avg_values = np.mean(samples, axis=0).tolist()
        print(f"✅ Collected {len(samples)} samples")
        print(f"📈 Average values: {[f'{x:.2f}' for x in avg_values]}")
        
        return avg_values
    
    def calibrate_rest_position(self) -> List[float]:
        """Calibrate the rest position (baseline values)."""
        print("\n🖐️  REST POSITION CALIBRATION")
        print("Please relax your hand completely (fingers straight)")
        print("Press Enter when ready...")
        input()
        
        return self.collect_calibration_data(duration=3.0, sample_rate=10.0)
    
    def calibrate_flex_position(self) -> List[float]:
        """Calibrate the flex position (maximum values)."""
        print("\n✊ FLEX POSITION CALIBRATION")
        print("Please make a tight fist (all fingers fully flexed)")
        print("Press Enter when ready...")
        input()
        
        return self.collect_calibration_data(duration=3.0, sample_rate=10.0)
    
    def run_full_calibration(self, username: str) -> dict:
        """
        Run the complete calibration process.
        
        Args:
            username: Username for the calibration
            
        Returns:
            Calibration data dictionary
        """
        print(f"\n🎯 Starting calibration for user: {username}")
        print("=" * 50)
        
        # Connect to glove
        if not self.connect():
            raise Exception("Failed to connect to glove")
        
        try:
            # Calibrate rest position
            baseline_values = self.calibrate_rest_position()
            
            # Calibrate flex position
            maximum_values = self.calibrate_flex_position()
            
            # Create calibration data
            calibration_data = {
                "username": username,
                "isCalibrated": True,
                "calibrationData": {
                    "sensorBaselines": baseline_values,
                    "sensorMaximums": maximum_values,
                    "calibrationTimestamp": int(time.time() * 1000)  # milliseconds
                }
            }
            
            print("\n✅ Calibration completed successfully!")
            print(f"📊 Baseline values: {[f'{x:.2f}' for x in baseline_values]}")
            print(f"📊 Maximum values: {[f'{x:.2f}' for x in maximum_values]}")
            
            return calibration_data
            
        finally:
            self.disconnect()
    
    def save_calibration(self, calibration_data: dict, filename: str = None):
        """Save calibration data to a JSON file."""
        if filename is None:
            username = calibration_data["username"]
            filename = f"calibration_{username}.json"
        
        with open(filename, 'w') as f:
            json.dump(calibration_data, f, indent=2)
        
        print(f"💾 Calibration saved to: {filename}")
    
    def load_calibration(self, filename: str) -> dict:
        """Load calibration data from a JSON file."""
        with open(filename, 'r') as f:
            return json.load(f)
    
    def test_calibration(self, calibration_data: dict):
        """Test the calibration by showing normalized values."""
        print("\n🧪 Testing calibration...")
        print("Move your hand and watch the normalized values:")
        print("(Press Ctrl+C to stop)")
        
        baseline = np.array(calibration_data["calibrationData"]["sensorBaselines"])
        maximum = np.array(calibration_data["calibrationData"]["sensorMaximums"])
        
        try:
            while True:
                data = self.read_sensor_data()
                if data is not None:
                    # Normalize the data
                    raw_values = np.array(data)
                    normalized = (raw_values - baseline) / (maximum - baseline)
                    normalized = np.clip(normalized, 0, 1)
                    
                    print(f"Raw: {[f'{x:.2f}' for x in raw_values]} | "
                          f"Normalized: {[f'{x:.2f}' for x in normalized]}")
                
                time.sleep(0.1)
        except KeyboardInterrupt:
            print("\n🛑 Test stopped")

def main():
    parser = argparse.ArgumentParser(description="Glove Calibration Tool")
    parser.add_argument("--port", "-p", help="Serial port (e.g., COM3)")
    parser.add_argument("--baudrate", "-b", type=int, default=9600, help="Baud rate")
    parser.add_argument("--username", "-u", required=True, help="Username for calibration")
    parser.add_argument("--test", "-t", help="Test existing calibration file")
    parser.add_argument("--output", "-o", help="Output filename")
    
    args = parser.parse_args()
    
    calibrator = GloveCalibrator(port=args.port, baudrate=args.baudrate)
    
    try:
        if args.test:
            # Test existing calibration
            calibration_data = calibrator.load_calibration(args.test)
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
