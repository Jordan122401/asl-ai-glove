// ===== ASL Glove Capture — Calibrated Output + BLE UART (NUS) + Streaming Mode =====
// This firmware reads flex sensors and an accelerometer to capture hand gestures for ASL recognition
//
// ===== QUICK START GUIDE =====
// 
// 1. Run calibration: "cal" (follow prompts to relax hand, then bend each finger)
// 2. Run IMU calibration: "imu_cal" (place glove flat and still)
// 3. Set label: "label:A" or just "a" (for gesture A)
// 4. Collect data: "start" (captures 2.5 seconds of data)
// 5. Repeat steps 5-6 for each gesture with different labels
//
// ===== ALL AVAILABLE COMMANDS =====
// CALIBRATION:
//   cal               -> calibrate flex sensors (rest + max bend for each finger)
//   imu_cal           -> calibrate accelerometer (place flat and still)
//   savecal           -> manually save current calibration to NVS
//   setgamma <value>  -> adjust gamma curve for flex response (0.2-5.0, default 1.0)
//
// DATA COLLECTION (TRIAL MODE):
//   label:<text>      -> set gesture label (e.g., "label:hello" or "label:A")
//   <letter>          -> shortcut to set label (e.g., "a" sets label to "A")
//   trial:<n>         -> manually set trial ID counter (e.g., "trial:1")
//   start             -> begin 2.5 second data capture with current label
//   stop              -> manually stop recording (auto-stops after 2.5s)
//
// STREAMING MODE (CONTINUOUS):
//   stream         -> start continuous 75 samples streaming (for real-time AI)
//   stream:off        -> stop streaming
//
// CONFIGURATION:
//   rate:<Hz>         -> set sampling rate 1-100 Hz (default 30, applies to trial & stream)
//   detail            -> show current calibration values and settings (JSON format)
//
// USER PROFILES:
//   setuser <name>    -> switch to different user profile (loads their calibration)
//   whoami            -> display current active user
//   listusers         -> show all registered users

// OUTPUT FORMATS:
// Trial mode:   t_s,flex1..5,roll_deg,pitch_deg,ax_g,ay_g,az_g,label,trial_id
// Stream mode:  t_s,flex1..5,roll_deg,pitch_deg,ax_g,ay_g,az_g

// ===== Library Includes =====
#include <Arduino.h>                      // Core Arduino functions
#include <Wire.h>                         // I2C communication for accelerometer
#include <Adafruit_Sensor.h>              // Unified sensor library base
#include <Adafruit_ADXL345_U.h>           // ADXL345 accelerometer driver
#include <Preferences.h>                  // ESP32 non-volatile storage (NVS) for saving calibration

#include <BLEDevice.h>                    // Bluetooth Low Energy device management
#include <BLEServer.h>                    // BLE server for hosting services
#include <BLEUtils.h>                     // BLE utility functions
#include <BLE2902.h>                      // BLE descriptor for notifications

// ===== Configuration Constants =====
static uint32_t SAMPLE_HZ = 30;           // Sampling rate in Hz (adjustable via rate:<Hz> command)
static const uint16_t N_SAMPLES = 75;     // Number of samples per trial (75 samples * 30Hz = 2.5 seconds)
static const float    VREF      = 3.30f;  // ADC reference voltage (3.3V for ESP32)
static const float    R_FIXED   = 47000.0f; // Fixed resistor value in voltage divider (47kΩ)
static const bool     SENSOR_ON_TOP = true; // Voltage divider topology (sensor connected to VCC)

// GPIO pins for the 5 flex sensors (thumb, index, middle, ring, pinky)
const int FLEX_PINS[5] = {39, 34, 35, 32, 33};

// ===== IMU (Accelerometer) Setup =====
Adafruit_ADXL345_Unified accel = Adafruit_ADXL345_Unified(12345); // Create accelerometer object with ID 12345
bool adxl_ok = false;                     // Flag to track if ADXL345 was successfully initialized

// ===== Recorder State Variables =====
bool recording = false;                   // Flag indicating if currently recording a trial
unsigned long period_ms = 1000UL / 30;    // Sampling period in milliseconds (updated when SAMPLE_HZ changes)
unsigned long next_ms = 0;                // Timestamp for next sample (for precise timing)
uint16_t sample_count = 0;                // Counter for samples collected in current trial

String   current_label = "A";             // Current gesture label (default "A")
uint32_t trial_id      = 1;               // Trial ID counter (auto-increments after each trial)
unsigned long t0_ms    = 0;               // Start time reference for current trial

// ===== Streaming State Variables (NEW) =====
bool streaming = false;                   // Flag indicating if currently in continuous streaming mode
unsigned long stream_t0_ms = 0;           // Start time reference for streaming session

// ===== Streaming (windowed 75-sample batches) =====
static const uint16_t STREAM_WIN = N_SAMPLES;  // 75
uint16_t      win_idx   = 0;                   // index within current window
uint32_t      batch_id  = 1;                   // monotonically increasing batch counter
unsigned long win_t0_ms = 0;                   // window start (t_s resets each window)

// ===== Streaming window gap (fixed 1s between batches) =====
static const uint32_t STREAM_GAP_MS = 1000;   // 1 second pause between batches
static unsigned long stream_gap_until = 0;    // millis() time when next window may start
static bool stream_need_header = false;       // print header at the start of each window




