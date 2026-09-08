# Off the Top 1.1.0 — Easier tilts, one accent

## What's changed

- **Tilt controls learn your starting angle.** Hold still at your forehead during the countdown, then nod down for correct or up to pass. You no longer need an almost perfectly upright hold or a large 43° flip.
- **Smaller motions:** 28° from your starting angle; 20° with Gentle tilts. Faster response, with protection against shaking and duplicate answers.
- **No lost returns between cards.** The game keeps tracking your return during feedback instead of resetting and ignoring it. Hidden cards still cannot score.
- **Live practice feedback:** readiness, up/down angle and a Reset tilt button. Pause/resume also resets your starting angle. Late forehead placement can finish settling after the countdown.
- **Just one accent: teal.** Navy ink and ivory paper, with the same bold fonts, sharp borders and printed feel. No red/green/purple deck colors. Night mode and the app icon match.

## Install

Download **Off-the-Top-1.1.0.apk** from the assets below and open it on your Android phone. **Install over the old version; no uninstall needed.** Your saved rounds, settings and played-card memory stay intact. If Android asks, allow installation from the browser or file manager used to open the APK. Requires **Android 8.0 or newer**. No account, internet connection, payment, ads, camera or microphone needed.

## Three complete decks — 1,844 original cards

- **Wild World — 622 cards:** animals, plants, weather, Earth and space.
- **Everyday Things — 617 cards:** objects, food, clothing, tools, instruments and transport.
- **Do Your Thing — 605 cards:** activities, jobs, sports, places and everyday situations. Great for charades too.

No pop-culture or celebrity decks. No duplicate topics within or across the three decks.

## How it plays

Choose a deck, hold the phone sideways against your forehead with the screen facing your friends, and hold still during the countdown. **Nod the screen down for correct, up to pass.** Return to your starting angle between tilts. You do not have to turn the phone fully flat. Large touch buttons are always available, and there's an optional touch-only mode.

- Practice both tilts before starting.
- 30, 60, 90 or 120-second rounds with a three-second ready countdown.
- Sound cues, vibration, gentle-tilt option and large self-fitting card text.
- Pause/resume; leaving the app automatically pauses the clock.
- Review every answer and tap to correct accidental scores.
- Last 20 rounds saved locally.
- Previously displayed cards are excluded until the deck is exhausted, including after restarting the app.
- Day, night and system themes.

## Checked before release

130 unit tests and 16 Android instrumented tests passed, including 108 long-card/layout cases. New regressions cover natural resting angles, smaller nods, fast returns during feedback, late placement, blocked hidden-card tilts, and different sensor sample rates. Text contrast passes checks in both themes. The signed update was installed over the old app on an Android 14 emulator and checked through actual simulated sensor events, touch gameplay, timer expiry, pause, review, themes and restart persistence.

**Please test both directions in the practice screen on your phone.** The old version passed idealized emulator tests but failed the user's physical playtest. These checks are more realistic and address reproduced faults, but they still do not prove reliability on the affected handset. Physical comfort, speaker volume and vibration strength also need hands-on confirmation.

This is an original game and card collection, not affiliated with Heads Up! or its publishers.