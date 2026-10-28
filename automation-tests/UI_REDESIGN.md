# Android UI based on CC106-UI.pdf

Reference: `G:/Downloads/CC106-UI.pdf` (seven pages, inspected as rendered images).

## Design decisions

- Burgundy `#570D29` for section banners and selected navigation backgrounds.
- Yellow `#FFD63E` for primary actions, active navigation text/icons, and availability.
- Cream `#F6F2E9` for page backgrounds and inputs; ink `#111113` for dark panels and navigation; white for surfaces and text on burgundy.
- Android sans-serif with bold 26–28sp page titles, 20–23sp section titles, and 13–16sp supporting text. Text wraps for long real account names and categories.
- Login follows reference page 2; the dashboard follows page 3; appointment cards follow page 6; profile follows page 7. Booking, professor screens, and chat extend the same visual system to the app's existing functionality.
- Use a fixed, rounded dark navigation bar, burgundy selection, and yellow active icon/label. Forms and content scroll above it. System bars and keyboard use compatible window insets.

The reference's landing page, sample class data, nonfunctional campus links, and extra Schedule/Consultation tabs are not added. The current booking calendar, Chat tab, recurring availability, and message removal remain available. This is a native Android adaptation, not a web page replica.

## Scope and verification

No API contracts, database schema, authentication behavior, or booking rules are changed by the redesign. `UiUtil.applyWindowInsets` provides a shared system-bar/keyboard layout boundary. The app uses the reference's light palette consistently in both device appearance modes.

`ui-redesign.mjs` uses Appium/UiAutomator2 at `http://127.0.0.1:4723`, the `.automation` APK, and the isolated loopback backend. Supply `TEST_PASSWORD` at runtime and optionally `ANDROID_UDID`. Reverse device port 8087 before running. It creates two temporary local slots and one pending booking between the authorized fixture accounts, captures the main screens, and removes those fixtures afterward. It never connects to the hosted API.

The script covers login, student home, booking calendar, custom request, appointments, chat, and profile, plus professor home, schedules, availability, weekly calendar, transfer, chat, and profile. Each successful check saves a screenshot in its timestamped artifact directory.

## Results (2026-10-28)

- All 14 main-screen checks passed at 480dp width and again at 360dp width on the Medium_Phone emulator.
- Login, recovery, and registration visual checks passed at 360dp width using `--auth-only`; no registration or recovery request was submitted.
- All 12 student/professor chat-removal regression checks passed at 360dp width.
- Normal debug APK build and all 10 unit tests passed. Final lint: zero errors, 239 warnings. Whitespace validation passed.
- Main-screen screenshots: `artifacts/ui-redesign-1793196917933/` (480dp), `artifacts/ui-redesign-1793197205667/` (360dp).
- Final auth-screen screenshots: `artifacts/ui-redesign-1793197606552/`. Chat regression report: `artifacts/chat-delete-ui-1793197311173/report.json`.
- Normal hosted-service APK: `artifacts/apks/citec-pdf-ui-debug.apk`. Local fixture APK: `artifacts/apks/pdf-ui-local-debug.apk`.

Visual review corrected overlapping form hints and long registration labels, and kept scrolled content clear of the status bar. Test fixtures were removed. No hosted deployment, commit, or push was performed.
