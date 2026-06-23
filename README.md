# Lighthouse Beacon

Sovereign voice link for Android. Captures audio from Ray-Ban wearables (or any mic), streams it over SSH to a private Jetson server running `claw-agent`, and plays the response back — no cloud, no subscriptions, no data leaving your hardware.

---

## What It Does

1. **Capture** — Android app listens via connected Bluetooth mic (Ray-Ban Meta or device mic).
2. **Tunnel** — Audio piped over SSH to `hammerhead7017@<jetson-host>`.
3. **Infer** — `claw-agent` on the Jetson runs the request through DeepSeek-R1:8b (local Ollama).
4. **Return** — Response audio streamed back, played on device.

No API keys. No Gemini. No Google. Thomas's stack, Thomas's rules.

---

## Requirements

- Android Studio (Hedgehog or later)
- Physical Android device or emulator (API 26+)
- SSH access to a running Jetson with `claw-agent` configured
- Ray-Ban Meta glasses (optional — device mic works too)

---

## Setup

```bash
# Clone
git clone <repo-url> LighthouseBeacon
cd LighthouseBeacon
```

Copy `.env.example` to `.env` and fill in your Jetson host details:

```
JETSON_HOST=your.jetson.ip.or.hostname
JETSON_USER=your_ssh_user
```

Open in Android Studio → **Open** → select the project directory → let Gradle sync.

Run on device or emulator.

---

## Security

- `local.properties`, `.env`, `*.jks`, `*.keystore` are all gitignored — keep them that way.
- SSH password auth. Credentials stored via `.env` → `BuildConfig` at build time, never committed.
- Signing key (`lighthouse-upload.jks`) stays local, never committed.

---

## Project Structure

```
app/                  Android app source
assets/               Static assets
build.gradle.kts      Root Gradle config
play-upload.py        Google Play upload automation
PRIVACY.md            Privacy policy (Play Store requirement)
```

---

## Deployment

Play Store upload handled by `play-upload.py` using service account credentials stored in `local.properties`. Build a release APK/AAB in Android Studio, then:

```bash
python3 play-upload.py
```

---

*Part of the Patricia sovereign AI stack. Session 9.*
