# Beat This — Product Specification

## Vision

**Beat This** is an open-source, professional-grade Android DAW that combines the best elements of existing commercial DAWs while adopting the AAP (Audio Plugins For Android) open plugin standard. Voice-controlled, AI-powered music production for everyone. The goal is to be the first serious, community-driven DAW on Android that supports third-party plugins via an open format — filling the gap that no commercial DAW has addressed.

---

## Architecture

### Platform Requirements
- Android 10+ (API 29) — required for NdkBinder (AAP plugin hosting)
- Android 11+ for plugin GUI embedding
- Target: phones, tablets, Chromebooks
- Language: Kotlin (UI/app layer) + C/C++ (audio engine via NDK)
- Build: Gradle + CMake (native)
- License: MIT or Apache 2.0

### Minimum Hardware Baseline
- 4GB RAM
- 4-core CPU @ 2.5GHz+
- Adreno GPU (2022 or newer — Adreno 619+ class)
- This baseline comfortably runs Whisper.cpp (tiny/base) + Piper TTS on-device
- Gemma E4B (4-5GB) is tight at 4GB RAM — remains opt-in for 6GB+ devices

### Audio Engine (Native/C++)
- Real-time audio processing via AAudio (preferred) / OpenSL ES (fallback)
- Shared memory (ashmem) buffer passing for plugin communication
- Sample rates: 44.1, 48, 96 kHz
- Bit depth: 16, 24, 32-bit float internal processing
- Buffer size configurable (64–2048 samples)
- USB audio interface support (class-compliant, multi-channel I/O)
- Low-latency path bypassing standard Android audio where possible

### Plugin System (AAP Integration)
- First-class AAP host — discover, instantiate, and communicate with AAP plugins via Binder IPC
- Plugin metadata scanning from `aap_metadata.xml` (no instantiation needed)
- MIDI 2.0 UMP for parameter changes and note data to plugins
- Plugin GUI embedding (native View on Android 11+, WebView fallback)
- Extension support: state, presets via AAPXS
- Expose host as AAP-compatible so other hosts can load our instruments

---

## UI/UX Design

### Design Philosophy
- Touch-first, gesture-driven (not a shrunken desktop UI)
- Adaptive layout: phone (compact), tablet (full), Chromebook (keyboard+mouse)
- Dark theme default, light theme option
- Minimal chrome — maximize workspace area
- Slide-in/out panels (inspired by Cubasis 3)
- Large tap targets (minimum 48dp)
- Undo/redo always accessible (unlimited history)

### Primary Views

| View | Description |
|------|-------------|
| **Arrangement** | Linear timeline with multitrack audio + MIDI clips. Horizontal scroll = time, vertical scroll = tracks. Pinch to zoom both axes. |
| **Pattern Editor** | Optional pattern-based workflow (FL Studio-style). Patterns placeable on arrangement timeline. |
| **Piano Roll** | MIDI note editing. Velocity bars below. Automation lanes expandable. Quantize, humanize tools. Step recording mode. |
| **Mixer** | Channel strips: level fader, pan, mute/solo, 8 insert slots, 4 send slots. Group/bus routing. Master bus. Pinch-zoom strip width. |
| **Drum Pad / Step Sequencer** | Grid-based beat programming. Tap pads or toggle steps. Triplet support. Custom sample assignment per pad. |
| **Audio Editor** | Waveform view. Cut, copy, paste, fade in/out, normalize, reverse, time-stretch, pitch-shift. Non-destructive. |
| **Plugin Browser** | Scan installed AAP plugins. Filter by type (instrument/effect), category, developer. Tap to instantiate. |
| **File Browser** | Import audio (WAV, MP3, FLAC, OGG, AIFF), MIDI, project files. Cloud storage integration (Google Drive). |
| **Home / Project Manager** | Recent projects, templates, new project wizard. |

### Navigation
- Bottom tab bar (phone): Arrangement | Mixer | Instruments | Browser
- Side rail (tablet): same sections as expandable panel
- Transport bar always visible: play, stop, record, loop, metronome, tempo, time signature
- Swipe gestures: left/right between views, long-press for context menus
- Keyboard shortcuts when hardware keyboard connected