// ===== Calibration Variables =====
// Flex sensor calibration: stores resistance at rest and maximum bend for each finger
float R_rest[5] = {0}, R_maxv[5] = {0};  // Arrays for rest and max resistance (in kΩ)
bool  flex_cal_ok = false;                // Flag indicating if flex calibration is valid

float gammaCurve = 1.0f;                  // Gamma correction curve for flex sensor response (default linear)

// IMU calibration: stores bias offsets for accelerometer axes
float ax_off = 0, ay_off = 0, az_off = 0; // Acceleration offsets (in m/s²)
bool  imu_cal_ok = false;                 // Flag indicating if IMU calibration is valid

// ===== Non-Volatile Storage (NVS) Objects =====
Preferences prefs_meta;                   // Metadata storage (user list, last user)
Preferences prefs_user;                   // Per-user calibration storage
String current_user_name = "default";     // Current active user profile

// ===== BLE (Nordic UART Service) Setup =====
static const char* BLE_NAME = "ASL-ESP32"; // Bluetooth device name visible to clients

// Standard Nordic UART Service (NUS) UUIDs for serial-like communication over BLE
#define NUS_SERVICE_UUID "6E400001-B5A3-F393-E0A9-E50E24DCCA9E" // Main service UUID
#define NUS_RX_CHAR_UUID "6E400002-B5A3-F393-E0A9-E50E24DCCA9E" // RX characteristic (receive commands)
#define NUS_TX_CHAR_UUID "6E400003-B5A3-F393-E0A9-E50E24DCCA9E" // TX characteristic (send data)

// BLE object pointers
BLEServer*         pServer  = nullptr;    // BLE server instance
BLECharacteristic* pTxChar  = nullptr;    // TX characteristic for sending data to client
BLECharacteristic* pRxChar  = nullptr;    // RX characteristic for receiving commands from client
volatile bool bleClientConnected = false; // Flag indicating if a BLE client is connected

// Function to send a string over BLE in chunks (handles long messages)
// ===== BLE TX payload sizing & line-safe notify =====
static uint16_t g_blePayload = 180; // safe default; updated on connect

static void bleSetPayloadFromMTU(uint16_t mtu) {
  // Effective payload per notification ≈ MTU - 3 (ATT header)
  if (mtu >= 23 && mtu <= 517) {
    uint16_t eff = (mtu > 3) ? (mtu - 3) : 20;
    if (eff < 20) eff = 20;
    if (eff > 244) eff = 244;     // conservative cap
    g_blePayload = eff;
  } else {
    g_blePayload = 180;
  }
}

// 
static void bleNotifyLine(const String& rawLine) {
  if (!pTxChar || !bleClientConnected) return;

  // Always end with CRLF so the app logs a clean line
  String line = rawLine;
  if (line.isEmpty() || line.charAt(line.length()-1) != '\n') line += "\r\n";

  const int n = line.length();
  const int CHUNK = (int)g_blePayload;

  for (int i = 0; i < n; i += CHUNK) {
    int end = i + CHUNK;
    if (end > n) end = n;
    const String part = line.substring(i, end);
    pTxChar->setValue((uint8_t*)part.c_str(), part.length());
    pTxChar->notify();
    delay(1);  // avoid flooding
  }
}

// helper (put near the top, after bleNotifyLine is defined)
static inline void logBoth(const String& s) {
  Serial.println(s);
  bleNotifyLine(s);
}
static inline void logBothf(const char* fmt, ...) {
  char buf[160];
  va_list ap; va_start(ap, fmt);
  vsnprintf(buf, sizeof(buf), fmt, ap);
  va_end(ap);
  Serial.println(buf);
  bleNotifyLine(buf);
}



// ===== NVS Helper Functions (Metadata Management) =====

// Open metadata namespace for reading/writing user list and last user
static void metaBegin() { prefs_meta.begin("meta", false); }

// Retrieve comma-separated list of all registered users
static String getUserListCSV(){
  metaBegin();                            // Open metadata namespace
  String s=prefs_meta.getString("users",""); // Get user list (empty string if none)
  prefs_meta.end();                       // Close namespace
  return s;
}

// Save comma-separated list of users to NVS
static void saveUserListCSV(const String& csv){
  metaBegin();                            // Open metadata namespace
  prefs_meta.putString("users", csv);     // Store user list
  prefs_meta.end();                       // Close namespace
}

// Add a new user to the user list (if not already present)
static void addUserToList(const String& name){
  String csv=getUserListCSV();            // Get current user list
  // Check if user already exists by searching for ",name," in ",csv,"
  if ((","+csv+",").indexOf(","+name+",")<0){
    if(csv.length()) csv+=",";            // Add comma separator if list not empty
    csv+=name;                            // Append new user
    saveUserListCSV(csv);                 // Save updated list
  }
}

// Store the last active user (for auto-loading on startup)
static void setLastUser(const String& name){
  metaBegin();                            // Open metadata namespace
  prefs_meta.putString("lastUser", name); // Store last user name
  prefs_meta.end();                       // Close namespace
}

