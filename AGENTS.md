# Audio Clarity — AGENTS.md
System Agents & Architecture (Android)

## 0) Product Intent
Audio Clarity is a **foreground listening-assist app** designed to enhance and clarify real-world sounds in real-time. It provides several powerful audio processing features that are fully configurable by the user.

- Captures microphone audio.
- Applies an advanced, multi-stage DSP chain for clarity.
- Plays the result to a selected audio output (e.g., headphones).
- Continues to run as a foreground service when the app is in the background.

## 1) Core Features (Implemented)

*   **Real-Time Audio Passthrough:** The core feature of the app, providing a live, processed audio stream from the microphone to the user's headphones.

*   **Adaptive "Auto-Clarity" Mode:** A smart, automated mode that:
    *   Uses a **Voice Activity Detector (VAD)** to identify when someone is speaking.
    *   When speech is detected, it engages a **Pitch Detector** to find the fundamental frequency of that specific voice.
    *   It then applies a dynamic **Parametric EQ** to boost that precise frequency, making that voice clearer and more prominent.
    *   When speech stops, the effect is automatically disengaged.

*   **"Scout Mode" Instant Replay:**
    *   The app continuously records the last 5 seconds of audio into a ring buffer.
    *   The user can tap a "Replay" button at any time to instantly play back a boosted version of what they just missed. This works from both the app and the lock screen notification.

*   **Advanced DSP Chain:** Users have manual control over a sophisticated set of audio effects:
    *   **Adjustable Gain:** Amplify incoming audio.
    *   **High-Pass Filter (HPF):** Cuts low-frequency rumble to improve speech intelligibility.
    *   **Noise Gate:** Automatically silences the output during quiet moments to eliminate background hiss.
    *   **System Effects:** Integrates with Android's built-in **Noise Suppressor (NS)**, **Acoustic Echo Canceler (AEC)**, and **Automatic Gain Control (AGC)**.
    *   **Safety Limiter:** A hard limiter is always active to prevent loud noises or high gain from causing painfully loud output.

*   **Robust Session Management:**
    *   **Foreground Service:** All audio processing happens in a foreground service, allowing the app to run reliably in the background or with the screen off.
    *   **Speaker Feedback Protection:** Automatically pauses audio if headphones are disconnected, preventing loud and unpleasant speaker feedback.
    *   **Persistent Settings:** The app saves and restores all user-configured settings between sessions.

*   **Diagnostics & Logging:**
    *   **Diagnostics Screen:** A dedicated screen displays live technical data, including the current audio device, supported system effects, and the real-time detected pitch.
    *   **Diagnostic Logging:** The app logs detailed information about session states, audio parameters, and errors to aid in debugging.

## 2) Safety & Compliance
-   **Foreground Visibility:** The app's active state is always visible via a persistent notification.
-   **No Hidden Behavior:** There are no features that could be construed as enabling spying or stealth recording.
-   **Minimal Permissions:** Requires only `RECORD_AUDIO` and `POST_NOTIFICATIONS` (on newer Android versions).

## 3) Architectural Agents (Components)

*   **UI/Control Agent (`MainActivity`, `DiagnosticsActivity`):** Provides the user interface for controlling the app and viewing diagnostic data. Binds to the `AudioService` to control the audio session.

*   **Session Orchestrator Agent (`AudioService`):** Owns the audio session lifecycle, manages the foreground service, and handles audio routing and resource management. It exposes its state via `LiveData`.

*   **Audio Capture/Playback Agents:** Responsibilities are managed within the `AudioService`'s dedicated audio thread. This includes configuring and reading from `AudioRecord` and writing to `AudioTrack`.

*   **System Pre-Processing Agent (`AudioFxPreproc`):** Integrated into `AudioService`, this agent manages the Android-provided audio effects (AGC, NS, AEC).

*   **DSP Agent (`DspChain`):** Contains the complete, app-owned signal processing chain, including the HPF, dynamic EQ, gain, noise gate, and limiter.

*   **Specialized DSP Agents (`VoiceActivityDetector`, `PitchDetector`):** These classes contain the specific logic for detecting speech and estimating pitch, respectively. They are owned and used by the `AudioService`.

*   **Notification/Controls Agent:** Implemented within the `AudioService`, this is responsible for the persistent notification, which includes actions for stopping the service or triggering an instant replay.

*   **Settings Agent (`SettingsRepository`):** A simple class using `SharedPreferences` to persist all user settings.

*   **Diagnostics Agent (`DiagLogger`):** A static logger class used throughout the app to record diagnostic information.

## 4) Threading Model
-   **Main Thread:** UI and user interaction only.
-   **Audio Thread:** A dedicated, high-priority thread inside the `AudioService` runs the tight `read -> process -> write` loop.
-   **Replay Thread:** Replay playback occurs on a separate, short-lived thread to avoid blocking the main audio thread.

## 5) State Machine
-   The `AudioService` follows a strict state machine: `IDLE` ↔ `STARTING` ↔ `RUNNING` ↔ `STOPPING`.
-   State transitions are exposed to the UI via `LiveData`.

## 6) Acceptance Tests (Manual)
-   [x] Session starts and stops correctly from the UI.
-   [x] Audio plays to headphones and continues when the app is backgrounded.
-   [x] Speaker feedback protection correctly stops audio when headphones are disconnected.
-   [x] Gain, HPF, and Noise Gate toggles work as expected in manual mode.
-   [x] Enabling "Auto-Clarity" disables manual controls and automatically engages HPF/EQ during speech.
-   [x] The "Replay" button (from app and notification) correctly plays back the last 5 seconds of audio.
-   [x] The Diagnostics screen displays accurate, live data.
-   [x] All settings are correctly saved and restored when the app is restarted.
