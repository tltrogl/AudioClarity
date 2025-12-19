# Ear Scout — Features

Ear Scout is a **foreground listening assist** app for Android: it captures microphone audio, applies lightweight processing for clarity and safety, and plays it back to your connected headphones/earbuds (including Bluetooth).

> **Important:** This is a user-visible listening session (persistent notification while active). No stealth/hidden behavior.

---

## v0 (Baseline) — Listening Assist (Passthrough)

### Session & reliability
- **Start/Stop listening session** from the app UI
- **Background operation via Foreground Service**
  - Runs while the screen is off / app is backgrounded
  - **Persistent notification** while active with a Stop action
- **Graceful recovery**
  - Handles Bluetooth disconnects without crashing
  - Idempotent Start/Stop (no double-start races)
- **Low-alloc audio loop** (stable playback; avoids GC spikes)

### Audio pipeline
- **Microphone capture** (`AudioRecord`) → **DSP** → playback (`AudioTrack`)
- **Stable buffer sizing**
  - Uses Android min buffer size as a baseline
  - Chooses stability over “tiny buffers” that crackle on real devices

### Audio processing (DSP)
- **User gain slider** (digital amplification)
- **High‑pass filter (“voice clarity”)**
  - Cuts low-frequency rumble (wind/engine/handling noise)
- **Hard limiter (safety)**
  - Prevents clipping and reduces painful spikes at higher gain
- **Full-band graphic EQ (10 bands, 31 Hz – 16 kHz)**
  - Fine-tune tone across the entire spectrum
- Optional (device-dependent) **Android system effects** when available:
  - **Automatic Gain Control (AGC)**
  - **Noise Suppression (NS)**
  - **Acoustic Echo Cancellation (AEC)**
- **No recording by default**
  - Audio is processed in memory for playback, not saved

### UI/UX
- **Simple controls**
  - Start/Stop
  - Gain slider
  - Toggle(s): clarity filter, limiter, optional system effects (only if supported)
- **Status feedback**
  - Permission state
  - Session state (Starting/Running/Stopping/Error)

### Safety & compliance
- **Always user-visible** listening (foreground notification)
- **Speaker feedback protection**
  - Speaker output is blocked by default or gated behind a warning
- Clear in-app disclosure:
  - what it does
  - how to stop
  - hearing safety warning

---

## Reality check: Bluetooth latency
Bluetooth output latency varies by device + earbuds. v0 prioritizes:
- **stability** (no dropouts)
- **predictable behavior** (no fake “real-time” marketing)

---

## v1 (Recommended direction) — Scout Mode (Detect + Boosted Replay)

Bluetooth-only setups feel better with **buffered replay** instead of trying to do “instant amplification.”

### Scout Mode behavior
- Maintain a rolling **3–10 second in-memory ring buffer**
- Detect selected events (examples):
  - smoke-alarm beep patterns
  - siren-like tones
  - nearby speech presence (voice activity)
  - loud sudden peaks (fallback)
- On trigger:
  - send an alert (notification/vibration)
  - optionally **auto-play a boosted replay clip** (“hear what just happened”)

### Boost Profiles (your key requirement)
Per event type, apply a preset to the replay clip:
- **Speech clarity**: HPF + speech EQ + gentle compression
- **Alarm emphasis**: band emphasis + limiter
- Optional: **Auto volume bump** during replay (with user cap), then restore prior volume

### Monetization-friendly additions
- Saved profiles (Home/Car/Work)
- Quiet hours
- Longer history + export
- More detectors (knock/bark/baby cry) if/when you add ML

---

## Out of scope / not supported
- Stealth monitoring
- Listening “through walls”
- Recording other apps’ audio
- Any hidden/undisclosed capture
