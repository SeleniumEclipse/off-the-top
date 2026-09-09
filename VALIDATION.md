# Release validation — Off the Top 1.5.0

## Current change: six decks and shared played-card memory

- Added Characters (580), Silent Acting (534), and Food & Drink (550). The original three deck files remain unchanged. Total: 3,508 deck entries, 2,933 unique titles, including 1,089 new titles and 575 intentionally reused entries. [Exact accounting](DECKS.md).
- Full-size deck stacks now have swipe and arrow navigation: three per page when the home area is at least 650 dp wide, otherwise two. The current page survives navigation and activity recreation.
- Added original mask, silent-face and fork/glass category vectors. The existing green/cream/red card design remains, with no gradients, emoji, suits or outer table outline.
- Silent Acting explains that clue givers act silently while the guesser may speak. Its countdown repeats the clue-giver rule. No microphone permission or automated speech monitoring was added.
- Played-card memory is now shared by normalized title. Legacy per-deck sets migrate once; resetting keeps the migration marker; deck exhaustion clears only that deck's identities. A rendered visible word is still required before it is remembered or scored. Settings and completed-round history formats are unchanged.
- RoundEngine, TiltDetector and MotionFilter are unchanged. Existing deadline, hidden-input, motion, pause, review and persistence assertions remain.
- New integration tests cover migration and borrowed preference-set safety, actual cross-deck overlap/exhaustion/reset flows, each new deck's scoring and history, paging and both width branches, and new artwork. Layout tests cover long clues from all six decks, intact title lines, count/mark separation and complete duration labels at enlarged fonts.

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

Signed APK SHA-256: `8dc88cca742bc1a6fc155bd206ded11c868ad7c68edf1b98c49ffcf60409da9d`. The release uses this exact installed and tested APK, not a later untested rebuild.

## Checks

| Check | Result | Exit code | Evidence |
|---|---|---:|---|
| Pure engine, motion, theme, data and design tests | 140 passed, 0 failed | 0 | Existing engine/motion/contrast checks plus expanded six-deck count, overlap and design guardrails |
| Android UI, model and rendered-background tests | 38 passed, 0 failed, 0 skipped | 0 | Complete suite on the dedicated SetTest emulator; includes shared-memory and expansion flows |
| Shared memory and upgrade migration | PASS | 0 | Legacy union, locale-independent matching, no reimport after reset, cross-deck exclusion, selected-deck exhaustion, unrelated settings/history preserved |
| Six-deck picker and Silent Acting rules | PASS | 0 | Swipe/arrows, bounds, both 649/650 dp width branches, page retained after navigation/recreation, rules present only on the acting deck |
| Picker typography and round-length controls | PASS | 0 | Characters stays on one line; titles reserve count space; duration labels stay whole and clickable at 550/650/884 dp widths and 1/1.3/2 font scales |
| Copy and accessible outcomes | PASS | 0 | No repeated slogans or expanded diagnostics by default; icon-only answers keep accessible outcome descriptions and correction behavior |
| Flat backgrounds and vector icons | PASS | 0 | Actual pixel captures verify uniform opaque backgrounds, no one-second animation, solid card surfaces, text contrast and painted vectors |
| Native artwork and card theme | PASS | 0 | Mirrored approved category vectors, red backs, opaque cream faces, green/red feedback and no outer table outline |
| Enlarged-font corner separation | PASS | 0 | 400×180 actual card with doubled font scale; complete clues cannot overlap timer, score or corner marks |
| Long-card layout cases | 216 fitting renders, plus overflow/recovery checks | 0 | 72 longest entries across six decks × 3 viewport/font-size combinations; actual AutoWord renderer |
| Release lint | 0 errors; 10 advisory warnings | 0 | Eight dependency update notices, intentional landscape orientation, older-Android backup configuration advice |
| Signed release build | PASS | 0 | R8 minification and resource shrinking enabled |
| APK update installation and cold launch | PASS | 0 | Actual signed release installed over previous version without clearing data |
| APK signature | PASS | 0 | apksigner verifies v2 signature, suitable for min Android 8 |
| Motion through Android sensor stack | PASS | 0 | Practice and live down/correct, up/pass: full flips plus a 22° resting hold moving to -12° and +56°, in both x-axis landscape directions |
| Release UI and saved history | PASS | 0 | All six packaged decks, new artwork/rule screens, correct/pass scoring, pause, review, theme switches and force-stop/relaunch history persistence |
| Real-time expiry | PASS | 0 | 30-second round plus 3-second countdown; completion observed after 37.6 seconds including UI inspection overhead |
| Android crash log | Empty | 0 | adb logcat -d -b crash |
| Permissions | Offline only | 0 | VIBRATE and AndroidX non-exported receiver permission; no INTERNET, camera, microphone or storage permission |

## Critical defects caught and fixed

- Signed-APK screenshot inspection caught the Characters title splitting inside the word and duration labels wrapping. Titles now shrink to their intentional line count, and durations receive dedicated width or their own row. Regression tests require complete, single-line labels rather than merely visible text.
- New deck titles could consume the height needed by their card count at enlarged fonts. Count height is now measured and reserved before choosing title size; titles cannot cover counts or corner marks.
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

Use the Gradle test commands in the [README](README.md). Set `ANDROID_SERIAL` to the dedicated test emulator before connected tests; for this run it was `emulator-5554` (SetTest). The [release smoke script](tools/release-smoke.ps1) accepts `-Serial` or uses `ANDROID_SERIAL`, checks that the target is an emulator, and exercises the installed release through UI inspection and actual acceleration values. No test-only control is exposed in the release application.

An early connected run inadvertently selected both SetTest and another project's emulator and failed. That run is not counted as passing evidence. The complete final suite ran only on SetTest and exited 0 after the layout fixes; no other project's emulator was used for final validation.

Unit tests cover precise deadline boundaries, timer overflow, cooldown, pause/resume and deck exhaustion. Instrumented lifecycle expiry tests advance the pure engine clock deterministically; the separate release smoke script also checks a real 30-second expiry with no injected time.

MotionFilterTest runs complete filtered down/up traces at 5, 10, 20 and 50 ms sample intervals. Detector tests use continuous streams instead of pretending two readings a long time apart prove stability. MotionFlowTest exercises actual AppModel input gates and one real-activity calibration lifecycle; its isolated model streams are **not** hardware tests. The release smoke script separately exercises Android's real sensor listener with emulator-injected acceleration and checks the simplified UI through visible text and accessibility descriptions. It now pins commands to the emulator so connected physical phones are untouched.

## Limits

- Android 14 emulator runtime verified; minimum API 26 and target API 35 validated by build, not exhaustively tested on every OS/device.
- Card layout samples the longest entries, not a visual inspection of every card on every device.
- In-progress rounds survive activity recreation and pause/resume, but not operating-system process termination. Settings, seen-card memory and completed rounds survive process termination.
- Deck familiarity varies by age, interests and region; content is not difficulty-filtered or children-only. Characters contains fictional pop culture, including names from mature works, and Food & Drink includes alcoholic drinks. See [content limits](DECKS.md).