---

## Core Features

### Recording
- Multitrack audio recording (simultaneous multi-input via USB interface)
- MIDI recording from external USB/Bluetooth controllers
- Punch-in/punch-out recording
- Count-in (1, 2, or 4 bars)
- Input monitoring with configurable latency compensation
- Take/comp lanes (record multiple takes, composite best parts)

### MIDI
- Unlimited MIDI tracks
- Piano roll with velocity, note length, CC editing
- Automation lanes per parameter
- Step recording mode
- Quantize (1/4 to 1/64, triplets, swing)
- MIDI learn for external controllers
- USB MIDI and Bluetooth MIDI support
- MIDI 2.0 UMP internally (future-proof, AAP-native)
- Chord helper / scale lock mode

### Audio
- Unlimited audio tracks
- Non-destructive editing (cuts, fades, moves)
- Time-stretching (independent tempo/pitch)
- Pitch-shifting
- Audio-to-MIDI conversion (basic monophonic)
- Vocal tuning (pitch correction with per-note control)
- Normalize, reverse, gain adjustment
- Crossfade between adjacent clips

### Mixing
- Per-track: volume, pan, mute, solo, record-arm
- 8 insert effect slots per track
- 4 send slots per track → send/return buses
- Group/bus tracks (submix routing)
- Master bus with inserts
- Sidechain routing (compressor, gate)
- Full automation on all mixer parameters (draw or record)
- Metering: peak, RMS, LUFS on master

### Automation
- Per-parameter automation lanes
- Draw mode (freehand, line, curve)
- Record mode (capture knob movements in real-time)
- Automation on: volume, pan, mute, all effect parameters, instrument parameters
- Snap to grid (configurable resolution)

### Instruments (Built-in)
- **Subtractive Synth**: 2 oscillators, filter, 2 envelopes, 2 LFOs, effects section
- **Sample Player**: Soundfont/SFZ support, multi-sample mapping
- **Drum Machine**: 16 pads, per-pad sample/tuning/envelope/filter, pattern sequencer
- **Wavetable Synth**: basic wavetable with morph control (stretch goal)

### Effects (Built-in)
- EQ (parametric 4-band + high/low shelf)
- Compressor (with sidechain input)
- Limiter
- Reverb (algorithmic)
- Delay (sync to tempo, ping-pong option)
- Chorus
- Phaser / Flanger
- Distortion / Saturation
- Noise Gate
- De-esser
- Stereo Width
- Filter (LP/HP/BP with resonance)

### Project Management
- Auto-save with configurable interval
- Project templates
- Tempo and time signature changes (including gradual tempo ramps)
- Markers / arrangement markers
- Track color coding and naming
- Track folders (collapse/expand groups)

### Export
- Mixdown: WAV (16/24/32-bit), MP3, FLAC, OGG, AAC
- Stem export (per-track or per-group)
- MIDI export
- Project file format (open, documented JSON + audio assets)
- Share via system share sheet

### Hardware Integration
- USB audio interfaces (class-compliant, multi-channel)
- USB MIDI controllers
- Bluetooth MIDI (with latency warning)
- External keyboard shortcuts
- Ableton Link support (multi-device tempo sync)

---

## Plugin Ecosystem (AAP)

### As Host
- Discover all installed AAP instrument and effect plugins
- Load into insert slots or as track instruments
- Display plugin GUI within DAW (embedded native View or WebView)
- Save/restore plugin state with project
- Preset browsing via AAP presets extension
- Parameter automation via MIDI 2.0 UMP

### As Plugin Provider
- Ship built-in instruments/effects as standalone AAP services
- Other AAP hosts can use our synths/effects independently
- Metadata exposed via `aap_metadata.xml`

### Plugin Manager
- List all installed AAP plugins with metadata
- Show plugin info: ports, parameters, developer
- One-tap install suggestions (link to APK sources)

---

## AI Services

