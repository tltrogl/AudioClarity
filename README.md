# Ear Scout (Android) — Listening Assist (Passthrough)

Ear Scout is a simple, robust Android app that captures microphone audio, applies lightweight processing for clarity and safety, and plays it back to your connected headphones/earbuds (including Bluetooth).

This project is intentionally minimal: it’s built to “just work” and remain easy to maintain.

---

## What it does
- Foreground “Listening” session (**persistent notification** while active)
- Microphone → processing → headphone/earbud playback
- Voice-focused clarity filter (reduces low-frequency rumble)
- Adjustable gain (volume boost) with a safety limiter
- Optional Android pre-processing (AGC / Noise Suppression / Echo Cancel) when supported

## What it does NOT do
- No stealth mode
- No hidden recording
- No “listen through walls” claims
- No cloud processing / accounts

---

## ⚠️ Safety warning (read this)
**Use headphones/earbuds.**
Do not run this app with the phone’s main speaker output while listening is active. The microphone can pick up speaker output and create an immediate, extremely loud feedback loop that can damage hearing and/or equipment.

Keep gain reasonable and enable the limiter.

---

## Reality check: Bluetooth latency
Bluetooth routing adds latency that varies by device and earbuds. This app prioritizes stability and predictable behavior over “perfect real-time” claims.

If you want Bluetooth-first “works reliably” behavior, the recommended roadmap is **Scout Mode** (detect + boosted replay clip). See Roadmap.

---

## Requirements
- Android SDK 24+ (Android 7.0 Nougat) recommended baseline
- A physical Android device (emulators often behave badly with audio)
- Microphone permission
- Headphones/earbuds (Bluetooth supported)

---

## Permissions
- **Microphone** (`RECORD_AUDIO`) — required to capture audio
- **Notifications** (`POST_NOTIFICATIONS`) — recommended so the foreground session notification is visible and controllable

---

## How to run (Android Studio)
1. Open the project in **Android Studio**
2. Connect a physical Android device
3. Build & Run
4. Grant Microphone + Notifications permissions when prompted

---

## How to use
1. Connect your Bluetooth earbuds/headphones
2. Open Ear Scout
3. Tap **Start**
4. Adjust **Gain** carefully (start low)
5. Lock the screen if you want — the session continues via the foreground service
6. Tap **Stop** in the app or from the persistent notification

---

## Troubleshooting
### No sound in earbuds
- Confirm earbuds are the active output route and media volume is up.
- Disconnect/reconnect earbuds and restart the session.
- Start the session and wait 1–2 seconds before locking the screen (some devices route late).

### Echo/feedback
- You’re using speaker output or audio is leaking into the mic.
- Lower gain, enable limiter, and keep the phone mic away from the earbud speaker.

### Choppy audio
- Increase buffer size (stability > low latency).
- Disable AGC/NS/AEC toggles one by one to isolate device-specific problems.

---

## Project structure (recommended)
- `app/` UI + service binding + notification UI
- `core/audio/` AudioRecord/AudioTrack setup, routing helpers
- `core/dsp/` HPF/EQ/limiter/gain chain
- `core/fx/` Android AudioFX wrappers (AGC/NS/AEC)
- `core/settings/` DataStore preferences
- `core/diag/` logging + diagnostics screen (optional)

---

## Build (Windows / PowerShell)
```powershell
.\gradlew assembleDebug
```

## Test
```powershell
.\gradlew test
```

---

## Roadmap (Bluetooth-first “works” behavior)
### Scout Mode: detect + boosted replay (recommended direction)
Instead of promising real-time amplification over Bluetooth:
- Maintain a rolling 3–10 second in-memory buffer
- Detect events (speech nearby, alarm beeps, siren-like patterns, loud sudden peaks)
- On trigger: notify + optionally play a boosted replay clip (“hear what just happened”)
- Per-event Boost Profiles (speech clarity vs alarm emphasis), plus optional volume bump with restore

---

## License
Pick one:
- MIT
- Apache-2.0