// Retrieve the last active user
static String getLastUser(){
  metaBegin();                            // Open metadata namespace
  String u=prefs_meta.getString("lastUser","default"); // Get last user (default to "default")
  prefs_meta.end();                       // Close namespace
  return u;
}

// Open a user-specific namespace for calibration data
static bool openUserNS(const String& user, bool ro=false){
  prefs_user.end();                       // Close any previously open namespace
  return prefs_user.begin(user.c_str(), ro); // Open namespace (ro=true for read-only)
}

// Save calibration data for a specific user
static bool saveCalibrationForUser(const String& user){
  if(!openUserNS(user,false)) return false; // Open namespace for writing (return false if fails)
  
  // Save flex sensor calibration (rest and max resistance for each finger)
  for(int i=0;i<5;i++){
    prefs_user.putFloat((String("Rr")+i).c_str(), R_rest[i]); // Save rest resistance
    prefs_user.putFloat((String("Rm")+i).c_str(), R_maxv[i]); // Save max resistance
  }
  prefs_user.putBool("hasFlex", true);    // Mark that flex calibration exists
  
  // Save IMU calibration (acceleration offsets)
  prefs_user.putFloat("ax_off", ax_off);  // X-axis offset
  prefs_user.putFloat("ay_off", ay_off);  // Y-axis offset
  prefs_user.putFloat("az_off", az_off);  // Z-axis offset
  prefs_user.putBool("hasIMU", imu_cal_ok); // Mark IMU calibration status
  
  prefs_user.putFloat("gamma", gammaCurve); // Save gamma correction value
  prefs_user.end();                       // Close namespace
  
  addUserToList(user);                    // Add user to master list
  setLastUser(user);                      // Set as last active user
  return true;
}

// Load calibration data for a specific user
static bool loadCalibrationForUser(const String& user){
  if(!openUserNS(user,true)) return false; // Open namespace for reading (return false if fails)
  
  // Check if calibration data exists
  bool okFlex=prefs_user.getBool("hasFlex",false); // Check for flex calibration
  bool okIMU=prefs_user.getBool("hasIMU",false);   // Check for IMU calibration
  
  // Load flex sensor calibration if available
  if (okFlex){
    for(int i=0;i<5;i++){
      R_rest[i]=prefs_user.getFloat((String("Rr")+i).c_str(),0.0f); // Load rest resistance
      R_maxv[i]=prefs_user.getFloat((String("Rm")+i).c_str(),0.0f); // Load max resistance
    }
  }
  
  // Load IMU calibration (offsets default to 0 if not found)
  ax_off=prefs_user.getFloat("ax_off",0.0f);
  ay_off=prefs_user.getFloat("ay_off",0.0f);
  az_off=prefs_user.getFloat("az_off",0.0f);
  
  gammaCurve=prefs_user.getFloat("gamma",1.0f); // Load gamma correction (default 1.0 = linear)
  prefs_user.end();                       // Close namespace
  
  flex_cal_ok=okFlex;                     // Update global calibration flags
  imu_cal_ok=okIMU;
  
  // Print warning messages if calibration not found
  if(!okFlex) Serial.println("# NOTE: Flex calibration not found for this user. Run cal.");
  if(!okIMU)  Serial.println("# NOTE: IMU calibration not found. Run imu_cal.");
  
  return okFlex||okIMU;                   // Return true if any calibration exists
}

// ===== Math Helper Functions =====

// Convert 12-bit ADC reading to voltage
static inline float adcToV(uint16_t adc){
  return (adc/4095.0f)*VREF;              // Scale 0-4095 range to 0-VREF volts
}

// Calculate sensor resistance from voltage divider output
static inline float estimateSensorOhms(float v){
  float x = constrain(v, 0.001f, VREF-0.001f); // Avoid division by zero
  // Use appropriate formula based on voltage divider topology
  return SENSOR_ON_TOP ? R_FIXED * (VREF/x - 1.0f)  // Sensor on VCC side
                       : R_FIXED * (x/(VREF-x));     // Sensor on GND side
}

// Convert radians to degrees
static inline float toDegrees(float r){
  return r*57.29577951308232f;            // 180/π conversion factor
}

// Calculate roll and pitch angles from accelerometer readings
static inline void accelToRollPitch_ms2(float ax_ms2,float ay_ms2,float az_ms2,float& roll_deg,float& pitch_deg){
  const float g2=9.80665f;                // Standard gravity (m/s²)
  float ax=ax_ms2/g2, ay=ay_ms2/g2, az=az_ms2/g2; // Normalize to g-forces
  
  float denom = sqrtf(ay*ay+az*az)+1e-6f; // Denominator for pitch calc (avoid /0)
  roll_deg  = toDegrees(atan2f(ay,az));   // Roll = rotation around X-axis
  pitch_deg = toDegrees(atanf(-ax/denom)); // Pitch = rotation around Y-axis
}

// Clamp value to 0-1 range
static inline float clamp01(float x){
  return x<0?0:(x>1?1:x);                 // Return 0 if negative, 1 if >1, else x
}

// ===== Calibration Routines =====

