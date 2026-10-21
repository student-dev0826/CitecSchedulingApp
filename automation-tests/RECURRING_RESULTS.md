# Recurring availability validation — October 21, 2026

Implemented in the professor's existing Set Availability flow, with a monthly calendar, weekday/time/date-range editor, rule editing/deletion, authenticated ownership, and database persistence.

- Normal Android debug build: passed.
- Android unit tests and `lintDebug`: passed.
- PHP syntax checks: passed for every API file.
- Pure recurrence checks: passed (October 2026 Tuesdays, Sundays, leap day, inclusive bounds, and eight invalid-input cases).
- Isolated MySQL/PHP API integration: 18 checks passed. Includes interval conflicts and rollback, student booking visibility, protection of pending/accepted appointments, stale-edit rejection, cancellation after deletion, declined-history replacement, authorization, login persistence, and logout revocation.
- Physical phone with Appium UiAutomator2: 7 checks passed. Professor login, monthly calendar, Tuesday creation, 9–10 AM calendar display, editing, persistence after logout/login, and deletion.

Evidence:

- API report: `artifacts/recurring-api/report.json`
- Phone report: `artifacts/weekly-ui-2026-10-21T12-19-26-255Z/report.json`
- Calendar screenshot: `artifacts/weekly-ui-2026-10-21T12-19-26-255Z/calendar-shows-tuesday-time.png`
- Tested local APK: `artifacts/apks/local-weekly-debug.apk`
- Normal hosted-API APK: `../app/build/outputs/apk/debug/app-debug.apk`

The test copy is installed as `com.example.citecschedulingapp.automation` and connects through ADB reverse to the isolated PHP/MySQL backend. The original phone package and hosted records were not changed. Test-generated future availability was removed after verification. Local PHP, MySQL and Appium helper processes remain available for inspection; their PIDs/logs are under `.runtime/`.

The hosted API has not been deployed. Follow `../backend/RECURRING_AVAILABILITY.md` to upload the backend changes, migrate automatically, install the normal APK and log in again. No commits or pushes were made.
