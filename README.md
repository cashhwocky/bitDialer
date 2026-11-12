# bitDialer

bitDialer. For bitDroid.

The phone app for bitDroid (Pixel 10, Android 17), built to match bitMessages: AMOLED black, blue accent,
Monet glow (on by default), Kotlin + Jetpack Compose, no servers.

## Features

- Works as the **default phone app** (`ROLE_DIALER`): `ACTION_DIAL` / `tel:` handling and an `InCallService` that Telecom hands every call to
- Calls screen: starred contacts as big favorites, recent calls as glass cards (or a frosted panel), missed calls in red, contact search
- Keypad with tones, contact match, long-press 0 for +, long-press 1 for voicemail
- Incoming call: full screen over the lock screen, slide to answer, decline, reply by text, silence
- In call: timer, mute, keypad (DTMF), speaker / Bluetooth, add call, hold, merge / swap, SIM picker
- Works before the first unlock after a reboot (direct-boot aware call service and screen)
- Predictive back gesture

## Install

Grab `bitDialer-x.y.z-release.apk` from [Releases](../../releases), open it and tap **Set as default**.

## Build

CI builds every push. Releases are published by running the **Build APK** workflow with a `release_tag`
(for example `v0.1.0`); the tag must match `versionName` in `app/build.gradle.kts`.

## Shipping in bitDroid

See `rom/`: an `Android.bp` prebuilt module, a `config_defaultDialer` overlay and a default-permissions file.
