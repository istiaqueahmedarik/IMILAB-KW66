# Source map

```text
MainActivity / Glass                 native screens, permissions, scan/device selection
          ↓ explicit intents
WatchService                        foreground connectedDevice owner
  reconnect → discover → CCCD FIFO → command/message FIFO
  incoming packet → Protocol → Store

NotificationBridge → private outbox → WatchService
ScheduleReceiver   → phone notice + private outbox
                  → RingingService (persistent phone ring + watch nudges)

LocalAi (core or gemma flavor) ← selected Store context only
          → JSON proposal → MainActivity field/scope checks → explicit apply
```

- `Protocol.java`: Android-independent decoder, byte framing and clock command. Tested with captured traffic and synthetic malformed/sleep/Unicode cases.
- `Store.java`: SQLite health records, diagnostics, expiring message outbox and todos. Health records use `(kind, local_time)` replacement to deduplicate repeated syncs. Watch timestamps are wall-clock strings; receipt timestamps are phone epoch milliseconds. The data is for one selected watch; switching between multiple watches is not a multi-device history feature.
- `WatchService.java`: one main-thread GATT queue for descriptor and characteristic operations. Ten-second operation and thirty-second connection timeouts close the GATT client before retrying. Commands use the default 20-byte payload rather than relying on MTU negotiation. Unexpected drops retry from 2 seconds up to 120 seconds. An explicit Disconnect disables reconnect. All callbacks from stale GATT objects are ignored.
- History transfers are serialized. A timeout reports incomplete sync and stops advancing the fetch sequence. Individual type buttons let a user test HR/SpO₂/sleep even if this firmware does not emit a step terminator. Notifications wait while a history fetch is active.
- Outbox has a 15-minute delivery deadline and 50-row cap. Alarm nudges collapse to one pending row. A multipart message is claimed before its first write, so partial transfers are reported rather than blindly replayed from the middle or duplicated after a process crash. There is deliberately no false watch-display delivery receipt. Queue expiry/drop is a bounded best-effort forwarding policy, not guaranteed notification delivery.
- `NotificationBridge.java`: notification access is granted in Android settings; forwarding starts only when its switch is enabled. All packages are included by default, excluding this app and group summaries. Seen package names populate an exclusion list. Up to 100 recent content strings are held transiently for update deduplication; queue text is app-private and not included in logs/export.
- `ScheduleReceiver.java` / `SchedulePolicy.java`: persistent Android AlarmManager schedules with exact access checks and inexact fallback; restore after boot/time change. Fixed-period repeats skip missed occurrences. Delivered one-shot todos persist visibly, without being rescheduled on every app start.
- `RingingService.java`: foreground media playback, an active-only partial wake lock and 30-second watch message nudges. The active flag is committed before ringing starts. In-app Stop clears the flag, releases resources and removes pending alarm nudges. A watch packet, swiping a notification or completing an unrelated todo does not stop this loop. Android system stop/force-stop/reboot can still interrupt it.
- `Calls.java` / `CellularCalls.java` / `CallPolicy.java`: optional live Telecom companion handles and call-notification intents, public Android watch association, caller cards and guarded call actions. Caller details are transient and excluded from AI/export. A unique-target policy guards source-derived D1 02 watch hang-up. See CALLS.md.
- `WatchActions.java`: optional D1 music/call actions; no gesture/camera mode manipulation.
- `AiEngine` / flavor-specific `LocalAi`: core carries a clear unavailable state; Gemma uses on-demand native engine/conversation allocation, bounded generation and cancellation. No automatic model tools or arbitrary GATT writes are registered.

The Kotlin adapter is the only optional inference layer; the Android UI/background/protocol implementation is Java. The app declares Android companion ongoing-call access and watch-profile association for the optional call controls. It does not request contacts lookup, SMS sending, call audio recording, GPS background access or a continuous microphone. App backup is disabled. Export is explicitly selected through Android's document picker and contains health data and todo titles.

The build scripts keep downloaded tools and Gradle state inside this project. Host tests do not instantiate Android services or prove system permission/Doze behavior.
