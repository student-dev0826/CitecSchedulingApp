# Student booking calendar

Student **Book → Available slots** now shows a month grid instead of the professor/schedule dropdowns. Dates display their number of future open slots. Tap a highlighted date to expand its time slots, professor names, consultation categories and locations. Choose a slot explicitly to reveal the reason field and request button immediately beneath that slot.

Professor/department search filters both date counts and slot cards. Month navigation clears the selection. Empty results explain what to do next; Refresh availability reloads the server's posted slots. Custom requests remain available in their own mode. Refresh and foreground return revalidate availability, and a failed/stale booking reloads the calendar. Booking still submits to the existing server endpoint, which has the final say if another student already requested the slot.

No PHP, hosting configuration or database schema change is needed for this UI update. Install the newly built normal debug APK at `../app/build/outputs/apk/debug/app-debug.apk`. Its API URL remains the hosted API. The local-test app uses the existing `.automation` package and loopback backend, keeping hosted records and the original phone package separate.

## Validation

`BookingCalendarTest` covers grouping/counts across professors, chronological sorting, name/department filtering, omission of past/started/pending/accepted/cancelled rows, and malformed/student-attached open rows. Run:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

For the phone test, start the isolated backend described in `../backend/RECURRING_AVAILABILITY.md` and use the existing Appium server at `http://127.0.0.1:4723`. Set `TEST_PASSWORD` for the local fixture accounts, unlock the connected physical phone, and run:

```powershell
.\gradlew.bat :app:assembleDebug -PtestApiBaseUrl=http://127.0.0.1:8087/api/
node automation-tests/booking-calendar-ui.mjs
```

The phone test creates three labeled local slots across two dates. It checks counts, expanded time/category/professor information, search and empty states, switching dates, explicit selection and a real request, count reduction after booking, stale-slot recovery, and custom mode. It cancels its own fixture request and removes its own generated slots afterward. Credentials are provided at runtime; they are not embedded in the script. Evidence is saved under `artifacts/booking-calendar-<UTC timestamp>/`.

Build again without `-PtestApiBaseUrl` before distributing the normal hosted-API APK. Nothing is committed or pushed.

## Verified result — October 21, 2026

The normal build, Android lint, and five focused calendar unit tests passed. All nine physical-phone checks passed using the isolated backend. The fixture request was cancelled and test availability removed afterward.

Report: `artifacts/booking-calendar-2026-10-21T13-15-22-112Z/report.json`.
Screenshots: `calendar-counts.png` and `date-expands-time-category-professor.png` in that same folder.
The tested local APK is preserved as `artifacts/apks/student-calendar-local-debug.apk`; the normal hosted-API APK was rebuilt afterward at the standard path above.
