# Audio Clarity

**Audio Clarity** is a robust, feature-rich Android application designed to enhance and clarify real-world sounds in real-time. It acts as a powerful listening-assist tool, capturing microphone audio, applying a sophisticated set of digital signal processing (DSP) effects, and playing the result back to your headphones.

Built with a clean architecture and a focus on stability, this app is both a powerful utility and a demonstration of advanced Android audio processing techniques.

---

## Core Features

*   **Real-Time Audio Passthrough:** The core of the app. It provides a live, low-latency audio stream from the microphone to your headphones, running reliably in the background as a foreground service.

*   **Adaptive "Auto-Clarity" Mode:** A smart, automated mode that intelligently enhances speech.
    *   **Voice Activity Detection (VAD):** Automatically detects the presence of human speech in the audio stream.
    *   **Pitch Tracking:** When speech is detected, it finds the fundamental frequency (pitch) of that specific voice.
    *   **Dynamic Parametric EQ:** Applies a custom-tuned EQ filter to boost the precise frequencies of the detected voice, making it "pop" from the background noise.

*   **"Scout Mode" Instant Replay:** Never miss a thing. 
    *   The app continuously records the last 5 seconds of audio into an in-memory ring buffer.
    *   With a single tap on the "Replay" button (in-app or from the notification), you can instantly hear a boosted version of the last 5 seconds.

*   **Advanced Manual DSP Controls:** For users who want fine-grained control, the app offers a suite of manual toggles:
    *   **Adjustable Gain:** Amplify sound with a simple slider.
    *   **High-Pass Filter (HPF):** A clarity filter that cuts low-frequency rumble.
    *   **Noise Gate:** Automatically mutes the output during quiet moments to eliminate background hiss.
    *   **System Effects:** Integrates with Android's built-in **Noise Suppressor (NS)**, **Acoustic Echo Canceler (AEC)**, and **Automatic Gain Control (AGC)**.

*   **Safety and Stability:**
    *   **Hard Limiter:** A safety feature that is always active to prevent sudden loud noises or high gain settings from causing painfully loud audio spikes.
    *   **Speaker Feedback Protection:** The app automatically stops audio passthrough if you disconnect your headphones, preventing a loud and dangerous feedback loop.
    *   **Persistent Settings:** All your preferred settings are saved and automatically restored the next time you open the app.

*   **Developer-Focused:**
    *   **Diagnostics Screen:** A dedicated screen displays live technical data for debugging and analysis, including the current audio output device, sample rate, buffer size, and live pitch detection frequency.
    *   **Detailed Logging:** The app uses a custom logger to provide detailed insight into session states, audio parameters, and errors.

---

## ⚠️ Safety Warning

**Always use headphones or earbuds.**

This application is designed for use with headphones. Running it with the phone's built-in speaker will cause the microphone to pick up its own output, creating an immediate and potentially damaging high-volume feedback loop.

---

## How to Build and Run

1.  Open the project in a recent version of **Android Studio**.
2.  Connect a physical Android device.
3.  Build and run the app.
4.  Grant the required **Microphone** and **Notifications** permissions when prompted.

---

## How to Use the App

1.  Connect your headphones (Bluetooth or wired).
2.  Open the Audio Clarity app.
3.  Tap **Start Audio Passthrough** to begin the session.
4.  **Adjust Gain:** Use the slider to increase or decrease the volume.
5.  **Manual Mode:** Use the toggles to manually enable the High-Pass Filter, Noise Gate, or other system effects.
6.  **Auto-Clarity Mode:** Enable the "Auto-Clarity" switch. The other DSP toggles will be disabled, and the app will automatically enhance speech for you.
7.  **Replay:** Tap the **Replay Last 5s** button to hear a boosted version of what just happened. This can also be done from the notification.
8.  **Stop:** Tap the **Stop** button in the app or in the persistent notification to end the session.

---

## Project Architecture

This project follows a clean, agent-based architecture designed for stability and maintainability. For a detailed breakdown of the components, their responsibilities, and the threading model, please see **`AGENTS.md`**.
