# Call alerts and controls — implementation and compatibility

Both APKs now build and pass lint, and core navigation has been tested in an isolated emulator. No actual cellular/VoIP or watch call-control test has completed, and the physical phone has not had this app installed by this task. Existing protocol/host tests and successful assembly do not establish that a KW66 call button works.

## Regular phone calls: Android Telecom companion access

On Android 12+, **Watch → Associate watch for cellular calls** requests Android's own companion-device association for the already selected KW66 address, using the watch device profile. Association approval is a phone OS consent screen; the app cannot approve it itself. The UI checks `TelecomManager.hasManageOngoingCallsPermission()` before describing call access as enabled.

The manifest declares `MANAGE_ONGOING_CALLS`, `REQUEST_COMPANION_PROFILE_WATCH` and a protected, non-UI `InCallService`. This follows Android's documented wearable companion route. It does not request default-dialer status. Android keeps the existing dialer UI, ringtone and emergency-call handling. No contacts lookup, call log dump, call screening/block list, call audio interception, recording or audio forwarding is implemented.

When Android supplies live Telecom calls, the app shows supplied caller display name or handle (or unavailable), state and applicable controls. A ringing call offers audio-only **Answer** and **Reject**; a live/held call offers **Hang up**. These dispatch Android `Call` methods and wait for Android state updates. They do not claim success merely because dispatch returned. Telecom can include eligible managed calls beyond cellular; this is not an API for controlling every third-party VoIP app. The role/association and OEM delivery path require testing on the target phone.

On Android 8–11 this direct companion route is unavailable in this app. The default phone app's exposed call notification may still provide manual actions through notification access. The app does not use deprecated global `acceptRingingCall` / `endCall`, hidden Telecom APIs or synthetic headset key presses as a fallback.

## WhatsApp and Messenger: current notification actions

With **Call alerts & phone controls** enabled and Android notification access granted, the listener recognizes `CATEGORY_CALL` notifications from WhatsApp/WhatsApp Business, Messenger/Messenger Lite and the current default phone package. It does not reinterpret ordinary messages containing the word “call”. Per-app exclusions apply.

For Android 12+ CallStyle notifications, public `EXTRA_CALL_PERSON`, `EXTRA_ANSWER_INTENT`, `EXTRA_DECLINE_INTENT` and `EXTRA_HANG_UP_INTENT` metadata provide the caller and exact semantic actions when present. If the provider omits those, only its actual exposed notification action labels are displayed. Localized labels are never guessed into answer/reject semantics. An incoming state is established by the structured answer intent; otherwise the app says the state is unavailable instead of inventing it.

The phone user can invoke an exposed action. Notification actions require the phone to be unlocked in this implementation, and the notification must still be active. A withdrawn PendingIntent, changed notification, locked phone or missing access produces an unavailable state instead of a fabricated result. Some actions may bring the original calling app to the foreground or require further interaction. Android/app background-launch and authentication restrictions still apply. No accessibility tapping or hidden VoIP APIs are used.

**Dismiss notification** appears only when Android marks that notification clearable. It means exactly that; it does not mean reject, hang up, mute the caller or stop ringing. CallStyle and ongoing notifications may not be dismissible. On older versions/legacy notification formats, actions may be available only as verbatim “App action…” buttons, or no action may be exposed at all.

## KW66 side

- Incoming calls queue a private, short-lived C5 type-0 caller alert; unknown-state call notifications use a generic type-4 message. Call alerts expire after 10 seconds, take priority over ordinary messages, and are removed when the call ends or its notification disappears. Active history fetch advancement pauses to prioritize a live call alert, and is explicitly reported incomplete.
- C1 04 is the reviewed source's call-end display command. It is sent through the same GATT FIFO after ringing ends, with a live-ringing check to avoid clearing a newer incoming call.
- **Watch hang-up controls calls**, initially off, maps only the source-derived `D1 02` event. One clear ringing call is eligible for reject; otherwise one clear active call is eligible for hang-up. Multiple ringing/active candidates, an unknown-state call notification or a missing semantic action cause no action. Call waiting selects the sole incoming call, not the already active call. Telecom and default-phone notification duplicates are suppressed when the Telecom handle is available.
- No KW66 answer event is established in the inspected protocol/captures. **Answer is a phone-app control, not a promised watch feature.** No C4 event was assigned to call answering or hang-up. Existing C4 camera/gesture findings remain unchanged.
- The app does not implicitly enable D7 or other unverified watch call settings. A watch that never emits D1 02 cannot control calls with this path until its actual supported behavior is established.
- A GATT ACK is not proof that the caller appeared, the watch rang, the watch button emitted an event or the phone rejected a call. Already in-flight BLE frames can finish just after a call ends; no claim of radio-level cancellation is made.

Caller identity, live Telecom handles and PendingIntents remain in process memory. Only the short-lived private outbox temporarily holds watch alert text. Caller strings and notification action titles are not written to diagnostics, exported or included in AI context. App restart requires Android/listener rebinding to recover live calls; stale action handles are not persisted.

## Required real-phone checks

1. Approve KW66 association; verify companion call access actually becomes true. Confirm the original dialer remains default.
2. Receive a regular phone call. Compare actual caller/state in the dialer, app card and watch. Test phone-app answer/reject and active-call hang-up separately.
3. Capture whether the KW66 emits D1 02 for its call control. Only then enable its mapping and test against the intended call, including call waiting and simultaneous WhatsApp/phone calls.
4. Receive WhatsApp and Messenger calls separately. Record whether CallStyle semantic intents are present and which actions work with this app version/device. Repeat with the phone locked; notification actions should refuse while locked.
5. Test notification updates, withdrawal, answered-call transition, caller redaction and app process recreation. Verify no expired incoming alert is delivered after reconnection and no ambiguous event acts on another call.
6. Confirm notification dismissal and call rejection remain distinct. Test whether app actions open another screen or require authentication; do not equate a sent PendingIntent with a completed call action.

## Sources checked 2026-11-11

- [Android: InCallService wearable companion access](https://developer.android.com/reference/android/telecom/InCallService)
- [Android: TelecomManager.hasManageOngoingCallsPermission](https://developer.android.com/reference/android/telecom/TelecomManager#hasManageOngoingCallsPermission())
- [Android companion-device pairing](https://developer.android.com/develop/connectivity/bluetooth/companion-device-pairing)
- [Android watch association profile](https://developer.android.com/reference/android/companion/AssociationRequest#DEVICE_PROFILE_WATCH)
- [Android Notification public CallStyle extras](https://developer.android.com/reference/android/app/Notification)
- [Android call-style notifications](https://developer.android.com/develop/ui/views/notifications/call-style)
- Reviewed [Gadgetbridge source snapshot](https://github.com/Kaboom780/Gadgetbridge/tree/083a0794bbe789b329686e7df01ea92cbc3dc028), `GloryFitSupport.kt`: `ACTION_CALL_HANGUP=02`, `CALL_END=04`, and C5 call type from `GloryFitNotificationType.kt`. These remain source-derived candidates on this watch.
