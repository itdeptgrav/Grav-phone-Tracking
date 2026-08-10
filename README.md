# Personal Call Recorder (Android)

A **personal-use**, sideload-only Android app that records your phone calls (best
effort — see the hard limits below), stores them locally with contact/number/
date/duration metadata, and is architected to later transcribe and AI-summarize
them via your own backend. **Not for the Play Store.**

> ⚠️ **Read "The honest Android limitation" first.** On a normal, non-rooted,
> modern Android phone, a third-party app **cannot** capture the other party's
> voice from a cellular call. This app is honest about that and implements the
> best legitimate fallback (microphone recording), clearly labeled.

---

## The honest Android limitation

Third-party apps **cannot** access the internal two-way audio of a cellular call
on modern Android:

- `AudioSource.VOICE_CALL`, `VOICE_DOWNLINK`, `VOICE_UPLINK` are **blocked** for
  non-system apps (they throw or return silence).
- Only `AudioSource.MIC` / `VOICE_COMMUNICATION` are available, and those capture
  the **local microphone only**.
- Additionally, on **Android 12+** an app generally **cannot start a
  microphone foreground service from the background** (e.g. from a phone-state
  broadcast) without a special exemption. This means fully-automatic recording
  is unreliable on stock Android and **may be blocked by the OS** — the app
  surfaces this as a failed call rather than pretending it worked.

### What this app actually does

| Mode | What | Status |
|------|------|--------|
| **A — Direct call audio** | True two-way internal audio | ❌ Not possible without root/system privileges |
| **B — Microphone** | Records the mic; captures the far party **only on speakerphone** | ✅ Implemented (the working default) |
| **C — OEM integration** | Manufacturer native call recording | 🔌 Abstraction only (`OemRecordingStrategy`) |
| **D — Privileged/root** | System/root capture of real call audio | 🔌 Abstraction only (`PrivilegedRecordingStrategy`) |

The in-app **Diagnostics** screen reports exactly what your specific device
supports — it never claims a capability it can't verify.

**Practical tip:** put the call on **speakerphone** to capture both sides via the
microphone.

---

## What's included

- Automatic call detection (incoming/outgoing/ringing/active/ended) via
  `PHONE_STATE`, with direction inference and call-log reconciliation.
- Foreground recording service with the mandatory recording notification
  (title, contact, live timer, **Stop** action).
- Microphone recording strategy (AAC in an `.m4a` container; Standard/High presets).
- Room database of call records + LIKE-based search across name/number/
  transcript/summary/notes.
- Material 3 Jetpack Compose UI: home call history, call detail with audio
  player (play/pause/seek/±10s), search, settings, diagnostics.
- Contact name resolution (optional `READ_CONTACTS`).
- Vendor-neutral **transcription** and **AI-summary** provider interfaces
  (disabled by default — nothing is uploaded).
- Privacy-first: app-private storage, no analytics/ads/telemetry, backups
  disabled, optional biometric lock, first-run consent notice.

---

## Requirements

- **Android 8.0 (API 26) → Android 14 (API 34+)**  (`minSdk 26`, `targetSdk 34`, `compileSdk 35`)
- Android Studio (Ladybug/Koala or newer recommended) — provisions the SDK automatically.
- JDK 17 (bundled with recent Android Studio).

---

## Architecture

```
app/src/main/java/com/personal/callrecorder/
├── call/          CallStateMonitor, PhoneStateReceiver, CallSession, CallLogResolver
├── recording/     RecordingStrategy (+Microphone/Oem/Privileged), RecorderManager, RecordingService
├── contacts/      ContactResolver
├── data/
│   ├── entity/    CallRecord, RecordingStatus, ProcessingStatus
│   ├── dao/       CallDao
│   ├── database/  CallDatabase, Converters
│   ├── repository/CallRepository
│   └── settings/  SettingsRepository, AppSettings (DataStore)
├── transcription/ TranscriptionProvider (+Disabled), TranscriptionRepository
├── ai/            AiSummaryProvider (+Disabled), CallSummary, AiRepository
├── capability/    CapabilityDetector, DeviceCapabilities
├── di/            Hilt modules (Database, App, Ai)
├── ui/            home, callDetails, search, settings, diagnostics, onboarding, permissions, player, theme, navigation
└── util/          StorageManager, Formatters, TimeProvider, BiometricAuthenticator
```

- **MVVM** with Hilt DI, Kotlin Coroutines/Flow, Room, DataStore, Media3 playback.
- The recording layer is fully abstracted behind `RecordingStrategy`, so the rest
  of the app is unaware of *how* audio was captured — new modes plug in without
  touching UI or data code.

### How call detection works

`PhoneStateReceiver` (a manifest-registered receiver for the still-allowed
`PHONE_STATE` implicit broadcast) forwards normalized states to the singleton
`CallStateMonitor`, which infers direction:

