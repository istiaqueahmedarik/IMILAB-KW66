# Health graphs

Current/latest readings remain numeric text. Health history is now shown only as a native Canvas graph, replacing the per-record text-card lists:

- Steps: hourly bars, including visible markers for recorded zeroes.
- Heart rate: time series, with a break whenever consecutive samples are more than 10 minutes apart.
- SpO₂: percentage time series, with the same missing-data gap policy and accurately labeled axes.
- Sleep: dots for the recorded raw stage codes. No stage names, duration units or connecting intervals are invented.

Each graph covers one watch-local day. It opens on the latest recorded day; arrows select previous/next days with records, and tapping the date opens a date picker. Days without records show an empty graph. The latest-value text is independent of the selected history date, with its own timestamp. Fresh live HR is used for at most five minutes; otherwise the newest recorded history value is shown with its watch-local date. Today retains its textual current/latest summary.

Packet refreshes preserve scroll position so incoming data does not jump the user away from the graph. No chart library, online graph service or synthetic data path was added to the production app.

## Visual/runtime checks

All four chart types and day navigation were exercised in the isolated Android 11 emulator using a synthetic fixture stored only under `/tmp` and that disposable emulator's private database. The fixture had 24 hourly step samples, 122 HR samples, 122 SpO₂ samples (with deliberate gaps), and 16 raw sleep-stage points on each of two days. The date picker was also tested on a day without samples, which correctly rendered an empty graph. These are UI fixtures, not watch measurements or sleep validation.

Actual emulator screenshots with synthetic chart data:

- [Latest-value text](screenshots/emulator-health-latest.png)
- [Step bars](screenshots/emulator-chart-steps-synthetic.png)
- [Heart rate](screenshots/emulator-chart-hr-synthetic.png)
- [SpO₂](screenshots/emulator-chart-spo2-synthetic.png)
- [Sleep raw stages](screenshots/emulator-chart-sleep-synthetic.png)
- [Empty selected day](screenshots/emulator-chart-empty.png)

Existing private health data was not changed on any physical phone. The APK contains no seeded sample records. History source data and exported records remain unchanged; this update changes their presentation.
