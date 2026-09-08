# Release validation — Off the Top 1.2.0

## Current change: less copy and a layered dark background

- Removed redundant marketing text and repeated headers from home, practice, countdown, gameplay, pause and results. Shortened settings/help. Tilt diagnostics moved behind a Tilt setup control.
- Kept the existing one-accent teal palette. Dark mode now uses cached static linear/radial shading, dark shadows and muted outlines; the readable clue surface remains opaque.
- No changes to AppModel, RoundEngine, TiltDetector, MotionFilter or deck files. Existing gameplay, history, settings and motion assertions remain; UI assertions now use the shorter visible labels and accessible answer descriptions.
- Added QuietUiTest (3 flows): clean home/help, optional diagnostics/reset, and compact touch gameplay through review/persistence.
- Added BackgroundAppearanceTest (3 rendered-image tests): day stays uniform, night visibly varies without animating and preserves >4.5 text contrast at sampled points, and solid card surfaces cover the gradient. These sample actual Android-rendered pixels, not an imitation of the gradient math.

## Previous motion regression (fixed in 1.1.0, retained)

The user reported intermittent pass gestures and no successful downward correct gestures on their phone. **The earlier emulator PASS was not proof of physical reliability.** The two new PhysicalTiltRegressionTest cases were run against v1.0.0 first: both failed (exit 1). A 22° resting hold failed its narrow ±16° arming band, and a 32° downward nod did not meet the old ~43° threshold. The exact user's sensor readings are unavailable, so these are reproduced defects consistent with the report, not a claimed handset-specific diagnosis.

The new detector learns a stable starting angle, triggers at relative ±28° (gentle ±20°), tracks center returns throughout feedback, and keeps an unfinished late countdown placement pending until stable. The sensor adapter uses sensor timestamps and short, time-based accelerometer smoothing instead of relying first on manufacturer-specific gravity-filter delay.

The single teal accent replaces all three former accents. Navy/ivory neutrals, glyphs and labels distinguish pass/correct without needing separate accent colors. Actual text and icon contrast is unit tested.

## Environment and result

- Native Android/Kotlin/Compose; no backend, account or browser layer.
- Windows; JDK 21 compiling Java/Kotlin target 17; Android SDK 35; Gradle 8.11.1.
- Emulator: SetTest, Android 14 / API 34, 1080 × 2340 phone rotated to landscape, 420 dpi.
- Physical device: unavailable. Physical tilt comfort, actual audible output and haptic strength remain unverified.
- Overall automated result: **PASS**. Physical playtest remains appropriate for those hardware-dependent qualities.

## Checks

| Check | Result | Exit code | Evidence |
|---|---|---:|---|
| Pure engine, motion, theme and data tests | 130 passed, 0 failed | 0 | 50 round, 61 detector, 9 filter, 2 physical-regression, 3 contrast, 5 deck tests |
| Android UI, model and rendered-background tests | 22 passed, 0 failed, 0 skipped | 0 | Existing five test classes plus QuietUiTest and BackgroundAppearanceTest |
| Copy and accessible outcomes | PASS | 0 | No repeated slogans or expanded diagnostics by default; icon-only answers keep accessible outcome descriptions and correction behavior |
| Layered background | PASS | 0 | Actual pixel captures verify visible night variation, no one-second animation, opaque card surfaces and sampled text contrast |
| Long-card layout cases | 108 fitting renders, plus overflow/recovery checks | 0 | 36 longest entries × 3 viewport/font-size combinations; actual AutoWord renderer |
| Release lint | 0 errors; 10 advisory warnings | 0 | Version update notices, intentional landscape orientation, retained license resource |
| Signed release build | PASS | 0 | R8 minification and resource shrinking enabled |
| APK update installation and cold launch | PASS | 0 | Actual signed release installed over previous version without clearing data |
| APK signature | PASS | 0 | apksigner verifies v2 signature, suitable for min Android 8 |
| Motion through Android sensor stack | PASS | 0 | Practice and live down/correct, up/pass: full flips plus a 22° resting hold moving to -12° and +56°, in both x-axis landscape directions |
| Release UI and saved history | PASS | 0 | Pause, review, theme switches, force-stop/relaunch history persistence |
| Real-time expiry | PASS | 0 | 30-second round plus 3-second countdown; completion observed after approximately 39 seconds including UI inspection overhead |
| Android crash log | Empty | 0 | adb logcat -d -b crash |
| Permissions | Offline only | 0 | VIBRATE and AndroidX non-exported receiver permission; no INTERNET, camera, microphone or storage permission |

## Critical defects caught and fixed

- Normal resting holds and downward nods rejected by absolute-angle gates: now calibrated relative angles, with faster center and tilt dwell times.
- Feedback and countdown transitions discarded motion readiness: now continuous tracking with a separate visible-card scoring gate.
- Late forehead placement could preserve an early handheld baseline: pending calibration now completes across countdown end before scoring.
- Hidden cards could be scored by input arriving while feedback expired: engine now rejects that input, and actual rendered text must acknowledge visibility before scoring.
- A hidden next card could appear as unanswered if time ended during feedback: the current card is now null while feedback hides it.
- Score correction was saved but not redrawn: results now receive immutable answer/score values to invalidate Compose rendering.
- Long card text previously relied on iterative shrinking: the actual renderer now measures the fitting size before displaying or enabling input.
- Paused and completed rounds previously retained sensor and timer work: subscriptions now depend on the actual phase.

## Reproduce

Use the Gradle test commands in the README. The PowerShell script in tools/release-smoke.ps1 exercises the installed release through Android UI inspection and actual emulator acceleration values; no test-only control is exposed in the release application.

Unit tests cover precise deadline boundaries, timer overflow, cooldown, pause/resume and deck exhaustion. Instrumented lifecycle expiry tests advance the pure engine clock deterministically; the separate release smoke script also checks a real 30-second expiry with no injected time.

MotionFilterTest runs complete filtered down/up traces at 5, 10, 20 and 50 ms sample intervals. Detector tests use continuous streams instead of pretending two readings a long time apart prove stability. MotionFlowTest exercises actual AppModel input gates and one real-activity calibration lifecycle; its isolated model streams are **not** hardware tests. The release smoke script separately exercises Android's real sensor listener with emulator-injected acceleration and checks the simplified UI through visible text and accessibility descriptions. It now pins commands to the emulator so connected physical phones are untouched.

## Limits

- Android 14 emulator runtime verified; minimum API 26 and target API 35 validated by build, not exhaustively tested on every OS/device.
- Card layout samples the longest entries, not a visual inspection of every card on every device.
- In-progress rounds survive activity recreation and pause/resume, but not operating-system process termination. Settings, seen-card memory and completed rounds survive process termination.
- Deck content mixes easy and moderately difficult everyday subjects; it is not difficulty-filtered.