// Read median resistance with periodic BLE progress updates.
// phase: "CAL FLEX: Rest" or "CAL FLEX: FINGER 1", etc.
float medianFingerRk(int pin, int n, const char* phase) {
  const int MAXS = 256;
  static float buf[MAXS];
  if (n > MAXS) n = MAXS;

  int nextTick = n / 10;      // 10% ticks
  if (nextTick <= 0) nextTick = 1;
  int tickAt = nextTick;

  for (int i = 0; i < n; i++) {
    uint16_t a = analogRead(pin);
    buf[i] = estimateSensorOhms(adcToV(a)) / 1000.0f;

    // Every ~10% samples, send a short progress line to BLE (not Serial spam)
    if (i + 1 >= tickAt) {
      int pct = ((i + 1) * 100) / n;
      // keep it short: fits in one notify
      bleNotifyLine(String("# ") + phase + " " + String(pct) + "%");
      tickAt += nextTick;
    }

    delay(1000UL / SAMPLE_HZ);
  }

  // Insertion sort
  for (int i = 1; i < n; i++) {
    float k = buf[i];
    int j = i - 1;
    while (j >= 0 && buf[j] > k) { buf[j + 1] = buf[j]; j--; }
    buf[j + 1] = k;
  }
  return (n & 1) ? buf[n/2] : 0.5f * (buf[n/2 - 1] + buf[n/2]);
}


// Calibrate all five flex sensors
void calibrateFlex(){
  logBoth("# CAL FLEX: Rest — keep hand relaxed for ~3 s");
  delay(200);
  const int N = 90;

  // Rest phase with progress
  for (int f = 0; f < 5; f++)
    R_rest[f] = medianFingerRk(FLEX_PINS[f], N, "CAL FLEX: Rest");

  // Per-finger max bend with progress
  for (int f = 0; f < 5; f++) {
    logBothf("# CAL FLEX: Bend FINGER %d to comfortable max (~3 s)", f+1);
    delay(200);
    char phase[40];
    snprintf(phase, sizeof(phase), "CAL FLEX: FINGER %d", f+1);
    R_maxv[f] = medianFingerRk(FLEX_PINS[f], N, phase);

    float dR = R_maxv[f] - R_rest[f];
    if (fabs(dR) < 0.8f)
      logBothf("# WARN: finger %d range small (ΔR≈%.2f kΩ)", f+1, dR);
  }

  flex_cal_ok = true;

  // Summary JSON + done
  String j = "{\"R_rest\":[";
  for (int i=0;i<5;i++){ if(i) j+=", "; j+=String(R_rest[i],2); }
  j += "],\"R_max\":[";
  for (int i=0;i<5;i++){ if(i) j+=", "; j+=String(R_maxv[i],2); }
  j += "],\"gamma\":"+String(gammaCurve,3)+"}";
  logBoth(j);
  logBoth("# CAL FLEX: done.");
}


// Calibrate IMU (accelerometer) by measuring bias while flat and still
void calibrateIMU(){
  if(!adxl_ok){
    logBoth("# CAL IMU: ADXL345 not detected.");
    return;
  }

  logBoth("# CAL IMU: Place glove FLAT & STILL (~4 s)...");
  delay(300);

  const int N = 120; // 4 s @ 30 Hz

  // throw away a few readings to settle
  for(int k=0;k<5;k++){
    sensors_event_t e;
    accel.getEvent(&e);
    delay(1000UL/SAMPLE_HZ);
  }

  // accumulate means
  double sx=0, sy=0, sz=0;
  for(int i=0;i<N;i++){
    sensors_event_t e;
    accel.getEvent(&e);
    sx += e.acceleration.x;
    sy += e.acceleration.y;
    sz += e.acceleration.z;
    delay(1000UL/SAMPLE_HZ);
  }

  // compute offsets
  const double mx = sx / N;
  const double my = sy / N;
  const double mz = sz / N;
  const double g  = 9.80665;

  ax_off = mx;
  ay_off = my;
  az_off = mz - g;
  imu_cal_ok = true;

  char b[160];
  snprintf(b, sizeof(b),
           "# CAL IMU OK: ax_off=%.4f ay_off=%.4f az_off=%.4f (m/s^2)",
           ax_off, ay_off, az_off);
  Serial.println(b);
  bleNotifyLine(String(b));

  // ✅ Save ONCE here (after offsets are known)
  if (saveCalibrationForUser(current_user_name)) {
    logBoth("# IMU calibration saved.");
  } else {
    logBoth("# ERROR: failed to save IMU calibration.");
  }
}


// ===== Output Header Functions =====

// Print CSV header for trial recording (includes label and trial_id)
void printHeader_trial(){
  const char* h="t_s,flex1,flex2,flex3,flex4,flex5,roll_deg,pitch_deg,ax_g,ay_g,az_g,label,trial_id";
  Serial.println(h);
  bleNotifyLine(String(h));
}

// Print CSV header for streaming mode (no label/trial_id)
void printHeader_stream(){
  const char* h="flex1,flex2,flex3,flex4,flex5,roll_deg,pitch_deg,ax_g,ay_g,az_g";
  Serial.println(h);
  bleNotifyLine(String("\n") + h);
}

// ===== Status Output =====

