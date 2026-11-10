# Validation and evidence boundaries

Both `coreDebug` and `gemmaDebug` have been assembled locally. Both Android lint variants pass with zero errors; nonfatal warnings remain. `apksigner verify` passed for the release-download debug APKs. Checksums accompany each GitHub release.

## Host checks

The standalone public suite uses synthetic protocol vectors and needs no private log or connected watch. It exercises valid/malformed packets, 10,000 randomized malformed inputs, unsigned step counts, missing values, historical timestamp offsets, Unicode message framing, synthetic sleep rollover, repeating reminder math, exact-package call matching and ambiguous-call rejection policy. Host SQLite checks cover schema constraints, deduplication, expiring/private outbox separation, live-call priority and task completion. Four bootstrap regression tests cover provider failure and checksum validation.

During development, an additional private 403-packet capture was replayed: source-derived decoding yielded 232 step records, 767 HR samples, 736 SpO₂ samples and one HR summary, with 1643 unique decoded kind/time keys. This is a replay of saved traffic, not proof of sensor accuracy or current native BLE operation. The underlying private capture is not published. Sleep stage packets were not present in that capture; sleep parser tests remain synthetic.

## Native runtime checks

A fresh isolated Android 11/API-30 x86_64 emulator installed and cold-launched the core APK. All five navigation screens opened. Reminder creation, process-restart persistence and completion/cancellation were checked. All four graph types, previous/next recorded-day navigation and a date without records were checked using synthetic data in the emulator only. No crash was recorded in the emulator crash buffer for those checks.

The native screenshots in `screenshots/` show emulator behavior, with explicitly synthetic graph/reminder values. The APK does not contain seeded demo health records. They are not screenshots from a connected KW66 session and do not establish physical phone compatibility.

## Still pending

- Physical KW66 connection, subscriptions, reconnect and OEM/Doze/background survival.
- Actual watch message rendering and notification vibration, including Unicode/font behavior.
- Live history timing and completion; real sleep stage names, duration units and midnight convention.
- Regular phone-call companion access, third-party call notification actions and a physical D1 02 watch event.
- Audible/repeating alarm operation on the phone and watch, particularly through background restrictions/reboot.
- Model import/loading, inference, cancellation/unload, peak RAM, heat and battery measurements on a physical phone.

Successful compilation and emulation do not turn these pending checks into device proof.
