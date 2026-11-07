# Cozy MDM Check

A small Android app: tap one button to see whether the phone is under **MDM** (company device
management), has a **work profile**, or carries a **payment lock**. Handy when buying used phones
or phones in bulk.

**Download the APK:** [latest release](https://github.com/itlboy/mdm-check/releases/latest)

## What it checks

| Section | Contents |
|---|---|
| Management signs | Device admin apps (system apps such as Find My Device are ignored), organization-owned device (device owner), work profile, common MDM / payment-lock apps, blocked features (reset, uninstall, USB) |
| Device info | Name, model, Android version, security patch, build |
| Device history | Setup date (first boot or last factory reset), OS build date, uptime |
| Other security | Bootloader, root, developer options, USB debugging |

The app follows the phone language: Vietnamese on Vietnamese phones, English everywhere else.
All user-visible text lives in `Texts.kt`.

The app only goes online to ask GitHub for the latest version (green/red dot at the bottom).
Check results never leave the device.

## Limitations

- **Zero-touch is invisible** (MDM bound to the IMEI, only applied after a reset).
  To be sure: factory reset, then set the phone up on Wi‑Fi.
- **The IMEI cannot be read**: Android 10+ blocks regular apps from it.
- The setup date is estimated from the system; it is not the manufacturer's warranty activation date.
- Extra profiles from Dual Apps / Second Space (MIUI, ColorOS) are not treated as work profiles.
  An extra profile whose type cannot be determined is shown in amber, never red.

## Development

Kotlin + Jetpack Compose, minSdk 26. All verdict logic lives in `Rules.kt` (pure functions, tested on
the JVM); `Checker.kt` (`Collector`) only reads data from the device.

```sh
gradle testDebugUnitTest          # unit tests for Rules
gradle connectedDebugAndroidTest  # tests on a connected device / emulator
gradle assembleRelease            # APK in app/build/outputs/apk/release/
```

CI (`.github/workflows/build.yml`): every push to `main` runs the unit tests and the emulator tests
(Android 14, Pixel 6 size). When both pass and the app changed since the last release, it builds the APK
and publishes `v1.0`, `v1.1`, … automatically. CI-only or README-only changes do not publish a release.

Released APKs are signed with one fixed key (secrets `SIGNING_KEYSTORE_B64` + `SIGNING_PASSWORD`,
PKCS12, alias `mdmcheck`), so new versions install over old ones. If that key is lost, users must
uninstall the old app before installing one signed with a new key.