// Print minimal JSON with current configuration and calibration values
void printDetailJSON_min(){
  String j;
  j.reserve(256);                         // Pre-allocate memory for efficiency
  j+="{\"user\":\""+current_user_name+"\",\"gamma\":"+String(gammaCurve,3);
  
  // Add rest resistance array
  j+=",\"R_rest\":[";
  for(int i=0;i<5;i++){
    if(i) j+=",";
    j+=String(R_rest[i],3);
  }
  j+="],\"R_max\":[";
  
  // Add max resistance array
  for(int i=0;i<5;i++){
    if(i) j+=",";
    j+=String(R_maxv[i],3);
  }
  
  // Add IMU bias values
  j+="],\"imu_bias\":{\"ax_off\":";
  j+=String(ax_off,4)+",\"ay_off\":"+String(ay_off,4)+",\"az_off\":"+String(az_off,4)+"}}";
  
  Serial.println(j);
  bleNotifyLine(j);
}

// ===== Command Processing =====

// Process incoming commands from Serial or BLE
bool processCommand(String s){
  s.trim();                               // Remove leading/trailing whitespace
  s.toLowerCase();                        // Convert to lowercase for case-insensitive matching
  if (!s.length()) return true;           // Ignore empty commands

  // "detail" command: print current configuration and calibration
  if (s=="detail"){
    printDetailJSON_min();
    return true;
  }

  // "label:<text>" command: set the gesture label for upcoming trials
  if (s.startsWith("label:")){
    String v=s.substring(6);              // Extract label text after "label:"
    v.trim();
    if (!v.length()){
      Serial.println("# ERROR: label:<text>");
      bleNotifyLine("# ERROR: label:<text>");
    }
    else {
      // Sanitize label: replace invalid characters with underscore
      for(size_t i=0;i<v.length();i++){
        char c=v[i];
        if(!((c>='a'&&c<='z')||(c>='0'&&c<='9')||c=='_'||c=='-'))
          v.setCharAt(i,'_');
      }
      current_label=v;
      String m="# Label set to "+current_label;
      Serial.println(m);
      bleNotifyLine(m);
    }
    return true;
  }

  // Single letter command: shortcut to set label (e.g., "a" sets label to "A")
  if (s.length()==1){
    char c=s[0];
    if(c>='a'&&c<='z'){
      current_label=String((char)toupper((int)c)); // Convert to uppercase
      String m="# Label set to "+current_label;
      Serial.println(m);
      bleNotifyLine(m);
    }
    return true;
  }

  // "trial_id:<n>" or "trial:<n>" or "id:<n>": set trial ID counter
  if (s.startsWith("trial_id:")||s.startsWith("trial:")||s.startsWith("id:")){
    if (recording||streaming){
      String m="# Can't change trial_id while busy.";
      Serial.println(m);
      bleNotifyLine(m);
      return true;
    }
    int i=s.indexOf(':');                 // Find colon separator
    long v=s.substring(i+1).toInt();      // Parse integer after colon
    if (v>0){
      trial_id=(uint32_t)v;
      String m="# Trial ID set to "+String(trial_id);
      Serial.println(m);
      bleNotifyLine(m);
    }
    else {
      String m="# Invalid trial_id; use a positive integer, e.g. trial:7";
      Serial.println(m);
      bleNotifyLine(m);
    }
    return true;
  }

  // "cal" command: run flex sensor calibration
  if (s=="cal"){
    if(recording||streaming){
      Serial.println("# Busy.");
      bleNotifyLine("# Busy.");
      return true;
    }
    calibrateFlex();                      // Run calibration routine
    flex_cal_ok=true;                     // Mark as calibrated
    if(saveCalibrationForUser(current_user_name)){
      String m="# Saved flex calibration for user: "+current_user_name;
      Serial.println(m);
      bleNotifyLine(m);
    }
    return true;
  }

  // "imu_cal" command: run IMU calibration
  if (s=="imu_cal"){
    if(recording||streaming){
      Serial.println("# Busy.");
      bleNotifyLine("# Busy.");
      return true;
    }
    calibrateIMU();                       // Run IMU calibration routine
    return true;
  }

  // "setgamma <value>" or just "setgamma": set/view gamma correction curve
  if (s.startsWith("setgamma")){
    int i=s.indexOf(' ');                 // Find space separator
    if(i>0){
      float g=s.substring(i+1).toFloat(); // Parse gamma value
      if(g>0.2f && g<=5.0f){
        gammaCurve=g;
        String m="# gamma="+String(gammaCurve,3);
        Serial.println(m);
        bleNotifyLine(m);
        saveCalibrationForUser(current_user_name); // Save to NVS
      }
      else {
        Serial.println("# gamma out of range (0.2..5.0)");
        bleNotifyLine("# gamma out of range (0.2..5.0)");
      }
    }
    else {
      // No value provided: just display current gamma
      String m="# gamma="+String(gammaCurve,3);
      Serial.println(m);
      bleNotifyLine(m);
    }
    return true;
  }

  // "rate:<Hz>" command: set sampling rate (1-100 Hz)
  if (s.startsWith("rate:")){
    long hz=s.substring(5).toInt();       // Parse Hz value
    if(hz<1||hz>100){
      Serial.println("# rate out of range (1..100 Hz)");
      bleNotifyLine("# rate out of range (1..100 Hz)");
      return true;
    }
    SAMPLE_HZ=(uint32_t)hz;               // Update global sampling rate
    period_ms = max(1UL, 1000UL / SAMPLE_HZ); // Recalculate sampling period
    String m="# rate set to "+String(SAMPLE_HZ)+" Hz";
    Serial.println(m);
    bleNotifyLine(m);
    return true;
  }

  // "start" command: begin trial recording (2.5 second capture)
  if (s=="start"){
    // Check prerequisites
    if (!flex_cal_ok){
      String m="# ERROR: Run 'cal' first.";
      Serial.println(m);
      bleNotifyLine(m);
      return true;
    }
    if (!imu_cal_ok){
      String m="# ERROR: Run 'imu_cal' first.";
      Serial.println(m);
      bleNotifyLine(m);
      return true;
    }
    if (!recording){
      recording=true;                     // Set recording flag
      sample_count=0;                     // Reset sample counter
      next_ms=millis() + period_ms;                   // Initialize timing
      t0_ms=next_ms;                      // Set start time reference
      String m="# START (2.5 s) label="+current_label+" trial_id="+String(trial_id);
      Serial.println(m);
      bleNotifyLine(m);
      printHeader_trial();                // Print CSV header
      delay(2);
    }
    return true;
  }

  // "stop" command: stop recording or streaming
  if (s=="stop"){
    if (recording){
      recording=false;                    // Clear recording flag
      Serial.println("# STOP (manual)");
      bleNotifyLine("# STOP (manual)");
      trial_id++;                         // Increment trial ID for next recording
    }
    if (streaming){
      streaming=false;                    // Clear streaming flag
      Serial.println("# STREAM OFF");
      bleNotifyLine("# STREAM OFF");
      win_idx   = 0;
      win_t0_ms = 0;
    }
    return true;
  }

  // "stream" command: begin continuous streaming in 75-sample windows
if (s=="stream"){
  // Check prerequisites
  if (!flex_cal_ok){ bleNotifyLine("# ERROR: Run 'cal' first."); return true; }
  if (!imu_cal_ok){  bleNotifyLine("# ERROR: Run 'imu_cal' first."); return true; }
  if (recording){    bleNotifyLine("# Busy (trial running). Send 'stop' first."); return true; }

  if (!streaming){
    streaming   = true;
    next_ms     = millis() + period_ms;  // schedule first tick
    // reset window/batch state
    win_idx     = 0;
    win_t0_ms   = 0;                     // will be set on first sample
    stream_gap_until = 0;
    stream_need_header = true;

    // (keep old stream_t0_ms around for compatibility but unused for windows)

   logBoth("# STREAM ON (windowed, 75 samples per batch, 1s delay between batches)");
    delay(2);
  }
  return true;
}


  // "stream:off" command: stop continuous streaming
  if (s=="stream:off"){
    if (streaming){
      streaming=false;                    // Clear streaming flag
      Serial.println("# STREAM OFF");
      bleNotifyLine("# STREAM OFF");
    }
    return true;
  }

  // "setuser <name>" command: switch to a different user profile
  if (s.startsWith("setuser ")){
    if (recording||streaming){
      String m="# Busy. Send 'stop' first.";
      Serial.println(m);
      bleNotifyLine(m);
      return true;
    }
    String name=s.substring(8);           // Extract username after "setuser "
    name.trim();
    if(!name.length()){
      Serial.println("# ERROR: setuser <name>");
      bleNotifyLine("# ERROR: setuser <name>");
      return true;
    }
    current_user_name=name;               // Switch to new user
    setLastUser(current_user_name);       // Save as last user
    
    // Attempt to load calibration for this user
   if (loadCalibrationForUser(current_user_name)){
     logBothf("# Loaded calibration for user: %s", current_user_name.c_str());
   } else {
     logBothf("# No calibration found for user: %s", current_user_name.c_str());
     logBoth("# TIP: run 'setuser <name>' then 'cal' and 'imu_cal' to save.");
   }
    return true;
  }

  // "whoami" command: display current user
  if (s=="whoami"){
    String m="# user: "+current_user_name;
    Serial.println(m);
    bleNotifyLine(m);
    return true;
  }
  
  // "listusers" command: display all registered users
  if (s=="listusers"){
    String csv=getUserListCSV();          // Get comma-separated user list
    String m=String("# users: ")+(csv.length()?csv:"(none)");
    Serial.println(m);
    bleNotifyLine(m);
    return true;
  }
  
  // "savecal" command: manually save current calibration
  if (s=="savecal"){
    if(saveCalibrationForUser(current_user_name)){
      String m="# Saved calibration for user: "+current_user_name;
      Serial.println(m);
      bleNotifyLine(m);
    } else {
      Serial.println("# ERROR: save failed");
      bleNotifyLine("# ERROR: save failed");
    }
    return true;
  }

  return false; // Return false if command not recognized
}