```
IDLE → RINGING → OFF_HOOK  ⇒ INCOMING (answered)
IDLE → OFF_HOOK            ⇒ OUTGOING
IDLE → RINGING → IDLE      ⇒ missed incoming (not recorded)
```

On modern Android the number is often withheld from the broadcast, so it is
reconciled against the call log at call-end (`CallLogResolver`, requires
`READ_CALL_LOG`).

### How recording works

On call answer, `CallStateMonitor` starts `RecordingService` (foreground,
`microphone` type). The service creates a `CallRecord`, drives `RecorderManager`
→ `MicrophoneRecordingStrategy` (`MediaRecorder`, AAC/`.m4a`), and shows the
recording notification. On call end it stops, validates the file (guards against
0-byte/too-short/corrupt audio), and finalizes the record with a status of
`COMPLETED` / `NO_AUDIO` / `FAILED`. If the OS blocks the background FGS start,
the call is logged as `FAILED` with the reason — never a silent crash.

---

## Permissions (only what's used)

| Permission | Why | Required? |
|---|---|---|
| `RECORD_AUDIO` | Record microphone audio | Yes |
| `READ_PHONE_STATE` | Detect call start/end | Yes |
| `POST_NOTIFICATIONS` (13+) | Mandatory recording notification | Yes (13+) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE` (14+) | Hold the mic during a call | Yes |
| `READ_CONTACTS` | Show caller's saved name | Optional |
| `READ_CALL_LOG` | Recover withheld numbers | Optional |

Each is requested at runtime with an explanation; denial is handled gracefully.

---

## Build the APK

Open in Android Studio (**recommended**):

1. `File → Open…` → select the `PersonalCallRecorder` folder.
2. Let Studio sync (it downloads the SDK/Gradle as needed). If prompted that the
   **Gradle wrapper** is missing, accept Studio's offer to create it, or run
   `gradle wrapper --gradle-version 8.9` once if you have Gradle installed.
3. `Build → Build App Bundle(s) / APK(s) → Build APK(s)`.

Or from the terminal (needs JDK 17 + Android SDK; set `ANDROID_HOME`):

```bash
./gradlew assembleDebug
```

> Note: this repo intentionally does **not** ship the binary
> `gradle/wrapper/gradle-wrapper.jar`. Android Studio regenerates it on first
> sync; for a pure-terminal build, run `gradle wrapper --gradle-version 8.9`
> first (or copy the jar from any Gradle 8.9 install).

Output:

```
app/build/outputs/apk/debug/app-debug.apk
```

## Install (sideload) on your phone

1. Enable **Settings → About phone → tap Build number 7×** to unlock Developer options.
2. Enable **USB debugging** (or **Install unknown apps** for your file manager).
3. `adb install app/build/outputs/apk/debug/app-debug.apk`  — or copy the APK to
   the phone and open it.
4. Grant Microphone + Phone permissions when asked.
5. For the best shot at automatic recording, also grant the app any
   **"Appear on top" / battery-unrestricted / autostart** options your OEM offers,
   and expect that stock Android 12+ may still block background mic FGS starts.

## Where recordings are stored

App-private internal storage (not world-readable, removed on uninstall):

```
<app files>/recordings/YYYY/MM/call_YYYY-MM-DD_HHmmss.m4a
```

To retrieve them: `adb exec-out run-as com.personal.callrecorder tar c files/recordings > recordings.tar`
(works on debuggable builds), or add an export feature later.

---

## Future integration (architecture is ready, nothing is wired to the internet)

### Transcription (Whisper)
Implement `TranscriptionProvider` and bind it in `di/AiModule.kt`:
- `WhisperLocalProvider` — on-device (whisper.cpp / TFLite)
- `WhisperApiProvider` — OpenAI-compatible Whisper endpoint
- `BackendTranscriptionProvider` — your own CMS backend

`TranscriptionRepository` already handles reading the audio file, updating
status, and persisting the transcript.

### AI summaries (Qwen via Ollama, behind your backend)
Implement `AiSummaryProvider` (returns the structured `CallSummary`) and bind it
in `di/AiModule.kt`. Intended, **backend-mediated** path:

```
Android app  →  your CMS backend (auth)  →  Ollama  →  Qwen
```

**Never** point the app directly at Ollama on the public internet — always go
through your authenticated backend.

### CMS sync
`CallRepository` is the single source of truth and is deliberately thin so a sync
layer can observe/replay operations and push completed calls (recording +
transcript + `CallSummary` JSON) into a customer timeline. Not built yet.

---

## Privacy

- Local-first: recordings live in app-private storage.
- No analytics, ads, tracking, or telemetry SDKs.
- No automatic uploads — transcription/AI are **disabled by default** and only run
  when you configure and enable a provider.
- Cloud/device backups disabled (`allowBackup=false` + extraction rules).
- Optional biometric (or device-credential) lock.
- First-run consent notice; you are responsible for complying with local
  call-recording/consent laws.

## License / use

Personal use only. You are responsible for lawful use, including obtaining any
consent required in your jurisdiction.