### Design Principles
- **Cloud-first default**: All AI routes through Pollinations free API out of the box (zero setup)
- **On-device is opt-in**: Users manually enable local models in Settings when they want offline/lower-latency
- **No paid APIs required**: Everything works at $0 cost
- **Voice as primary AI input**: speak commands to control the DAW hands-free

### Default Configuration (Settings)

```
AI Processing: Cloud (Pollinations)  ← default
  ☐ On-device STT (Whisper.cpp) — requires ~150MB download
  ☐ On-device LLM (Gemma E4B) — requires ~4GB download
  ☐ On-device TTS (Piper) — requires ~30MB download
```

When all toggles are off (default), the full pipeline is:
```
Voice → Pollinations whisper (cloud STT)
  → Pollinations openai (cloud LLM + function calling)
    → DAW executes
    → Pollinations qwen-tts (cloud TTS feedback)
```

This simplifies the dev loop — we build and test against one API surface (Pollinations REST) first, then add on-device as a later optimization toggle.

### On-Device Stack (Free, Offline, Private)

| Component | Technology | Size | Purpose |
|-----------|-----------|------|---------|
| **Speech-to-Text** | Whisper.cpp (MIT) via NDK | ~40MB model | Real-time voice → text on-device |
| **Intent / Orchestration** | Gemma 4 E4B via LiteRT-LM / MediaPipe | ~4-5GB (INT4) | Parse voice commands → structured DAW function calls |
| **Text-to-Speech** | Piper TTS (MIT) via NDK | ~30MB per voice | Spoken feedback, vocal generation |

### Pollinations Cloud API (Free Tier, No Auth for Listing)

Base URL: `https://gen.pollinations.ai` — OpenAI-compatible endpoints.

#### Audio Services

| Endpoint | Model | Capability |
|----------|-------|-----------|
| `GET /audio/{text}?model=elevenlabs` | ElevenLabs v3 | High-quality TTS (70+ languages, emotional) |
| `GET /audio/{text}?model=elevenflash` | ElevenLabs Flash v2.5 | Ultra-low latency TTS (~75ms) |
| `GET /audio/{text}?model=qwen-tts` | Qwen3-TTS Flash | Fast multilingual TTS |
| `GET /audio/{text}?model=qwen-tts-instruct` | Qwen3-TTS Instruct | TTS with emotion & style control |
| `GET /audio/{prompt}?model=elevenmusic` | ElevenLabs Music | Generate studio-grade music from text prompts |
| `GET /audio/{prompt}?model=acestep` | ACE-Step 1.5 Turbo | Open-source music gen with lyrics support |
| `POST /v1/audio/speech` | Any TTS model | OpenAI-compatible TTS endpoint |
| `POST /v1/audio/transcriptions` | whisper-large-v3 / scribe | Cloud STT (fallback when on-device unavailable) |

#### Text/LLM Services (for DAW Orchestration)

| Model ID | Description | Use Case |
|----------|-------------|----------|
| `openai` | GPT-5.4 Nano [tools] | Fast function calling for DAW commands |
| `openai-audio` | GPT Audio Mini [tools] | Voice input → function calls directly |
| `midijourney` | MIDIjourney [tools] | AI music composition assistant |
| `deepseek` | DeepSeek V4 Flash [tools, reasoning] | Complex reasoning for arrangement suggestions |
| `mistral` | Mistral Small 3.2 [tools] | Lightweight function calling |

#### Available TTS Voices
`alloy, echo, fable, onyx, nova, shimmer, ash, ballad, coral, sage, verse, rachel, domi, bella, elli, charlotte, dorothy, sarah, emily, lily, matilda, adam, antoni, arnold, josh, sam, daniel, charlie, james, fin, callum, liam, george, brian, bill`

### Voice-Driven DAW Control

#### Function Calling Schema (defined for both Gemma E4B and Pollinations LLMs)

