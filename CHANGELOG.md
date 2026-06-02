# Changelog

## v2.0-pro

### New files
| File | Role |
|---|---|
| `ProSettings.kt` | Immutable data class + enums for all pro parameters |
| `EuclideanRhythm.kt` | Bjorklund algorithm — distributes N pulses over M steps |
| `MarkovMelody.kt` | First-order Markov chain over scale degrees |
| `JitterEngine.kt` | Uniform / Gaussian / Exponential timing humanisation |
| `VelocityShaper.kt` | 5 velocity envelope patterns with micro-humanisation |
| `ProSettingsFragment.kt` | All Tier 1–3 UI controls in a swipeable tab |
| `MainFragment.kt` | Original UI refactored into a Fragment |

### Modified files
| File | Change |
|---|---|
| `MidiService.kt` | Added `TIMING_EUCLIDEAN`, `updateProSettings()`, integrated all pro helpers |
| `MainActivity.kt` | Hosts `ViewPager2` with Main + Pro tabs; routes callbacks |
| `activity_main.xml` | Replaced ScrollView with TabLayout + ViewPager2 |
| `fragment_main.xml` | Original layout, now Fragment-scoped |
| `fragment_pro_settings.xml` | Full Pro Settings UI layout |
| `arrays.xml` | `default_octave_range` for RangeSlider |
| `app/build.gradle` | Added `viewpager2`, `fragment-ktx`; bumped versionCode to 2 |

### Pro feature map
```
Tier 1 (Foundational)
  ├── Jitter Amount (0–100 %)  ← universal, applied to every timing mode
  ├── Jitter Distribution      ← Uniform / Gaussian / Exponential
  └── Velocity Pattern         ← Random / Ascending / Descending / Peak / Accent Beats

Tier 2 (Structured Generators)
  ├── Euclidean Rhythm         ← Steps (2–32), Density, Rotation
  │     └── selectable as 4th timing mode in Main tab
  └── Markov Melodic Logic     ← Stepwise / Arpeggiated / Wide Leaps
        └── toggle + style selector

Tier 3 (Hybrid Presets)
  ├── Ambient Texture          ← slow Euclidean + Gaussian jitter + stepwise Markov + peak velocity
  └── Experimental Soundscape  ← high exponential jitter + wide-leap Markov + accent-beat velocity
```
