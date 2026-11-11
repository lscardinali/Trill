<p align="center">
  <img src="design/trill-icon.png" alt="Trill icon" width="160">
</p>

<h1 align="center">Trill</h1>

<p align="center">
  <b>Tiny game jingles from YouTube, made right on your handheld.</b><br>
  Search a game, preview its theme songs, trim the perfect few seconds and save a jingle that weighs just a few kilobytes.
</p>

<p align="center">
  <a href="https://github.com/lscardinali/Trill/releases/latest"><img src="https://img.shields.io/github/v/release/lscardinali/Trill?color=8A62EE&label=release" alt="Latest release"></a>
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/lscardinali/Trill"><img src="https://img.shields.io/badge/Get%20it%20on-Obtainium-B08CFF" alt="Get it on Obtainium"></a>
  <img src="https://img.shields.io/badge/Android-10%2B-3DDC84" alt="Android 10+">
</p>

> [!IMPORTANT]
> **Trill is an independent, unofficial project. It is not affiliated with, endorsed by, or connected to [Cocoon](https://github.com/inssekt/CocoonFE) or its developers.**
> It's simply a companion tool that makes jingles in a format Cocoon can use, and its look is a love letter to Cocoon's pods.
> Trill is also not affiliated with YouTube or Google.

---

## What it does

Launchers like **Cocoon** can play a short *jingle* when you land on a game. Trill makes those jingles in a couple of minutes, without a PC:

1. **Search** for a game by name, or pick one straight from your ROM folders (**My games**).
2. **Preview** YouTube results (title themes, main themes, OSTs, menu music) as audio only.
3. **Use** the one you like. Trill downloads just the audio and shows its waveform.
4. **Trim** the moment you want with two markers. Add fade in/out and volume levelling, then preview the *exact* result.
5. **Save** it as `<Game name>.ogg` to a folder you choose (default `Music/Jingles`).
6. In Cocoon: **edit the game → Jingle → pick the file.**

### Highlights

- 🎮 **Built for handhelds**: fully navigable with a controller *and* touch, with a contextual button legend and an optional Switch-style (A/B, X/Y swapped) layout.
- 🪶 **Really small files**: jingles are encoded as **Ogg Opus**, the most efficient codec Android can encode. An 8-second jingle is ~17–65 KB depending on quality.
- 🎚️ **Quality presets**: Tiny (16k mono), Small (24k mono), **Clear (40k stereo, default)** and Rich (64k stereo).
- 🌊 **Waveform trimmer** with a zoomed view, a whole-track overview, marker nudging on the D-pad and dragging by touch.
- 📁 **Folder picker that works with a controller**, plus ROM-folder auto-detection for picking games.
- 💜 **Cocoon-inspired UI**: dark cards, glowing focus ring, Poppins type and a pod-style icon.

## Install

### With Obtainium (recommended)

[Obtainium](https://github.com/ImranR98/Obtainium) installs Trill from GitHub Releases and keeps it updated.

1. Tap **[Add to Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/lscardinali/Trill)** on your device,
   or in Obtainium choose **Add App** and paste `https://github.com/lscardinali/Trill`.
2. Install, and Obtainium will pick up new releases automatically.

### Manually

Download `trill-<version>.apk` from the [latest release](https://github.com/lscardinali/Trill/releases/latest) and install it.

### First run

Trill asks for **All files access** so it can read your ROM folders and save jingles into shared folders (like `Music/Jingles`) that Cocoon's file picker can see.

## Controls

| Where | Button | Action |
|---|---|---|
| Everywhere | **A** / **B** | Select / Back |
| Results | **A** · **X** · **Y** | Preview · Use track · Stop preview |
| Results | **L1 / R1** · **L2 / R2** | Seek preview ±10 s · Change search flavor |
| Trimmer | **◀ ▶** · **A** | Move the active marker · Switch start/end marker |
| Trimmer | **L1 / R1** · **L2 / R2** | Slide the selection · Jump 10 s |
| Trimmer | **Y** · **☰ Start** | Preview the encoded jingle · Save |

The on-screen legend only shows the actions that are available right now.

## Building

Requirements: Android Studio (or JDK 17+) and the Android SDK.

```bash
./gradlew :app:assembleDebug
```

## How it works

- **Search & streams**: [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) (no API key, no Google services).
- **Playback**: AndroidX Media3 / ExoPlayer.
- **Decoding**: Android `MediaExtractor` + `MediaCodec`, with a light resampler to 48 kHz.
- **Encoding**: the platform Opus encoder, packed into Ogg by Trill's own muxer. Android's built-in Ogg writer adds ~40% overhead on files this small.

## Please be respectful

Trill is meant for making **personal** launcher jingles from short clips. Music on YouTube belongs to its creators. Don't redistribute what you make unless you have the rights to do so.

## Credits

- Built with [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) (GPL-3.0), [Media3](https://developer.android.com/media/media3), [Coil](https://coil-kt.github.io/coil/), [OkHttp](https://square.github.io/okhttp/) and Jetpack Compose.
- Font: [Poppins](https://fonts.google.com/specimen/Poppins) (SIL Open Font License).
- Visual style inspired by [Cocoon](https://github.com/inssekt/CocoonFE), with much love and no affiliation.
