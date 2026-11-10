# KW66 feature audit

This audit uses the local Gadgetbridge/GloryFit source review and existing saved packets, not a newly connected watch. General GloryFit support is not a hardware capability guarantee for KW66 firmware `RH192POAV004473`. No watch settings or firmware were changed by this Android task.

| Capability | Evidence | This app |
|---|---|---|
| Firmware and battery | A1/A2 physically observed in the earlier logger session | Read on connection, display reported values |
| Steps history | 232 18-byte B2 packets in saved capture; repeated requests mean duplicates | Source-derived decoder, timestamp deduplication, raw counters/subfields retained |
| HR history | 72 F7 07 records; 767 non-missing samples under reviewed layout | Decoder and timestamped history |
| SpO₂ history | 76 34 FA 07 records; 736 non-missing samples under reviewed layout | Decoder; actual sensor accuracy/capability not independently verified |
| Sleep | 31 02 end observed; no session header or stage packets in saved capture | Synthetic-tested session/stage parser. Date convention tentative; no duration-unit or stage-name claims |
| Live heart rate | Earlier capture has E5 11 measurements; F7 04 max/min/average summaries | Decode/store observed formats; no undocumented command to force continuous acquisition |
| All-app notifications | Reviewed C5 multipart UTF-16BE protocol; later log includes C5/AB ACK-like responses but no independent display proof | Native notification listener and private C5 message queue. ACKs do not prove display |
| Custom messages / vibration | Same C5 + AB protocol | Explicit send control; unsigned 1-byte byte length bounded to 240 |
| Camera gesture | C4 01/02/03 physically observed earlier | Kept in original gesture task; no automatic camera mode or gesture commands from this app |
| Music controls | Reviewed D1 07/08/09/0D/0E event map | Optional dispatch to active Android media session, no phone playback test yet |
| Music metadata / phone volume display | Reviewed 3A and D1 volume writes | Audited, not implemented |
| Find watch | Reviewed 8-byte AB vibration pattern | Explicit control; physical behavior untested |
| Find phone | Reviewed D1 0A events and acknowledgments | Retained diagnostically; phone ringing from watch not assigned |
| Set clock | Reviewed A3 year/month/day/hour/minute/second | Explicit button only; no implicit time/settings overwrite |
| Watch-native alarm slots | Reviewed 9-byte AB scheduled alarm format | Not programmed. Required repeating alarm is a phone-owned persistent ring + watch-message nudges; it cannot continue watch delivery with phone disconnected |
| Todo/reminder calendar | Phone-level feature; no watch task database protocol established | Persisted in app, Android alarms trigger phone/watch messages |
| User profile, goals, raise-to-wake, HR alerts | Reviewed A9 user settings and 3F goals, model-specific layouts | Audited; not written without a validated KW66 settings schema |
| Automatic HR / SpO₂ sampling | Reviewed F7 and 34 configuration commands | Audited; not enabled automatically |
| Sedentary reminder | Reviewed D3 settings | Use app-level scheduled reminders instead; no native setting write |
| Language / units | AF / A0 in reviewed source | Audited; not changed |
| Weather / forecasts | CB encoding in reviewed source | Audited; no network/location source or weather writes implemented |
| Calls / SMS quick replies | C1 call status, D1 02 hangup, 45 target, 46 canned text, 52 reply, D7 call reject settings | Telecom companion call access and exposed WhatsApp/Messenger notification actions are now implemented in source; see CALLS.md. Optional D1 02 reject/hang-up mapping requires a unique target and real watch validation. No established watch answer event. No SMS sending, quick replies, audio capture or contacts lookup |
| Contacts / SOS | Reviewed 37 contact-transfer paths | Not implemented or sent |
| Watchfaces | Hello Imilab listing claims support; broad app claims do not establish safe KW66 upload format | No upload/editor |
| Firmware update / reset | Factory reset AD exists in reviewed source; safe compatible OTA/recovery not established | No reset, firmware flash or destructive commands exposed |
| Raw accelerometer / gyro / PPG | No raw stream established in inspected source/captures | No claim or fabricated sensor readings |
| Running AI on the watch | No application runtime established on KW66 | AI runs optionally on the Android phone, not the watch |

Protocol reference: GloryFitSupport.kt, GloryFitFetcher.kt and GloryFitNotificationType.kt in the [reviewed Gadgetbridge source snapshot](https://github.com/Kaboom780/Gadgetbridge/tree/083a0794bbe789b329686e7df01ea92cbc3dc028). Attribution and licensing are recorded in [NOTICE](../NOTICE).

The native app stores a bounded 5000-packet diagnostic history and 200 connection events. It retains unknown received BLE packets from subscribed known services, except private notification echoes/quick replies are redacted. This is not radio capture, all watch internals, or a guarantee that firmware transmits every measurement. No notification content is sent to the AI context or exported.
