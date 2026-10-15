# Alarm Clock Pro

A lean Android clock app built around **labelled, temporary alarms**: one-off alarms ("dentist", "nap 20m")
clean themselves up after they ring instead of piling up in your alarm list.

Forked from [Fossify Clock](https://github.com/FossifyOrg/Clock) (GPL-3.0). The alarm engine follows Fossify's
approach (`setAlarmClock`, a foreground ringing service, reboot rescheduling); the UI is rewritten in
Jetpack Compose + Material 3 without the Fossify Commons library. Release APK ≈ 1.5 MB (Fossify: ≈ 9 MB).

## Features

- **Temporary alarms** — one-time alarms are temporary by default: after ringing they move to
  *Recently ended* (restorable) and are cleared after 7 days.
- **Quick add** — type `7:15 gym`, `nap 20m`, `in 45 min laundry`, `fri 9am dentist`, `weekdays 6:45 work`;
  a live preview shows how it was understood before you add it.
- Next-alarm card, skip next occurrence, snooze, swipe to delete with undo, per-alarm sound & vibration,
  gradual volume, auto-silence, missed-alarm detection after the phone was off.
- Timer (multiple, keypad entry), stopwatch with laps, world clock.
- Works with assistants via the standard `SET_ALARM` / `SET_TIMER` intents.

## Build

Requires JDK 17+ (Android Studio's bundled JBR works) and the Android SDK (compileSdk 37).

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/
./gradlew assembleRelease      # minified, signed with the debug key for personal installs
```

## Test

```sh
./gradlew testDebugUnitTest    # quick-add parser, next-trigger math, alarm lifecycle rules
maestro test .maestro/         # end-to-end on a running emulator/device (alarms really ring)
```

The Maestro flows wait for real alarms (about 1–2 minutes each). Compose test tags are exposed as
resource IDs (`testTagsAsResourceId`), so flows select elements by `id:`.

## License

GPL-3.0, see [LICENSE](LICENSE).
