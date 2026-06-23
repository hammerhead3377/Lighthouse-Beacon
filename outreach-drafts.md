# Lighthouse Beacon — Outreach Drafts
*Session 9. Thomas reviews, approves before posting.*

---

## Track A — Craft (Android / #AndroidSpotlight)

**Platform:** X/Mastodon, Android dev circles, #AndroidDev #AndroidSpotlight

---

Just shipped **Lighthouse Beacon** to the Play Store — a native Android AI voice client built entirely in Jetpack Compose.

What it does: tap once, speak, get a spoken response back. Clean. Fast. No typing.

Under the hood it's proper Compose architecture — ViewModel-driven state machine with distinct stages (warming, listening, connecting, speaking), Room-backed interaction log, and a settings screen that actually saves between sessions. Full Bluetooth mic support with tuned sensitivity params for wearable microphones — tested on Ray-Ban Meta glasses.

The backend is self-hosted. The app doesn't know or care what model answers your question — it sends your voice, gets a response, speaks it back. Completely swappable.

Roadmap: wake-word trigger (always-on mode), streaming TTS for lower latency, lock-screen widget for true hands-free operation.

Built this because I wanted a voice interface that felt like native Android, not a WebView wrapper. Compose made it possible.

#AndroidDev #JetpackCompose #AndroidSpotlight #MobileAI

---

## Track B — Thesis (Sovereign / HN / Nostr)

**Platform:** Hacker News (Show HN), Nostr, sovereign tech communities

---

**Show HN: Lighthouse Beacon — voice-to-AI over SSH, no cloud required**

The pitch is simple: speak into your phone, get a response from your own hardware, hear it spoken back. No API keys. No subscription. No data leaves your network.

The architecture is what makes it interesting. The Android app handles capture and playback. SSH is the transport. Whatever you're running on your own hardware — the app calls it, gets a response, and speaks it. The inference backend is entirely yours to choose and change.

It handles mesh networking automatically. If you're home on LAN, it routes locally. Leave the house, it falls back to your Tailscale address without any manual switching. The app doesn't require you to think about topology.

Continuous loop mode turns it into a voice-first interface — speak, listen, speak again, no touch required. That's the target state for wearable use: Ray-Ban glasses as mic, phone in pocket, AI reachable by voice from anywhere you have mesh coverage.

Privacy properties: no cloud STT (uses Android's on-device recognizer), no cloud TTS (device speech engine), no external API surface. The only outbound connection is your SSH tunnel to your own server.

Current state: Play Store release, stable. Roadmap includes wake-word trigger and streaming response for sub-second latency.

The thing I kept running into was that every "AI voice" app is either a cloud product or a demo. This is neither — it's infrastructure.

---

*Both drafts: no repo link, no code, no infra specifics. Thomas approves before any post.*
