# MIDI Randomizer

An Android app that sends randomized MIDI notes to any connected MIDI app or hardware device using the Android MIDI API (`android.media.midi`).

## Features

- **Randomized note generation** — picks notes from a selected musical scale each beat
- **10 built-in scales** — Chromatic, Major, Natural/Harmonic Minor, Pentatonic Major/Minor, Blues, Dorian, Mixolydian, Whole Tone
- **Adjustable BPM** — 20–300 BPM via seekbar
- **Velocity control** — with slight random humanization per note
- **Octave selection** — octaves 0–8
- **MIDI channel selector** — channels 1–16
- **Device list** — auto-discovers all connected MIDI devices (USB, Bluetooth, virtual)
- **Live note display** — shows last sent note name, MIDI number, and velocity

## Requirements

- Android 6.0+ (API 23+)
- MIDI feature support (`android.hardware.midi`)
- Works with any MIDI app that exposes virtual MIDI ports (e.g. **Caustic**, **FL Studio Mobile**, **AudioKit Synth One**, **FluidSynth**) or hardware MIDI devices via USB OTG

## Getting Started

1. Clone the repo and open in **Android Studio Hedgehog** (or newer)
2. Build and install on device
3. Open a synth app (e.g. Caustic) and ensure it exposes a virtual MIDI input
4. In MIDI Randomizer, tap the device in the list to connect
5. Choose scale, BPM, velocity, octave, and channel
6. Press **START** — random notes will be sent on each beat

## Project Structure

```
app/src/main/java/com/nachinbombin/midirandomizer/
  MainActivity.kt     — all MIDI logic and UI
app/src/main/res/
  layout/activity_main.xml
  values/strings.xml
  values/themes.xml
```

## How It Works

- Uses `MidiManager` to enumerate devices and open an `MidiInputPort`
- A `Handler` fires at each beat interval (`60000 / BPM` ms)
- Each beat: sends `NOTE_OFF` for the previous note, picks a random interval from the selected scale, constructs a MIDI byte array, and sends `NOTE_ON` via the port
- Device connection/disconnection is handled via `MidiManager.DeviceCallback`

## License

MIT
