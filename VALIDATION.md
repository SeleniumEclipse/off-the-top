# Release validation — Off the Top 1.0.0

## Environment and result

- Native Android/Kotlin/Compose; no backend, account or browser layer.
- Windows; JDK 21 compiling Java/Kotlin target 17; Android SDK 35; Gradle 8.11.1.
- Emulator: SetTest, Android 14 / API 34, 1080 × 2340 phone rotated to landscape, 420 dpi.
- Physical device: unavailable. Physical tilt comfort, actual audible output and haptic strength remain unverified.
- Overall automated result: **PASS**. Physical playtest remains appropriate for those hardware-dependent qualities.

## Checks

| Check | Result | Exit code | Evidence |
|---|---|---:|---|
| Pure engine and data tests | 95 passed, 0 failed | 0 | 50 round, 40 motion, 5 deck tests |
| Android UI tests | 12 passed, 0 failed | 0 | GameplayFlowTest, SettingsFlowTest, LifecycleFlowTest, CardLayoutTest |
| Long-card layout cases | 108 fitting renders, plus overflow/recovery checks | 0 | 36 longest entries × 3 viewport/font-size combinations; actual AutoWord renderer |
| Release lint | 0 errors; 10 advisory warnings | 0 | Version update notices, intentional landscape orientation, retained license resource |
| Signed release build | PASS | 0 | R8 minification and resource shrinking enabled |
| APK installation and cold launch | PASS | 0 | Actual signed release, not merely debug build |
| APK signature | PASS | 0 | apksigner verifies v2 signature, suitable for min Android 8 |
| Motion through Android sensor stack | PASS | 0 | Practice and live floor/correct and ceiling/pass events using emulator gravity vectors |
| Release UI and saved history | PASS | 0 | Pause, review, theme switches, force-stop/relaunch history persistence |
| Real-time expiry | PASS | 0 | 30-second round plus 3-second countdown; completion observed after 37.3 seconds including UI inspection overhead |
| Android crash log | Empty | 0 | adb logcat -d -b crash |
| Permissions | Offline only | 0 | VIBRATE and AndroidX non-exported receiver permission; no INTERNET, camera, microphone or storage permission |

## Critical defects caught and fixed

- Hidden cards could be scored by input arriving while feedback expired: engine now rejects that input, and actual rendered text must acknowledge visibility before scoring.
- A hidden next card could appear as unanswered if time ended during feedback: the current card is now null while feedback hides it.
- Score correction was saved but not redrawn: results now receive immutable answer/score values to invalidate Compose rendering.
- Long card text previously relied on iterative shrinking: the actual renderer now measures the fitting size before displaying or enabling input.
- Paused and completed rounds previously retained sensor and timer work: subscriptions now depend on the actual phase.

## Reproduce

Use the Gradle test commands in the README. The PowerShell script in tools/release-smoke.ps1 exercises the installed release through Android UI inspection and actual emulator acceleration values; no test-only control is exposed in the release application.

Unit tests cover precise deadline boundaries, timer overflow, cooldown, pause/resume and deck exhaustion. Instrumented lifecycle expiry tests advance the pure engine clock deterministically; the separate release smoke script also checks a real 30-second expiry with no injected time.

## Limits

- Android 14 emulator runtime verified; minimum API 26 and target API 35 validated by build, not exhaustively tested on every OS/device.
- Card layout samples the longest entries, not a visual inspection of every card on every device.
- In-progress rounds survive activity recreation and pause/resume, but not operating-system process termination. Settings, seen-card memory and completed rounds survive process termination.
- Deck content mixes easy and moderately difficult everyday subjects; it is not difficulty-filtered.