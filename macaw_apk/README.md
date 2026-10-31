# Macaw Rescue Fieldbook v6 — Android APK source

Native Android Java WebView host for the **offline** manual-observation fieldbook. Bundles v1/v3/v4/v5 research excerpts and the v6 individual-learning supplement; no cloud AI, background camera, mini-PC or network connection needed. Package `au.com.macawrescue.fieldbook`. `minSdk=26`, `targetSdk=35`.

Features: manual photo/video chooser; video seeking, screenshot export, timestamped ABC logging; local bird profile, training, rewards, welfare history; human corrections with preserved prior revisions; first-confirmed-warning summaries; BORIS CSV export/import; JSON backup/restore; on-device reference search. Media is not retained as part of backup.

The full v6 FastAPI/Gemini/99-chunk SQLite server remains a separate optional package; this APK does NOT execute Python, run auto video inference, or fetch research automatically. When analysis is needed, attach the clip to ChatGPT manually and use the built-in prompt, or deploy the separate v6 server.

## Build

From `macaw_apk/` with Android SDK platform 35, Java 17 and Gradle 8.10.2 installed:

    gradle --no-daemon :app:assembleDebug

APK: `app/build/outputs/apk/debug/app-debug.apk` (debug-signed, sideload only).

## Privacy / data

Zero Android permissions requested. Document selection and saving use Android system pickers. Data is stored in Android WebView origin storage, and can be lost if the application is uninstalled or its data cleared. Export JSON backups regularly. The native JavaScript export bridge is only usable in the bundled `appassets.androidplatform.net` page; external links open the system browser.

## Limitations

- Android 16 physical device smoke test not yet recorded.
- WebView media codec support follows system capabilities; videos not decodable on the device cannot be annotated.
- The first warning and session comparisons summarize only **human-confirmed entered data**, not hidden psychological state.
- The APK does not include a medically validated welfare scoring tool.
- BORIS Android app is independent; the APK exports/imports BORIS-style CSV, not the BORIS software itself.