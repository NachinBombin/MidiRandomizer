# MIDI Randomizer

An Android app that generates and sends MIDI note data to any connected MIDI device or virtual port using the Android MIDI API (`android.media.midi`). Built for live performance, generative composition, and algorithmic music exploration.

---

## Current Version: V9 — `melody-harmony-theory`

V9 is the active development branch. It introduces a full **harmony theory layer**, a **multi-engine melodic system**, and a **three-voice architecture**, transforming the app from a simple note randomizer into a real-time algorithmic music engine.

---

## Architecture Overview

The app is structured around a **foreground `MidiService`** that drives all MIDI output. The UI lives in three Fragments hosted in a `ViewPager2` inside `MainActivity`:

| Fragment | Role |
|---|---|
| `MainFragment` | Transport (Play/Stop), BPM, scale, root note, octave range, MIDI channel, timing mode, live note display |
| `VoicesFragment` | Voice 2 and Voice 3 configuration — mode, harmony, independent, melodic, drone |
| `ProSettingsFragment` | All advanced settings: melodic engine selector, Tier-1 overlays, Tier-2 engine configs, jitter, Euclidean rhythm, velocity shaping |

---

## Voice Architecture

Three independent voices run concurrently, each routable to any MIDI channel.

### Voice 1 — Main Voice
The primary generative voice. Drives the global BPM, scale, root note, and timing mode. Supports four styles:

| Style | Behaviour |
|---|---|
| `GENERATIVE` | Picks scale degrees on every beat via the active melodic engine |
| `SINGLE_NOTE_DRONE` | Fires the root note once and holds it indefinitely |
| `EVOLVING_DRONE` | Fires a new root-area note every N beats (constant or randomised interval) |
| `CHORDS` *(in progress)* | Emits chord voicings instead of single notes |

### Voice 2 & Voice 3 — Secondary Voices
Each secondary voice has three operating modes:

| Mode | Behaviour |
|---|---|
| `HARMONY` | Reacts to every V1 note-on; plays a diatonically offset note (configurable step offset, time drift, skip probability) |
| `INDEPENDENT` | Runs its own beat loop — own BPM, scale, root, octave range, timing mode, and full Pro Settings |
| `MELODIC` *(stub)* | Chord-aware melodic counterpoint; engine to be wired in the upcoming Chords update |

Voice 3 in Harmony mode can reference Voice 2 as its harmonic anchor instead of Voice 1.

---

## Scales

17 built-in scales, selectable per voice:

| # | Scale | # | Scale |
|---|---|---|---|
| 0 | Chromatic | 9 | Whole Tone |
| 1 | Major (Ionian) | 10 | Kurd |
| 2 | Natural Minor | 11 | Celtic Minor |
| 3 | Harmonic Minor | 12 | Pygmy |
| 4 | Pentatonic Major | 13 | Lydian Dominant |
| 5 | Pentatonic Minor | 14 | Aegean (Lydian) |
| 6 | Blues | 15 | Hijaz |
| 7 | Dorian | 16 | Akebono |
| 8 | Mixolydian | | |

---

## Timing Modes

Four timing modes available per voice:

| Mode | Behaviour |
|---|---|
| `METRONOME` | Strict beat grid at the set BPM |
| `MIXED` | 30% chance of a half-beat subdivision |
| `RANDOMIZED` | Each interval is `base × U(0.5, 1.5)` |
| `EUCLIDEAN` | Bjorklund algorithm distributes N pulses over M steps; onset pattern drives note firing |

All modes pass through the **Jitter Engine** for human-feel timing variance.

---

## Melodic Engine System

Voice 1 (and independent V2/V3) selects notes via a **`MelodicEngineDispatcher`** that routes to one of six engines:

### Tier-0 — Naive
Pure random scale-degree selection. Default fallback.

### Tier-1 — Markov Chain
First or second-order Markov chain over scale degrees, with three optional overlays:

- **Narmour IR Scoring** — weights successors by Implication-Realization rules: small intervals imply continuation; large intervals imply reversal and step-return.
- **Contour Gravity** — opposes prolonged register drift by applying a restoring force once accumulated degree-delta exceeds a threshold.
- **Gesture Curve Bias** — adds a directional weight from the active pitch curve preset (see Gesture Engine).
- **Melodic Logic Style** — `STEPWISE`, `ARPEGGIATED`, or `WIDE_LEAPS` shapes the base Markov weight table.

