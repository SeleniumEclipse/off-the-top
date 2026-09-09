# Release validation — Off the Top 1.4.0

## Current change: approved native Card Table theme

- User approved the green/red card-table preview and requested removal of its outer poker-table outline. Native app and approved preview now have no outer outline.
- Deck selection is a horizontal row of cream card stacks with red patterned backs and original vector corner marks. Each deck's leaf/mug/motion artwork is retained exactly from the approved drawing; no suits or emoji.
- Gameplay places the readable clue, score, timer and mirrored category marks on a cream card; green correct/red pass feedback changes the ink and printed border without hiding clarity behind texture. Both themes preserve that color meaning.
- Timer/score/icon slots are measured before the clue, keeping long words clear at enlarged system font sizes. Card-back crosshatching is cached and confined to backs, never the table or readable face.
- No changes to AppModel, RoundEngine, TiltDetector, MotionFilter or deck files. Gameplay, history, settings and motion assertions remain.
- Added four CardTableAppearanceTest flows: native stacks and original vectors, actual playing-card faces/backs in both themes, real scoring callbacks with correct/pass colors and no hidden scoring, and 400×180 cards with doubled font scale and strict corner/clue collision checks.
- Design rules now permit only the three user-approved category vectors and red-back pattern, while still prohibiting gradients, emoji, table outlines and filler copy.

## Previous motion regression (fixed in 1.1.0, retained)

The user reported intermittent pass gestures and no successful downward correct gestures on their phone. **The earlier emulator PASS was not proof of physical reliability.** The two new PhysicalTiltRegressionTest cases were run against v1.0.0 first: both failed (exit 1). A 22° resting hold failed its narrow ±16° arming band, and a 32° downward nod did not meet the old ~43° threshold. The exact user's sensor readings are unavailable, so these are reproduced defects consistent with the report, not a claimed handset-specific diagnosis.

The new detector learns a stable starting angle, triggers at relative ±28° (gentle ±20°), tracks center returns throughout feedback, and keeps an unfinished late countdown placement pending until stable. The sensor adapter uses sensor timestamps and short, time-based accelerometer smoothing instead of relying first on manufacturer-specific gravity-filter delay.

The newly approved green/red palette replaces the earlier teal-only experiment. Actual page, card and outcome-button contrast is tested; outcome labels and vectors avoid relying on color alone.

## Environment and result

- Native Android/Kotlin/Compose; no backend, account or browser layer.
- Windows; JDK 21 compiling Java/Kotlin target 17; Android SDK 35; Gradle 8.11.1.
- Emulator: SetTest, Android 14 / API 34, 1080 × 2340 phone rotated to landscape, 420 dpi.
- Physical device: unavailable. Physical tilt comfort, actual audible output and haptic strength remain unverified.
- Overall automated result: **PASS**. Physical playtest remains appropriate for those hardware-dependent qualities.

## Checks

| Check | Result | Exit code | Evidence |
|---|---|---:|---|
| Pure engine, motion, theme, data and design tests | 137 passed, 0 failed | 0 | 50 round, 61 detector, 9 filter, 2 physical-regression, 3 contrast, 5 deck, 7 design-guardrail tests |
| Android UI, model and rendered-background tests | 26 passed, 0 failed, 0 skipped | 0 | Existing seven test classes plus four CardTableAppearanceTest flows |
| Copy and accessible outcomes | PASS | 0 | No repeated slogans or expanded diagnostics by default; icon-only answers keep accessible outcome descriptions and correction behavior |
| Flat backgrounds and vector icons | PASS | 0 | Actual pixel captures verify uniform opaque backgrounds, no one-second animation, solid card surfaces, text contrast and painted vectors |
| Native artwork and card theme | PASS | 0 | Mirrored approved category vectors, red backs, opaque cream faces, green/red feedback and no outer table outline |
| Enlarged-font corner separation | PASS | 0 | 400×180 actual card with doubled font scale; complete clues cannot overlap timer, score or corner marks |
| Long-card layout cases | 108 fitting renders, plus overflow/recovery checks | 0 | 36 longest entries × 3 viewport/font-size combinations; actual AutoWord renderer |
| Release lint | 0 errors; 10 advisory warnings | 0 | Eight dependency update notices, intentional landscape orientation, older-Android backup configuration advice |
| Signed release build | PASS | 0 | R8 minification and resource shrinking enabled |
| APK update installation and cold launch | PASS | 0 | Actual signed release installed over previous version without clearing data |
| APK signature | PASS | 0 | apksigner verifies v2 signature, suitable for min Android 8 |
| Motion through Android sensor stack | PASS | 0 | Practice and live down/correct, up/pass: full flips plus a 22° resting hold moving to -12° and +56°, in both x-axis landscape directions |
| Release UI and saved history | PASS | 0 | Pause, review, theme switches, force-stop/relaunch history persistence |
| Real-time expiry | PASS | 0 | 30-second round plus 3-second countdown; completion observed after approximately 39 seconds including UI inspection overhead |
| Android crash log | Empty | 0 | adb logcat -d -b crash |
| Permissions | Offline only | 0 | VIBRATE and AndroidX non-exported receiver permission; no INTERNET, camera, microphone or storage permission |

## Critical defects caught and fixed

- Fixed a real corner/clue overlap in the compact double-font card. PlayingCard now measures the corner content before allocating the center, instead of relying on fixed padding.
- Investigated two apparent plain-label overflow failures: Compose 1.7.6 reconstructs semantics MultiParagraph at the parent's max width but reports the original smaller Text size. The test now checks complete unellipsized line extents against actual node bounds (at most 1px raster rounding); strict AutoWord overflow and real collision checks remain. This exposed, rather than concealed, the actual overlap fixed above.
- During the icon refactor, accessible live score stayed at zero while visible score updated. Tests caught it; the semantics now capture an immutable score value and match the displayed number after scoring.
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