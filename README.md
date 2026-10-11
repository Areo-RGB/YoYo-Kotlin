# Yo-Yo Fitness Tracker (Kotlin)

A native Android port of [YoYo-Flutter](https://github.com/Areo-RGB/YoYo-Flutter), written in Kotlin with Jetpack Compose. Behaviour, protocols, audio calibration, and the Carbon-inspired UI match the Flutter app.

## Features

- Yo-Yo IR1, Yo-Yo IR2, and Beep Test protocol switching, with an IR1 start-level picker.
- Live protocol clock driven by the bundled audio recordings. The recording's playback position is the master clock, so the on-screen phase stays aligned with the beeps athletes hear.
- Focus-mode live screen with a phase-coloured HUD and a full-height athlete grid: tap once to warn, tap again for out. Also undo, pause/resume, and finish with confirmation.
- Beep Test rule: two consecutive missed shuttles eliminate the athlete. Miss/Out are ignored during the start countdown.
- Ranking with shared ties, VO₂max estimates, and CSV export to the clipboard.
- Saved session history with a leaderboard you can switch between average and total distance.
- Roster and history persist locally (SharedPreferences, same JSON shape as the Flutter app).
- Responsive layout: a drawer on phones and a navigation rail with protocol tabs at ≥ 900 dp. Fullscreen (immersive) mode, sound mute, and keep-screen-on while a test runs.

## Project layout

```
app/src/main/java/com/example/yoyo/
├── domain/        Protocols, runtime derivation, models + JSON, leaderboard, avatars
├── audio/         AudioEngine interface and MediaPlayer implementation
├── AppController  State machine: roster, clock, audio sync, ranking, persistence
├── MainActivity   Activity + ViewModel; applies keep-screen-on and immersive mode
└── ui/            Compose screens (Roster, Live, Results, History, Settings) and shell
```

`AppController` holds all state as Compose snapshot state. Audio, storage, and the clocks are injected, so the controller is unit-tested on the JVM.

## Build & run

Requires JDK 17+ and the Android SDK (API 35).

```bash
./gradlew :app:assembleDebug        # or scripts/build-debug.sh
scripts/install-debug.sh            # install on all connected devices
```

## Verify

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug
```

The unit tests cover protocol schedules and the recorded-beep alignment, the leaderboard, and the controller: clock, audio drift correction, misses, ranking, undo, and persistence. Robolectric Compose UI tests drive the main flows at phone and tablet sizes and write screenshots to `app/build/screenshots/`.

See [docs/protocols.md](docs/protocols.md) for protocol details.