### Tier-2 — Replacement Engines
Full replacements for the Markov chain; each produces structurally distinct output:

| Engine | Description |
|---|---|
| `PWG` — Probabilistic Weighted Grammar | Phrases built from pre-defined degree-delta motifs; 4 motif vocabulary sets (balanced, ascending, descending, angular) |
| `L_SYSTEM` — Lindenmayer Fractal | Self-similar melody via rewrite rules applied to a seed axiom; 1–4 iterations |
| `CELL_AUTOMATA` — Cellular Automaton | Pitch-class cells evolve via life-like birth/survival rules each generation |
| `NRT_MELODIC` — Neo-Riemannian Tonnetz | Melody notes drawn from chord tones of the current NRT Klang after P/L/R transformations |

---

## Gesture Curve Engine

Inspired by Mazzola's mathematical gesture theory. Evaluates piecewise-linear phrase arcs over a fixed 16-beat phrase window.

Four independent curve channels — **pitch bias**, **register shift**, **density gate**, **velocity scale** — each mapped to one of 8 built-in curve presets (flat, rising arch, falling arch, sawtooth rise/fall, step-up, crescendo-decrescendo, valley). A `gestureDepth` parameter blends between pure algorithmic output (0) and full curve dominance (1).

---

## Neo-Riemannian Theory Engine (`NRTEngine`)

Maintains a **Klang** (consonant triad state: root pitch-class + major/minor quality) and walks the Tonnetz via three involutory transformations:

| Op | Name | Effect |
|---|---|---|
| P | Parallel | Toggles major↔minor, same root |
| L | Leading-tone exchange | C maj → E min (root shifts −1 semitone) |
| R | Relative | C maj → A min (root shifts +9/−3 semitones) |

Three cycle presets for live use:
- **Random PLR walk** — weighted random selection of P/L/R
- **LPPL hexatonic** — repeating 4-op cycle through 6 chords
- **PRRP octatonic** — repeating 4-op cycle through 8 chords

The NRT engine is used as the `NRT_MELODIC` engine today and is architecturally ready to drive a future chord harmony mode.

---

## Pro Settings — Parameter Reference

| Category | Parameter | Range / Options |
|---|---|---|
| Timing | Jitter Amount | 0–100% of base interval |
| Timing | Jitter Type | `NONE` · `SLIGHT` · `MODERATE` · `HEAVY` |
| Rhythm | Euclidean Steps | 2–32 |
| Rhythm | Euclidean Density | 1 – Steps |
| Rhythm | Euclidean Rotation | 0 – Steps−1 |
| Velocity | Velocity Pattern | `RANDOM` · `ACCENT` · `CRESCENDO` · `DECRESCENDO` · `FLAT` |
| Melodic Engine | Engine | `NAIVE` · `MARKOV` · `PWG` · `L_SYSTEM` · `CELL_AUTOMATA` · `NRT_MELODIC` |
| Markov | Melodic Logic Style | `STEPWISE` · `ARPEGGIATED` · `WIDE_LEAPS` |
| Markov | Second-Order | Boolean |
| Narmour | Process vs Reversal | 0–1 |
| Narmour | Return Bias | 0–1 |
| Narmour | Max Leap Penalty | 0–1 |
| Contour Gravity | Threshold | degree steps |
| Contour Gravity | Strength | 0–8 |
| Gesture | Curve Presets (×4) | 0=flat · 1=rise arch · 2=fall arch · 3=saw↑ · 4=saw↓ · 5=step · 6=crescendo · 7=valley |
| Gesture | Depth | 0–1 |
| PWG | Motif Set | 0=balanced · 1=ascending · 2=descending · 3=angular |
| PWG | Phrase Length (motifs) | 1–8 |
| PWG | Direction Bias | −1 to +1 |
| L-System | Axiom Index | 0–N built-in axioms |
| L-System | Iterations | 1–4 |
| L-System | Rule Variance | 0–1 |
| Cell Automata | Survival Min/Max | neighbor counts |
| Cell Automata | Birth Count | exact neighbor count |
| Cell Automata | Mutation Rate | 0–1 |
| NRT Melodic | P/L/R Weights | independent floats |
| NRT Melodic | Cycle Preset | 0=random · 1=LPPL hexatonic · 2=PRRP octatonic |

