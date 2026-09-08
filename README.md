# Off the Top

An original, offline Android forehead-guessing party game. Three large decks, no ads, no accounts, no network access. Original content, not affiliated with Heads Up! or its publishers.

**[Download the Android APK](https://github.com/SeleniumEclipse/off-the-top/releases/tag/v1.0.0)** · Android 8.0+

![Off the Top deck selection](screenshots/home.png)

## Three original decks

| Deck | Cards | What's inside |
|---|---:|---|
| Wild World | 622 | Animals, plants, Earth, weather and space |
| Everyday Things | 617 | Objects, food, tools, clothing, instruments and transport |
| Do Your Thing | 605 | Activities, jobs, places, sports and situations |

**1,844 distinct cards**, without pop-culture themes. Day/night/system theme, motion practice, gentle tilts, touch-only option, sound, vibration, four round lengths, automatic pause and editable round review.

## Play

Choose a deck and round length. Hold the phone sideways at your forehead with the screen facing your friends. They describe or act out the card without saying its words. Tilt the **screen toward the floor** for correct; **toward the ceiling** to pass. Bring it upright between cards. Touch controls are also available. A short practice screen demonstrates both directions before a round.

Pause stops the clock. Leaving the app automatically pauses a running round. Review and correct your answers afterward. Unseen words are remembered separately for each deck, even across app restarts; when all have been used, the deck reshuffles.

## Build

Native Kotlin / Jetpack Compose; Java 17+, Android SDK 35, min Android 8. Gradle wrapper included. Build with `gradlew.bat :app:assembleDebug`. Tests: `gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintRelease` (emulator required for connected tests).

For a signed release supply `-PsigningProperties=C:/private/keystore.properties` to `:app:assembleRelease`. The supplied properties file contains storeFile, storePassword, keyAlias, keyPassword. The keystore path is relative to that properties file. Signing material is deliberately excluded from source archives.

## Testing strategy

Native Android only; no web backend, authentication, browser or database service. Test the pure round/timer and tilt engine with JUnit. Test all bundled decks for count, format and duplicates. Run Android Compose tests for deck selection, timed touch play, pause/resume, settings persistence, review corrections and replay. Install and launch the actual signed release on the Android emulator; inspect screenshots and crash logs. Test sensor values through emulator controls and deterministic gravity-vector tests. Real-hand comfort and actual speaker/vibration feel require a physical phone and are not implied by emulator tests.

## Theme and fonts

Matches the user's Set game: warm newsprint, Archivo Black and Barlow, solid ink borders, hard shadows, red/green/purple rules. Fonts are bundled under their accompanying SIL Open Font License.

## Validation

95 unit tests and 12 Android tests passed. The actual signed release was installed and tested with simulated hardware motion and a real-time countdown. See [the validation report](VALIDATION.md) for commands, coverage and physical-device limits.

![In-round gameplay](screenshots/playing.png)

![Round review](screenshots/results.png)