// Check for and process commands from Serial monitor
void handleSerial(){
  while(Serial.available()){
    String s=Serial.readStringUntil('\n'); // Read line from Serial
    processCommand(s);                    // Process the command
  }
}

// ===== BLE Callback Classes =====

// Server callbacks: handle client connect/disconnect events
class NUSServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* s) override {
    bleClientConnected = true;
    uint16_t mtu = BLEDevice::getMTU();   // use what got negotiated
    bleSetPayloadFromMTU(mtu);
  }
  void onDisconnect(BLEServer*) override {
    bleClientConnected = false;
    BLEDevice::startAdvertising();
  }
};


// RX characteristic callbacks: handle incoming commands from BLE client
static String rxBuf;

class NUSRxCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* c) override {
    String v = c->getValue();         // <-- Arduino String
    if (!v.length()) return;

    bool sawNewline = false;

    // Feed chars, split on CR/LF as line endings
    for (size_t i = 0; i < v.length(); ++i) {
      char ch = v.charAt(i);
      if (ch == '\r' || ch == '\n') {
        sawNewline = true;
        if (rxBuf.length()) {
          String line = rxBuf;
          rxBuf = "";
          line.trim();                // handles CRLF and spaces
          if (line.length()) processCommand(line);
        }
      } else {
        rxBuf += ch;
      }
    }

