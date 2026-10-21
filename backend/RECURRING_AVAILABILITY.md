# Weekly recurring professor availability

Open **Professor dashboard → Set Availability → Weekly schedules & calendar**. Select a month with the arrows and add a weekly schedule. Choose Monday–Sunday, start/end times, category, and location. The default date range is the entire displayed month; both dates can be changed for a custom inclusive range (up to one year). Add separate rules for different weekdays or multiple non-overlapping intervals on one day.

The month grid marks available dates green and appointments amber. Tap a date to see its exact times, status, and location. Each recurring rule has Edit and Delete controls. All dates/times use Asia/Manila. Past occurrences are skipped when generating; existing past entries remain visible for reference. For example, the October 2026 planner identifies October 6, 13, 20 and 27 as Tuesdays; saving on October 21 generates only the future October 27 occurrence.

## Persistence and conflicts

`cs_recurring_availability` stores rules and optimistic versions; generated rows go into the existing `cs_schedules` table with `recurrence_id`, so existing student booking, faculty schedules, dashboard counts and notifications continue to use the same records. Recurring save/delete is transactional. The API serializes scheduling writers with a MySQL named lock, checks actual interval overlap (adjacent intervals are allowed), and rolls back the entire save on a conflicting date. It never silently overwrites a conflict.

Editing regenerates future OPEN/unbooked occurrences only. An occurrence with an attached student keeps its date, time, metadata and status; that date is skipped for the edited rule. Deleting removes future unbooked occurrences and archives the rule, retaining bookings and past history. A student cancelling a protected occurrence after its rule was deleted or moved cannot reopen the removed availability. Cancelled/declined history is retained and does not prevent posting new availability solely because its old textual time label matches.

## Authentication and rollout

Password-verified login now issues an opaque, random, 30-day scheduling token; the database stores only its SHA-256 hash. Authenticated scheduling actions enforce the session's role and ID on the server. A caller cannot select another owner by changing `faculty_id`, `student_id`, or `recurrence_id`. Logout attempts server revocation and always clears local session state; offline revocation is best-effort. Session preferences are excluded from Android backups.

Deploy `api/auth.php`, `api/recurrence.php`, `api/login.php` and `api/schedules.php` together to the existing API directory. Keep the hosting's database credentials/configuration. `config/database.php` additionally supports `DB_PORT` for local tests; its production default remains 3306. On first use, the API adds the recurrence and auth tables and the nullable schedule association/index. The old exact-time unique index becomes a non-unique lookup index: interval checks under the writer lock now enforce conflicts while allowing cancelled history. The DB account needs CREATE/ALTER privileges for this migration. Back up the database before rollout as for any schema change.

`database.sql` describes a fresh install using the actual `cs_schedules` and `cs_notifications` names used by the API. Do not import the whole fresh-install script over hosting as a migration. No DROP TABLE or data reset is needed.

Install the updated Android build and log in again online. Existing tokens are absent in older app sessions, so scheduling asks for login again. Older APKs do not send the new token and must be updated alongside this API rollout. Other API files and login field names stay compatible.

The normal APK continues to use `https://citecscheduling.x10.mx/api/`. Hosted PHP files have **not** been uploaded by this task; there is no configured hosting deployment workflow. Local test success does not imply that the hosted feature is live.

## Local validation

Tests use an isolated MySQL data directory under `automation-tests/.runtime/mysql-data`, listening only on `127.0.0.1:3307`, and an official checksum-verified PHP runtime under `.runtime/php`. They do not connect to the hosted database or the existing MySQL service on 3306. `seed-recurring.php` refuses any other database name/port and creates local fixture accounts 9876/98765. Credentials come from `TEST_PASSWORD`; no password is embedded in test scripts.

Set `DB_HOST=127.0.0.1`, `DB_PORT=3307`, `DB_NAME=citec_recurring_test` and `TEST_PASSWORD` in the process environment. Start PHP with PDO MySQL and mbstring enabled, serving `backend/` at `127.0.0.1:8087`:

Alternatively, after setting `TEST_PASSWORD`, run `.\automation-tests\start-local-backend.ps1 -SeedAccounts`. It downloads/verifies PHP if absent, initializes a separate MySQL data directory if absent, and starts both local services. It refuses to replace a different process already using either port. It does not start Appium.

```powershell
$runtime = "$PWD\automation-tests\.runtime"
& "$runtime\php\php.exe" -d "extension_dir=$runtime\php\ext" -d extension=pdo_mysql -d extension=mbstring automation-tests/seed-recurring.php
& "$runtime\php\php.exe" -d "extension_dir=$runtime\php\ext" -d extension=mbstring automation-tests/recurrence-plan-test.php
# Keep the local PHP server running in another terminal:
& "$runtime\php\php.exe" -d "extension_dir=$runtime\php\ext" -d extension=pdo_mysql -d extension=mbstring -S 127.0.0.1:8087 -t backend
# Then run the API checks:
node automation-tests/recurring-api.mjs
```

For the physical-phone test, use the installed Appium UiAutomator2 driver at `http://127.0.0.1:4723`, forward `adb reverse tcp:8087 tcp:8087`, then:

```powershell
.\gradlew.bat :app:assembleDebug -PtestApiBaseUrl=http://127.0.0.1:8087/api/
node automation-tests/weekly-ui.mjs
```

The local API override is accepted only in debug builds and only for loopback URLs. It installs as `com.example.citecschedulingapp.automation`, leaving the production package/data separate. Tests create labeled fixture schedules and remove their own future unbooked rules on completion. Reports, UI hierarchies and screenshots are under `automation-tests/artifacts/`. Runtime binaries, database files, logs, and test APKs are local artifacts, not deployment source.

Rebuild without `-PtestApiBaseUrl` for the hosted API. Local background helper PIDs are recorded under `.runtime/`; only stop the recorded helpers after checking the corresponding process paths. Keep the server running while inspecting the local-test app. No Git commits or pushes are performed.
