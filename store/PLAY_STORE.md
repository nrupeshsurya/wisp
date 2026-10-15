# Play Store readiness — Wisp

Status: **ready to submit, not submitted.** Package: `io.github.nrupeshsurya.wisp` (permanent once published).

## What's ready

| Item | Where |
|---|---|
| Signed app bundle | `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab` |
| Title, short & full description, release notes | `fastlane/metadata/android/en-US/*.txt`, `changelogs/1.txt` |
| Icon 512×512, feature graphic 1024×500 | `fastlane/metadata/android/en-US/images/` |
| 5 phone screenshots (1080×1920) | `fastlane/metadata/android/en-US/images/phoneScreenshots/` |
| Privacy policy text | `PRIVACY.md` (needs a public URL and a contact email — see below) |

The `fastlane/metadata` layout is what `fastlane supply` uploads, if you automate later.

Regenerate graphics: `maestro test store/screenshots.yaml --test-output-dir /tmp/shots` (with the debug
build on an emulator), then `python3 store/make_graphics.py /tmp/shots/<run>/screenshots/takeScreenshot`.
Captions live in `store/captions.txt`.

## Signing — back this up now

- **Upload key:** `~/keys/wisp/upload-keystore.jks` (alias `upload`); passwords in `~/keys/wisp/keystore.properties`
  (also copied to the repo root as git-ignored `keystore.properties`, which the build reads).
- Enrol in **Play App Signing** (the default): Google holds the app signing key; this is only the upload key.
  If it is ever lost, Play support can reset it — but back up the folder and the password (password manager).
- Without `keystore.properties` the release build falls back to the debug key (fine for local testing only).
- Play-installed and sideloaded builds will have different signatures; uninstall one to install the other.

## Before you submit

1. **GPL source availability.** Wisp is GPL-3.0 (forked from Fossify Clock). Distributing it on Play means
   offering its source to users — make the GitHub repo public (or publish the source elsewhere) first.
2. **Privacy policy URL.** Replace `<CONTACT_EMAIL>` in `PRIVACY.md` and host it publicly (public repo file,
   GitHub Pages, or a public gist). A private-repo link won't work.
3. **Test on a real phone** (locked screen, reboot, battery saver), and decide the version
   (`VERSION_NAME`/`VERSION_CODE` in `gradle.properties`; currently 0.1.0 / 1).
4. **Developer account:** play.google.com/console, one-time US$25 fee, identity verification.
5. **Closed testing requirement:** new personal developer accounts must run a closed test with at least
   12 opted-in testers for 14 days before production access (check the current rule in Play Console).

## Play Console answers

**App details:** category *Tools*; free; no ads; contact email of your choice.

**Content rating questionnaire:** no violence, sexual content, profanity, drugs, gambling, user interaction,
data sharing, or location → expect *Everyone / PEGI 3*.

**Target audience:** 13+ (don't target children; avoids the Families policy requirements).

**Data safety:**
- Does the app collect or share user data? **No.** (No internet permission — data never leaves the device.)
- Data deletion: not applicable (nothing collected); local data is removed on uninstall.

**Permission declarations** (Policy → App content):
- *Exact alarms* (`USE_EXACT_ALARM`): "Wisp is an alarm clock; scheduling user-set alarms to ring at an exact
  time is its core functionality."
- *Full-screen intent* (`USE_FULL_SCREEN_INTENT`): "Shows the ringing alarm/timer screen over the lock screen
  so the user can snooze or dismiss it — core alarm clock functionality."
- *Foreground service — special use* (`FOREGROUND_SERVICE_SPECIAL_USE`): "While an alarm or finished timer is
  ringing, a foreground service plays the alarm sound, vibrates and keeps the ringing notification until the
  user dismisses or snoozes it. It runs only while ringing (auto-silenced after the configured time)."
  Play may ask for a short screen recording of this flow — record one with
  `adb shell screenrecord /sdcard/ring.mp4` while an alarm rings.

## Verified so far (emulator, API 36)

62 unit tests; Maestro flows for the temporary-alarm lifecycle, snooze, repeating alarms + skip, timer,
stopwatch and world clock; ringing on a locked/sleeping device; rescheduling after a real reboot; release
(R8-minified) build; no INTERNET permission in the merged manifest.