```json
{
  "tools": [
    { "function": { "name": "add_track", "parameters": { "type": "audio|midi|drum" } } },
    { "function": { "name": "set_tempo", "parameters": { "bpm": "number" } } },
    { "function": { "name": "add_effect", "parameters": { "track": "number", "effect": "string" } } },
    { "function": { "name": "mute_track", "parameters": { "track": "number" } } },
    { "function": { "name": "solo_track", "parameters": { "track": "number" } } },
    { "function": { "name": "record", "parameters": { "track": "number" } } },
    { "function": { "name": "generate_music", "parameters": { "prompt": "string", "duration_sec": "number" } } },
    { "function": { "name": "generate_vocals", "parameters": { "text": "string", "voice": "string" } } },
    { "function": { "name": "set_volume", "parameters": { "track": "number", "db": "number" } } },
    { "function": { "name": "export_mixdown", "parameters": { "format": "wav|mp3|flac" } } }
  ]
}
```

#### Flow: Voice Command → DAW Action

Default (cloud — no setup required):
```
User speaks → Pollinations /v1/audio/transcriptions (whisper) → text
  → Pollinations /v1/chat/completions (openai, tools=DAW schema) → function call JSON
    → DAW Engine executes
    → Pollinations /audio/{response}?model=qwen-tts → spoken feedback
```

Opt-in on-device (user enables in Settings):
```
User speaks → Whisper.cpp (on-device) → text
  → Gemma E4B (on-device) → function call JSON
    → DAW Engine executes
    → Piper TTS (on-device) → spoken feedback
```

### AI Generation Cost Model

All Pollinations requests consume Pollen ($1 = 1 Pollen). Not free — but cheap.

**Primary music generation: `acestep` (ACE-Step 1.5 Turbo)**
- Open-source model, lowest compute cost on Pollinations
- Supports lyrics, style control, full songs
- Used for all dev/prototyping and as default user-facing model

**Cost tiers for users:**

| Feature | Model | Est. Cost | Notes |
|---------|-------|-----------|-------|
| Voice commands (STT+LLM+TTS) | whisper + openai + qwen-tts | ~$0.001/command | Negligible |
| Music generation (default) | `acestep` | Cheapest audio model | Open-source backend |
| Music generation (premium) | `elevenmusic` | ~$0.30/min | Opt-in upgrade |
| Vocal synthesis | `qwen-tts` / `elevenlabs` | $0.05-0.10/1K chars | qwen-tts is cheaper |
| Composition assistant | `midijourney` | Text-tier pricing | Very cheap |

**UX: Show Pollen cost estimate before each generation. User confirms.**

**Free fallback path (no Pollen needed):**
- Companion server running ACE-Step locally = $0
- On-device Stable Audio Small (459M) for short clips = $0
- On-device Whisper + Gemma + Piper for voice control = $0

### Companion Server (Optional, for Heavy AI)