    // If no newline was present in this write, treat it as a complete command
    if (!sawNewline && rxBuf.length()) {
      String line = rxBuf;
      rxBuf = "";
      line.trim();
      if (line.length()) processCommand(line);
    }

    if (rxBuf.length() > 1024) rxBuf = "";  // safety
  }
};




// ===== Setup Function (runs once at startup) =====
void setup(){
  // Initialize LED pin
  pinMode(LED_BUILTIN, OUTPUT);
  digitalWrite(LED_BUILTIN, LOW);         // Turn LED off initially

  // Initialize Serial communication
  Serial.begin(115200);                   // 115200 baud rate
  delay(200);                             // Wait for Serial to stabilize

  // Configure ADC for flex sensors
  analogReadResolution(12);               // Set ADC to 12-bit resolution (0-4095)
  for (int i=0;i<5;i++)
    analogSetPinAttenuation(FLEX_PINS[i], ADC_11db); // Set 11dB attenuation (0-3.3V range)

  // Initialize I2C bus and accelerometer
  Wire.begin(21,22,400000);               // SDA=21, SCL=22, 400kHz clock
  adxl_ok = accel.begin();                // Initialize ADXL345
  if (adxl_ok){
    accel.setRange(ADXL345_RANGE_2_G);    // Set ±2g range (most sensitive)
    accel.setDataRate(ADXL345_DATARATE_100_HZ); // Set 100Hz output rate
    logBoth("# ADXL345 OK");
  }
  else {
    logBoth("# WARN: ADXL345 not detected.");
  }

  // Load last active user and their calibration
  current_user_name = getLastUser();      // Retrieve last user from NVS
  if (!current_user_name.length())
    current_user_name="default";          // Fall back to "default" if empty
  
  if (loadCalibrationForUser(current_user_name)){
    Serial.printf("# Loaded calibration for user: %s\n", current_user_name.c_str());
  }
  else {
    Serial.printf("# No calibration found for user: %s\n", current_user_name.c_str());
    Serial.println("# TIP: run 'setuser <name>' then 'cal' and 'imu_cal' to save.");
  }

  // Calculate sampling period from rate
  period_ms = max(1UL, 1000UL / SAMPLE_HZ); // Convert Hz to milliseconds

  // Initialize BLE
  BLEDevice::init(BLE_NAME);              // Initialize BLE with device name
  BLEDevice::setMTU(185);                 // Set maximum transmission unit (larger packets)
  pServer = BLEDevice::createServer();    // Create BLE server
  pServer->setCallbacks(new NUSServerCallbacks()); // Attach connection callbacks
  
  // Create Nordic UART Service
  BLEService* svc = pServer->createService(NUS_SERVICE_UUID);

  // Create TX characteristic (for sending data to client)
  pTxChar = svc->createCharacteristic(NUS_TX_CHAR_UUID, BLECharacteristic::PROPERTY_NOTIFY);
  pTxChar->addDescriptor(new BLE2902()); // Add Client Characteristic Configuration descriptor

  // Create RX characteristic (for receiving commands from client)
  pRxChar = svc->createCharacteristic(NUS_RX_CHAR_UUID, 
                                      BLECharacteristic::PROPERTY_WRITE | 
                                      BLECharacteristic::PROPERTY_WRITE_NR);
  pRxChar->setCallbacks(new NUSRxCallbacks()); // Attach write callback

  svc->start();                           // Start the service
  
  // Configure and start BLE advertising
  auto adv = BLEDevice::getAdvertising();
  adv->addServiceUUID(NUS_SERVICE_UUID);  // Advertise NUS service UUID
  adv->setScanResponse(true);             // Enable scan response for more info
  BLEDevice::startAdvertising();          // Begin advertising

  // Print startup messages
  logBoth("# BLE ready. Use Bluetooth LE Explorer.");
  logBoth("# Commands: start/stop (trial), stream:on/off, rate:<Hz>, cal, imu_cal, label:..., setuser ...");
}

