# Cortex

Cortex is a Kotlin/Jetpack Compose Android companion app (package `com.cortex.assistant`) with a custom liquid-glass-inspired UI, local Room chat history, encrypted Gemini key storage, Android speech recognition and text-to-speech, Gemini REST requests, exponential retry for quota/network failures, and confirmed phone-action drafts.

The project uses free/open-source Android tooling and libraries. Gemini usage is governed by Google's current free-tier limits and terms; it is not an unlimited offline service. No API key is included. **Never enter passwords, OTPs, banking credentials, or other secrets.** Content sent to Gemini may be used by Google to improve its products under applicable free-tier terms.

## Build from GitHub (free)

1. Create a new repository on GitHub (for example, `Cortex`).
2. Unzip this project and push the contents of the `Cortex` folder to the repository's default branch. For example:
   ```sh
   git init
   git add .
   git commit -m "Add Cortex Android app"
   git branch -M main
   git remote add origin https://github.com/YOUR_ACCOUNT/Cortex.git
   git push -u origin main
   ```
3. Open the repository's **Actions** tab and wait for **Build Cortex APK** to finish successfully.
4. Open the completed workflow run, find **Artifacts**, and download **cortex-debug-apk**.
5. Extract `app-debug.apk` and install it on your Android phone. Android may ask you to allow installation from your browser or file manager; enable that only for the installer you use.
6. Open Cortex. Enter a name if desired and paste your own key created at [Google AI Studio](https://aistudio.google.com/app/apikey). You can also add the key later in Settings.
7. Grant microphone access when you tap the talk button. Typed chat works without microphone access.
8. If you want optional screen-reading/scroll actions, open **Settings → Open accessibility settings → Cortex** and enable it yourself. Android displays its own persistent accessibility warning. Disable the service at any time in Android Settings.

No Play Store account or fee is needed. Android Studio can also open the project and build `:app:assembleDebug` locally with JDK 17 and Android SDK platform 35 installed.

## Current implementation and boundaries

- **AI:** `GeminiClient.kt` contains the single model constant `GEMINI_MODEL`. It calls Gemini's REST `generateContent` endpoint, sends function declarations, retries quota/server/network errors with exponential delay, and supplies a small rules-based local fallback. That fallback is not a local language model and cannot provide full AI answers without Gemini.
- **Memory:** chat history is stored locally with Room. The API key is stored using AndroidX EncryptedSharedPreferences backed by Android Keystore. “Forget everything” clears both local history and saved settings.
- **Speech:** Android's installed speech-recognition provider handles tap-to-talk. Android TextToSpeech speaks responses. Exact offline/online recognition behavior depends on the phone's speech provider.
- **Actions:** actions are presented in a confirmation dialog. Calls open the dialer; SMS and WhatsApp launch drafts for review rather than sending. Alarms and timers open the system Clock confirmation UI. App opening uses a best-effort installed-app label match. Some less common Gemini tools are safely declined unless supported by the optional accessibility service.
- **Accessibility safety:** password nodes and likely banking/OTP app screens are blocked from screen reading; secure focused fields are never typed into. These heuristics cannot perfectly identify every sensitive screen. The user must enable the service explicitly. A visible short toast appears when the service acts.
- **Not bundled:** this source does not include Gemma, Vosk, or Piper model files or their native runtimes. The wake-word service is deliberately inert and does not record audio; wake word remains off. There is no fabricated “offline Gemma” mode. Advanced AGSL edge refraction, full RenderEffect blur, sensor parallax, haptic/motion tuning, and amplitude-synchronized audio visualizations are not part of this initial implementation; the visual treatment uses animated Compose gradients and a drawn metallic orb, so it degrades gracefully on API 26–35.

## Privacy and permissions

Cortex requests microphone permission only when you tap the mic. Gemini sends your prompt to Google over HTTPS. Local message history stays in the app's Room database unless the device/app is compromised or you choose to erase it. The optional AccessibilityService can inspect visible UI text to support user-requested actions; do not enable it unless you understand and want that access. It is not a substitute for a security boundary—never use Cortex with credentials, OTPs, or financial screens.

## Build troubleshooting

- **`SDK location not found`:** install Android SDK platform 35 and Build Tools through Android Studio, then set `ANDROID_HOME` (or `local.properties` with `sdk.dir=...`). Do not commit `local.properties`.
- **JDK / Gradle errors:** use JDK 17. The CI workflow installs Temurin JDK 17 and Gradle automatically.
- **Missing `gradle-wrapper.jar`:** this repository has wrapper scripts and wrapper properties. If the binary wrapper JAR was excluded by a source-transfer system, regenerate it from the project root with `gradle wrapper --gradle-version 8.9` (requires Gradle installed), then run `./gradlew :app:assembleDebug`.
- **Dependency download failure:** verify network access to Google Maven, Maven Central, and `services.gradle.org`, then retry. No paid dependency or API is required to compile.
- **Gemini HTTP 400/401:** check that the pasted key is valid, enabled for the Generative Language API, and has no leading/trailing whitespace. Keys are stored encrypted locally and are not part of this repository.
- **HTTP 429 / `RESOURCE_EXHAUSTED`:** free quota is limited. Cortex backs off and switches to its basic local fallback; wait for quota reset or check Google AI Studio.
- **Speech unavailable:** check Android microphone permission and ensure the device has a speech-recognition provider. Typed chat remains available.
- **Accessibility action unavailable:** enable Cortex in Android's Accessibility settings; some actions are intentionally unsupported or blocked for safety.

## License and cost

This project is provided as source for personal use. The included build dependencies are free/open-source. Gemini is an external Google service with a free tier whose quotas and terms can change. Sideloading the debug APK avoids Play Store distribution fees.