For capabilities too large for phone or Pollinations free tier:
- Auto-discovered via mDNS on local WiFi
- Simple REST API (self-hosted on user's PC)
- Hosts: ACE-Step (full music gen), Demucs (stems), Magenta RealTime (live jam)
- All open-source, all free
- DAW works fully without it — this is purely additive

### Accessibility via Voice

Voice control is not a gimmick — it's a primary input method:
- Blind/low-vision users can fully operate the DAW via voice
- Users with motor disabilities can produce music hands-free
- Inspired by Jamu (AI co-producer for Ableton) and its disabled user community

---

## Performance & Optimization

- Audio processing entirely in native (C/C++) via NDK
- Lock-free ring buffers for audio thread communication
- Thread priority elevation for audio callback
- Track freezing (render to audio, free CPU)
- Buffer underrun detection and reporting
- Efficient UI rendering (Jetpack Compose with minimal recomposition)
- Lazy loading of plugin UIs
- Memory-mapped file I/O for large audio files

---

## Collaboration & Cloud

- Google Drive project sync (backup/restore/share)
- Export project as shareable archive (.zip with audio + project JSON)
- Songtree-style collaboration (stretch goal): publish stems, others add tracks
- Network file transfer between devices on same LAN

---

## Accessibility

- TalkBack support for all UI elements
- Minimum contrast ratios (WCAG AA)
- Scalable UI text
- Keyboard navigation for all functions
- Haptic feedback on transport controls and pad hits

---

## Development Phases

### Phase 1 — AI Services + Pollinations Client (Easy Win)
- Pollinations REST client (OpenAI-compatible, Ktor)
- Voice command pipeline: whisper STT → openai function calling → qwen-tts feedback
- Music generation via `acestep`
- Vocal generation via `qwen-tts` / `elevenlabs`
- Composition assistant via `midijourney`
- DAW tool schema definition (function calling JSON)
- Settings UI: API key, Pollen balance, model selection

### Phase 2 — AAP Plugin Hosting
- AAP host implementation (discover, load, communicate via Binder IPC)
- Plugin metadata scanning (`aap_metadata.xml`)
- MIDI 2.0 UMP parameter/note messaging to plugins
- Plugin GUI embedding (native View on Android 11+)
- Plugin state save/restore with project
- Ship built-in instruments as AAP services

### Phase 3 — Audio Engine Foundation
- MWEngine integration (audio I/O, buffer management)
- Basic multitrack timeline (audio tracks)
- Simple mixer (volume, pan, mute/solo, master bus)
- WAV/MP3/FLAC import/export
- Project save/load (JSON + audio assets)
- USB audio interface support via Oboe

### Phase 4 — MIDI & Instruments
- MIDI track support + piano roll UI (Compose Canvas)
- MWEngine synth + drum machine exposed to UI
- Step sequencer
- USB/Bluetooth MIDI input
- Basic automation (volume, pan)

### Phase 5 — Effects & Mixing
- MWEngine effects wired to insert/send slots
- Group buses
- Full parameter automation
- Sidechain routing
- Waveform UI (karya-inc/Waveform library)

### Phase 6 — Polish & Advanced Features
- Vocal tuning
- Time-stretching / pitch-shifting
- Take/comp lanes
- Ableton Link (AndroidLinkAudio reference)
- Cloud sync (Google Drive)
- Stem export
- Templates and presets library

### Phase 7 — On-Device AI (Opt-in)
- Whisper.cpp on-device STT (port from aiope-inf JNI pattern)
- Gemma E4B on-device LLM (via LiteRT-LM / llama.cpp)
- Piper TTS on-device
- Settings toggles to switch cloud → on-device

### Phase 8 — Companion Server
- mDNS auto-discovery on local WiFi
- REST API protocol definition
- Demucs stem separation
- ACE-Step full music generation (3.5B, GPU)
- Magenta RealTime live accompaniment
- All open-source, all free — DAW works fully without it

---

## Differentiation from Existing DAWs

| Gap in Market | Our Solution |
|---------------|--------------|
| No open-source full DAW on Android | MIT/Apache licensed, community-driven |
| No DAW supports open plugin standard | First-class AAP host — any developer can make plugins |
| Commercial DAWs have closed ecosystems | Open project format (JSON), open plugin API |
| Most DAWs are pattern-only OR timeline-only | Hybrid: pattern + linear arrangement (user chooses) |
| No Android DAW has voice control | On-device Whisper + Gemma E4B for hands-free operation |
| No DAW uses AI for free music generation | Pollinations free API (ElevenLabs Music, ACE-Step, MIDIjourney) |
| Vocal tuning only in Audio Evolution (paid addon) | Built-in vocal tuning |
| No DAW has Ableton Link on Android | Multi-device sync out of the box |
| Desktop compatibility is limited | Open format + stem export + MIDI export |
| Touch UX often feels like shrunken desktop | Purpose-built touch-first with adaptive layouts |
| AI features require paid subscriptions | $0 stack: on-device models + Pollinations free tier |
| Voice control is inaccessible to disabled users | Voice-first design as primary input method |

---

## Tech Stack Summary

| Layer | Technology |
|-------|-----------|
| UI | Kotlin + Jetpack Compose |
| Audio Engine | C++ via NDK, AAudio API |
| MIDI | MIDI 2.0 UMP (internal), Android MIDI API |
| Plugin Hosting | AAP (aap-core) via NdkBinder IPC |
| AI — On-Device STT | Whisper.cpp (C++/NDK, MIT) |
| AI — On-Device LLM | Gemma 4 E4B via LiteRT-LM / MediaPipe (free, open weights) |
| AI — On-Device TTS | Piper TTS (C++/NDK, MIT) |
| AI — Cloud | Pollinations.ai free API (OpenAI-compatible) |
| AI — Music Gen | Pollinations → ElevenLabs Music / ACE-Step |
| AI — Composition | Pollinations → MIDIjourney |
| Build | Gradle (app) + CMake (native) |
| Serialization | JSON (project files), Protobuf (IPC, optional) |
| Storage | Scoped storage + MediaStore, Google Drive API |
| Testing | JUnit + androidaudioplugin-testing (AAP) |
| CI | GitHub Actions |

---

## Open-Source Dependencies (External Repos)

### Audio Engine & DSP

| Repo | License | Use |
|------|---------|-----|
| [google/oboe](https://github.com/google/oboe) | Apache 2.0 | High-performance Android audio (AAudio + OpenSL ES fallback) |
| [igorski/MWEngine](https://github.com/igorski/MWEngine) | MIT | Complete Android audio engine + DSP: sequencer, synths, effects, recording, mixing with Kotlin API |
| [android/midi-samples](https://github.com/android/midi-samples) | Apache 2.0 | Official Android MIDI API samples (USB + BLE) |

### Multi-Device Sync

| Repo | License | Use |
|------|---------|-----|
| [Ableton/link](https://github.com/Ableton/link) | GPL 2+ | Official Link SDK — tempo/beat/phase sync across devices |
| [jbloit/AndroidLinkAudio](https://github.com/jbloit/AndroidLinkAudio) | MIT | Ableton Link + Oboe integration reference for Android |

### UI Components

| Repo | License | Use |
|------|---------|-----|
| [karya-inc/Waveform](https://github.com/karya-inc/Waveform) | Apache 2.0 | Jetpack Compose waveform visualization + interactive segment selection |

### AI / On-Device Inference

| Repo | License | Use |
|------|---------|-----|
| [ggerganov/whisper.cpp](https://github.com/ggerganov/whisper.cpp) | MIT | On-device STT via NDK |
| [rhasspy/piper](https://github.com/rhasspy/piper) | MIT | On-device TTS via NDK |
| [google/gemma.cpp](https://github.com/google/gemma.cpp) | Apache 2.0 | Lightweight Gemma inference in C++ |
| [facebookresearch/demucs](https://github.com/facebookresearch/demucs) | MIT | Stem separation (companion server) |
| [ACE-Step/ACE-Step-v1-3.5B](https://huggingface.co/ACE-Step/ACE-Step-v1-3.5B) | Apache 2.0 | Open-source music generation (companion server) |
| [stabilityai/stable-audio-open](https://stability.ai/stable-audio) | Stability Community | On-device capable (459M) music/SFX generation |

### Audio Effects

| Repo | License | Use |
|------|---------|-----|
| [james34602/JamesDSPManager](https://github.com/james34602/JamesDSPManager) | GPL 2.0 | High-quality DSP algorithms (EQ, reverb, compression) |

### Sequencer / DAW Reference

| Repo | License | Use |
|------|---------|-----|
| [yuxshao/ptcollab](https://github.com/yuxshao/ptcollab) | MIT | Collaborative piano-roll sequencer — UI/UX patterns |

---

## Code Reuse — Porting from Existing Repos

### aiope-inf (On-Device LLM Engine) — PRIMARY PORT

Existing on-device llama.cpp inference engine with JNI, Vulkan GPU, streaming, and OpenAI-compatible local server. Provides ~80% of Gemma E4B integration.

| Source | Port Target |
|--------|-------------|
| `app/src/main/jni/llama_jni.cpp` | Adapt for Whisper.cpp + Gemma E4B JNI bridge |
| `app/src/main/jni/streaming.cpp` | Token streaming for real-time intent parsing |
| `app/src/main/jni/gpu_backend.cpp` | Vulkan GPU detection/acceleration |
| `app/src/main/jni/multimodal.cpp` | Audio input handling for Gemma E4B |
| `app/src/main/cpp/CMakeLists.txt` | NDK build config with llama.cpp submodule |
| `OpenAIServer.kt` | Local API server (reuse for companion server) |
| `ModelManager.kt` | Model loading/unloading lifecycle |
| `InferenceService.kt` | Foreground service to keep AI alive during recording |

### aiope2 (Android AI App) — Architecture Patterns

| Source | Port Target |
|--------|-------------|
| `MEDIA_GENERATION_SPEC.md` | Pollinations client architecture (OpenAI-compatible, tool calling) |
| `core-network/` | HTTP client patterns, API abstraction |
| `core-model/` | Data models for AI responses |
| `build-logic/` | Convention plugins, multi-module Gradle |
| `feature-chat/` (tool handlers) | DAW function calling tool handlers |
| `LlmProvider.kt` pattern | Multi-provider routing (Pollinations vs on-device) |
| `TaskModelStore.kt` pattern | Route tasks to models (music gen → elevenmusic, STT → whisper) |

### aio-pulse — UI/Module Structure

| Source | Port Target |
|--------|-------------|
| `build-logic/` | Shared Gradle conventions |
| `core-designsystem/` | Compose theme base |
| `core-network/` | Ktor/OkHttp client setup |
| Multi-feature module pattern | Separate DAW features into modules |

### aap-core (Cloned) — Plugin Hosting (Phase 4)

| Source | Port Target |
|--------|-------------|
| `androidaudioplugin/` | Plugin discovery, Binder IPC, audio buffers |
| `include/aap/` | MIDI 2.0 UMP message format |
| `androidaudioplugin-ui-compose/` | Plugin GUI embedding |
| `androidaudioplugin-testing/` | Plugin integration tests |

### helio-sequencer (Cloned) — DAW Core Logic (Phase 1-2)

| Source | Port Target |
|--------|-------------|
| `Source/Core/` | MIDI sequencing, transport, timeline |
| `Source/Core/Audio/` | Playback/recording architecture |
| `Source/Core/Serialization/` | Project save/load patterns |
| `Source/UI/Sequencer/` | Piano roll UI concepts |

---

## References

- [aap-core](https://github.com/atsushieno/aap-core) — AAP plugin framework
- [aap-juce-helio](https://github.com/atsushieno/aap-juce-helio) — Helio with AAP (reference host)
- [helio-sequencer](https://github.com/helio-fm/helio-sequencer) — open-source sequencer (GPL)
- [Pollinations API](https://gen.pollinations.ai/docs) — free generative AI API (OpenAI-compatible)
- [Whisper.cpp](https://github.com/ggerganov/whisper.cpp) — on-device STT (MIT)
- [Gemma 4 E4B](https://huggingface.co/google/gemma-4-E4B) — on-device multimodal LLM with function calling
- [LiteRT-LM](https://developers.googleblog.com/blazing-fast-on-device-genai-with-litert-lm/) — Google's on-device inference runtime
- [Piper TTS](https://github.com/rhasspy/piper) — fast on-device text-to-speech (MIT)
- [ACE-Step](https://huggingface.co/ACE-Step/ACE-Step-v1-3.5B) — open-source music generation (Apache 2.0)
- [Stable Audio 3.0](https://stability.ai/stable-audio) — open-weight audio models (459M on-device capable)
- [Demucs](https://github.com/facebookresearch/demucs) — stem separation (MIT)
- [Magenta RealTime](https://arxiv.org/html/2508.04651v1) — live steerable music generation (open weights)
- FL Studio Mobile, Cubasis 3, Audio Evolution Mobile, n-Track Studio, Roland Zenbeats — commercial references
- [Jamu](https://www.jamu.ai/) — AI co-producer for Ableton (voice-driven DAW control reference)