// ===== Main Loop (runs continuously) =====
void loop(){
  unsigned long now = millis();           // Get current timestamp
  handleSerial();                         // Check for Serial commands

  // Exit early if neither recording nor streaming
  if (!recording && !streaming) return;

  // Wait until next sample time
  if (now < next_ms) return;
  next_ms += period_ms;                   // Schedule next sample

  // Set time reference on first sample
  if (recording && sample_count==0) t0_ms = now;
  if (streaming && stream_t0_ms==0) stream_t0_ms = now;

  // Calculate elapsed time in seconds relative to current mode's start time
  float t_s = ((recording ? (now - t0_ms) : (now - stream_t0_ms))) / 1000.0f;

  // Read all 5 flex sensors and convert to resistance
  float Rk[5];                            // Array to store raw resistance values (kΩ)
  for(int i=0;i<5;i++){
    uint16_t adc=analogRead(FLEX_PINS[i]); // Read 12-bit ADC value
    Rk[i]=estimateSensorOhms(adcToV(adc))/1000.0f; // Convert to kΩ
  }

  // Normalize and apply gamma correction to flex values (0-1 range)
  float bend_cal[5];                      // Calibrated bend values
  for(int i=0;i<5;i++){
    float denom = (R_maxv[i]-R_rest[i]);  // Calculate calibration range
    float x=(Rk[i]-R_rest[i])/(fabs(denom)<1e-6f?1e-6f:denom); // Normalize (avoid /0)
    x = clamp01(x);                       // Clamp to 0-1 range
    bend_cal[i] = powf(x, gammaCurve);    // Apply gamma correction curve
  }

  // Read and process IMU data
  float ax=0, ay=0, az=0, roll_deg=0, pitch_deg=0; // Initialize variables
  if (adxl_ok){                           // Only read if accelerometer is working
    sensors_event_t e;
    accel.getEvent(&e);                   // Read accelerometer data
    
    // Apply calibration offsets
    float axp=e.acceleration.x-(imu_cal_ok?ax_off:0.0f); // X-axis with offset
    float ayp=e.acceleration.y-(imu_cal_ok?ay_off:0.0f); // Y-axis with offset
    float azp=e.acceleration.z-(imu_cal_ok?az_off:0.0f); // Z-axis with offset
    
    // Calculate roll and pitch angles
    accelToRollPitch_ms2(axp,ayp,azp,roll_deg,pitch_deg);
    
    // Convert to g-forces (normalize by standard gravity)
    const float G=9.80665f;               // Standard gravity constant
    ax=axp/G;                             // X acceleration in g
    ay=ayp/G;                             // Y acceleration in g
    az=azp/G;                             // Z acceleration in g
  }

  // Output data based on current mode
  if (recording){
    // Trial mode: include label and trial_id in CSV line
    String line;
    line.reserve(170);                    // Pre-allocate memory for efficiency
    
    // Build CSV line: time, 5 flex values, roll, pitch, 3 accelerations, label, trial_id
    line  = String(t_s,3);                // Time with 3 decimal places
    line += ","+String(bend_cal[0],3)+","+String(bend_cal[1],3)+","+String(bend_cal[2],3)+","+String(bend_cal[3],3)+","+String(bend_cal[4],3);
    line += ","+String(roll_deg,2)+","+String(pitch_deg,2); // Angles with 2 decimals
    line += ","+String(ax,3)+","+String(ay,3)+","+String(az,3); // Accelerations with 3 decimals
    line += ","+current_label+","+String(trial_id); // Append label and trial ID
    
    // Send to Serial and BLE
    Serial.println(line);
    bleNotifyLine(line);

    sample_count++;                       // Increment sample counter
    
    // Check if trial is complete (75 samples = 2.5 seconds @ 30Hz)
    if (sample_count >= N_SAMPLES){
      recording=false;                    // Stop recording
      Serial.println("# AUTO STOP (2.5 s complete)");
      bleNotifyLine("# AUTO STOP (2.5 s complete)");
      trial_id++;                         // Auto-increment trial ID for next trial
    }
  }

  if (streaming){
  // Start-of-window housekeeping: optional gap + header + t=0 latch
  if (win_idx == 0) {
    // If a gap is scheduled (set at the end of the previous window), wait non-blocking
    if (stream_gap_until && millis() < stream_gap_until) {
      return;   // keep BLE/Serial responsive during the pause
    }
    // Gap completed? clear it
    if (stream_gap_until && millis() >= stream_gap_until) {
      stream_gap_until = 0;
    }
    // Print CSV header at the beginning of *every* window
    if (stream_need_header) {
      printHeader_stream();
      stream_need_header = false;
      win_t0_ms = millis();  // latch window t=0 if you want to use it later
    }
    if (win_t0_ms == 0) win_t0_ms = millis();
  }

  // ----- build one CSV row: flex1..5, roll, pitch, ax, ay, az -----
  String s;
  s.reserve(170);
  s  = String(bend_cal[0],3) + "," + String(bend_cal[1],3)
     + "," + String(bend_cal[2],3)
     + "," + String(bend_cal[3],3)
     + "," + String(bend_cal[4],3);
  s += "," + String(roll_deg,2) + "," + String(pitch_deg,2);
  s += "," + String(ax,3) + "," + String(ay,3) + "," + String(az,3);

  Serial.println(s);
  bleNotifyLine(s);

  // Advance within the 75-sample window
  win_idx++;

  // Window done? schedule the 1s gap and mark to print header next window
  if (win_idx >= STREAM_WIN) {
    win_idx           = 0;
    win_t0_ms         = 0;
    stream_need_header= true;                          // print header before next window
    stream_gap_until  = millis() + STREAM_GAP_MS;     // schedule 1s pause
  }
}

}