---

## Harmony Mode — Diatonic Voice Leading

When a secondary voice is in `HARMONY` mode, it uses `DiatonicHarmony` to place its note:

1. Builds the set of all allowed MIDI notes (0–127) in the current scale and root.
2. Finds the index of the V1 note (or nearest allowed note) in that set.
3. Adds the configured `toneStepOffset` (default: +2 scale steps = a diatonic third).
4. Applies velocity scaling and micro-drift.
5. Optionally delays the note-on by up to `timeDriftMs` milliseconds for a humanised ensemble feel.
6. Optionally skips the note entirely at `skipProbability`.

---

## In Progress — CHORDS Mode (V9)

`VoiceStyle.CHORDS` is defined and plumbed into `VoiceConfig.IndependentConfig` with initial parameters (`chordsType`, `chordsRhythm`). Full implementation — chord voicing engine, arpeggio scheduler, and `MELODIC` voice counterpoint — is the active work item for this branch.

---

## Project Structure

```
app/src/main/java/com/nachinbombin/midirandomizer/
  MainActivity.kt             — ViewPager2 host; service binding; callback routing
  MainFragment.kt             — V1 transport, BPM, scale/root, timing mode, live display
  VoicesFragment.kt           — V2/V3 mode, harmony & independent config UI
  ProSettingsFragment.kt      — Full pro settings UI (Tier 0–2 engines + overlays)
  PerformanceFragment.kt      — Performance view
  MidiService.kt              — Core foreground service; V1 note loop; voice orchestration
  VoiceEngine.kt              — Self-contained engine for V2/V3 (harmony + independent loops)
  MelodicEngineDispatcher.kt  — Routes note requests to the active engine
  MarkovMelody.kt             — Tier-1 Markov chain with Narmour/gravity/gesture overlays
  PhraseGrammar.kt            — Tier-2 PWG (motif-based phrase generation)
  LSystemMelody.kt            — Tier-2 Lindenmayer fractal melody
  CellAutomata.kt             — Tier-2 pitch-class cellular automaton
  NRTEngine.kt                — Neo-Riemannian Tonnetz engine (P/L/R Klang walks)
  NRTMelodicEngine.kt         — NRT melodic adapter (maps Klang tones to scale degrees)
  GestureEngine.kt            — Mazzola-inspired phrase-arc gesture curves
  EuclideanRhythm.kt          — Bjorklund Euclidean rhythm generator
  JitterEngine.kt             — Timing humanisation (uniform/Gaussian/exponential)
  VelocityShaper.kt           — Velocity envelope patterns with micro-humanisation
  VoiceConfig.kt              — VoiceMode/VoiceStyle enums + all config data classes + DiatonicHarmony
  ProSettings.kt              — Master ProSettings data class + all engine config data classes
  MidiOutputService.kt        — Virtual MIDI output port (exposes app as a MIDI source)
  ThemeManager.kt             — Runtime theme switching
  ThemePreset.kt              — Built-in colour/style presets
  ThemePickerDialog.kt        — Theme selection dialog
```

---

## Requirements

- Android 6.0+ (API 23+) — `android.hardware.midi` feature required
- Works with any MIDI receiver: virtual ports (e.g. **Caustic**, **FL Studio Mobile**, **AudioKit Synth One**), USB OTG hardware, Bluetooth MIDI, or other apps via the virtual output port exposed by `MidiOutputService`

## Getting Started

1. Clone the repo and open in **Android Studio Hedgehog** or newer
2. Build and install (`app` module, any `debug` variant)
3. Open a synth app and ensure it exposes a virtual MIDI input port
4. In MIDI Randomizer, select the device from the list to connect
5. Configure Voice 1 (scale, root, BPM, octave range, timing mode)
6. Optionally enable Voice 2/3 and configure their modes
7. Tap **START**

## License

MIT
