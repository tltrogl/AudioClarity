# Audio Clarity — Real-Time Listening Assist (Android)

**Audio Clarity** is a real-time listening-assist app: it captures microphone audio, applies voice-focused DSP (digital signal processing), and plays the result to your connected headphones/earbuds.

This is a **user-visible** listening session that runs reliably in the background.

---

## Core Features

### Runs in the Background
- The app is designed to run continuously as a background task. As long as the session is active, audio processing will continue even if you lock your screen or switch to another app.
- This is accomplished using a **Foreground Service**, which is Android's modern and required way to run long-lasting background tasks, indicated by a persistent notification.

### Adaptive Auto-Clarity (Speech Enhancement)
- **Voice Activity Detection (VAD):** detects when speech is present.
- **Pitch tracking:** estimates the voice's fundamental frequency.
- **Dynamic "speech EQ":** boosts voice-relevant frequencies when speech is detected.

### Scout Mode Replay (“What did they just say?”)
- Continuously maintains a rolling in-memory buffer (last ~5 seconds).
- One-tap **Replay** (in-app or notification) plays a boosted version of the last moments.

### Manual DSP Controls
- Adjustable gain (volume boost).
- High-pass filter (cuts low rumble).
- Noise gate (mutes output during quiet moments).
- Optional Android system effects when supported (device-dependent):
  - Noise Suppressor (NS)
  - Acoustic Echo Canceler (AEC)
  - Automatic Gain Control (AGC)

### Safety & Stability
- Always-on limiter (prevents harsh spikes at higher gain).
- Automatically stops passthrough if headphones disconnect (prevents speaker feedback loops).
- Persistent settings: restores your last configuration automatically.

### Developer/Diagnostics
- Diagnostics screen: output device, sample rate, buffer size, pitch estimate, etc.
- Detailed internal logging for session state + audio parameters.

---

## What This App Does NOT Do
- **No stealth mode or invisible operation.** A persistent notification is always visible while the service is active, as required by Android for user safety and privacy.
- No hidden/background recording.
- No “listen through walls” claims.
- No cloud processing, accounts, or uploading.

---

## ⚠️ Safety Warning (Read This)
**Use headphones/earbuds.**
Do not run mic passthrough through the phone speaker — it can cause immediate high-volume feedback.

Keep gain reasonable.

---

## Reality Check: Latency
Bluetooth latency varies by device + earbuds. This app prioritizes stability. For the most immediate, low-latency experience, wired or USB headphones are recommended.

Use the **Calibrate** button in the main screen to run the built-in latency test. The calibration screen measures round-trip delay for your current audio path and saves it so playback buffers can better align with your device. Calibration temporarily adjusts routing for the test, then restores your prior audio routing so you can safely rerun it anytime.

---

## Build & Run
1. Open in **Android Studio**
2. Run on a physical Android device
3. Grant **Microphone** + **Notifications** permissions

---

## How to Use
1. Connect headphones/earbuds
2. Tap **Start**
3. Adjust **Gain**
4. Either:
   - Enable **Auto-Clarity** (speech enhancement), or
   - Use manual toggles (HPF / Gate / system effects)
5. Tap **Replay** to hear the last moments
6. Tap **Stop** (in-app or notification)

---

## Architecture
This project uses a clean, agent-based architecture aimed at stability and maintainability.
See **`AGENTS.md`** for components and threading model.
