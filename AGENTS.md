# Ear Scout — AGENTS.md
System Agents & Architecture (Android)

## 0) Product intent (plain English)
Ear Scout is a **foreground listening assist** app:
- Captures microphone audio
- Applies lightweight processing (clarity filter + gain + limiter + optional system pre-processing)
- Plays the result to the currently selected output route (typically Bluetooth earbuds/headphones)
- Continues in the background via a **Foreground Service** + persistent notification

Optional roadmap: “Scout Mode” (event detection + boosted replay clips). Baseline is “Monitor/Passthrough Mode”.

## 1) Non-negotiables (safety + compliance + usability)
- Foreground session only: user must always be able to tell it’s running (persistent notification + in-app state).
- No stealth/hidden behaviors. No “spy” positioning.
- No speaker output by default (feedback risk). If allowed, require explicit “I understand” gating.
- Safe output: hard limiter on playback + user-configurable gain cap.
- Minimal permissions: `RECORD_AUDIO` required; `POST_NOTIFICATIONS` recommended.

## 2) Runtime model (high level)
A “listening session” is a stateful pipeline:

Mic Input → (optional system pre-processing: AGC/NS/AEC) → App DSP (HPF/EQ + gain + limiter) → Output playback

The pipeline is owned by a Foreground Service so it survives UI/background changes.

## 3) Agents (components) and responsibilities

### 3.1 UI/Control Agent (`MainActivity`)
**Role:** Human controls + session visibility.  
**Responsibilities:**
- Request runtime permissions (`RECORD_AUDIO`, `POST_NOTIFICATIONS`).
- Present controls:
  - Start / Stop
  - Gain slider
  - Toggles: clarity, limiter, system effects (if supported)
- Bind to `AudioService` via `Binder` and call service methods.
- Reflect the service’s actual state (IDLE/STARTING/RUNNING/STOPPING/ERROR).

### 3.2 Session Orchestrator Agent (`AudioService`)
**Role:** Owns session lifecycle, threading, and reliability.  
**Responsibilities:**
- Promote to **Foreground Service** at start:
  - persistent notification with Stop action
  - notification channel management
- Own session state machine:
  - `IDLE`, `STARTING`, `RUNNING`, `STOPPING`, `ERROR`
- Deterministic resource control:
  - create/start/stop/release `AudioRecord`
  - create/play/stop/release `AudioTrack`
- Prevent races:
  - `start()` and `stop()` idempotent
  - guarded transitions
- Apply configuration updates (gain/toggles) thread-safely.

**Binder methods (example):**
- `startAudioPassthrough()`
- `stopAudioPassthrough()`
- `setVolumeGain(float gain)`
- `setOptions(Options opts)`

### 3.3 Audio Capture Agent (`AudioInput`)
**Role:** Configure and read microphone frames reliably.  
**Responsibilities:**
- Select sample rate + format (e.g., 44.1kHz, mono, PCM16).
- Select an `AudioSource` appropriate for desired effects:
  - `VOICE_COMMUNICATION` / `VOICE_RECOGNITION` can improve AEC/NS/AGC behavior on some devices.
- Choose stable buffer sizes:
  - base on `getMinBufferSize()` and pick a safe multiple.
- Produce frames to DSP as PCM.

### 3.4 System Pre-Processing Agent (`AudioFxPreproc`)
**Role:** Attach Android-provided effects when available.  
**Responsibilities:**
- Optionally attach/enable:
  - `AutomaticGainControl`
  - `NoiseSuppressor`
  - `AcousticEchoCanceler`
- Expose availability honestly (device-dependent).
- If an effect can’t be enabled, surface this in UI/logs.

### 3.5 DSP Agent (`DspChain`)
**Role:** App-owned signal processing chain.  
**Recommended baseline chain:**
1) High-pass filter (rumble cut; “speech clarity” feel)  
2) Gain multiply (user-controlled)  
3) Hard limiter (prevents clipping spikes)  
4) Optional: simple EQ preset for speech emphasis  
5) Optional: noise gate (reduce hiss in quiet scenes)

**Rules:**
- No allocations in the audio loop.
- Keep output in range (no overflow/clipping after limiter).

### 3.6 Output/Playback Agent (`AudioOutput`)
**Role:** Play processed audio to the current route.  
**Responsibilities:**
- Configure `AudioTrack` for low-latency where possible.
- Stable write loop (handle partial writes; track underruns).
- Prefer stability over “chasing minimum latency”.

### 3.7 Notification/Controls Agent (`SessionNotification`)
**Role:** Make the session visible and controllable outside the app.  
**Responsibilities:**
- Persistent notification while RUNNING:
  - state + Stop action
  - deep-link to app

### 3.8 Settings Agent (`SettingsRepo`)
**Role:** Persist user preferences.  
**Responsibilities:**
- Store last gain, toggles, caps, etc.
- Use `DataStore` (preferred) or SharedPreferences.

### 3.9 Diagnostics Agent (`DiagLogger`)
**Role:** Make debugging audio problems possible.  
**Responsibilities:**
- Log:
  - session transitions
  - buffer sizes / sample rate
  - effect availability
  - underruns/errors
- Optional diagnostics screen:
  - output route
  - effect support
  - session uptime counters

### 3.10 Roadmap Agent (optional): Event Detection + Replay (`ScoutEngine`)
**Role:** Detect events and play boosted “last N seconds” clips.  
**Behavior:**
- Maintain 3–10s ring buffer
- Detector triggers → notification → boosted replay clip
- Per-event Boost Profile presets (speech vs alarm vs general)

This is the Bluetooth-friendly way to deliver “I was alerted—boost what I missed.”

## 4) Threading & performance model
- Main thread: UI only.
- Audio thread: tight loop:
  - `AudioRecord.read` → DSP → `AudioTrack.write`
- No heavy work in audio thread (no DB/file IO).

Settings updates use atomics/volatile or a thread-safe queue.

## 5) State machine (authoritative)
- `IDLE` → start() → `STARTING` → success → `RUNNING`
- `RUNNING` → stop() → `STOPPING` → release → `IDLE`
- Any fatal error → `ERROR` → stop() releases → `IDLE`

## 6) Acceptance tests (manual)
- Start session with permission granted → audio plays to headphones.
- Limiter ON prevents harsh clipping at high gain.
- Background/lock screen → session continues with persistent notification.
- Stop from notification → session ends, notification removed.
- Bluetooth disconnect mid-session → no crash; user can stop/start again.
- Speaker feedback protection works (blocked or gated).

## 7) PowerShell (Windows)
```powershell
.\gradlew clean
.\gradlew test
.\gradlew assembleDebug
```
