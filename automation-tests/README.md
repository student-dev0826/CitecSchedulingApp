# Android Appium smoke test

## Notification inbox UI checks

`node automation-tests/notification-ui.mjs` tests summary-only notification cards, full original messages and reasons, list/detail navigation, rotation, unknown messages, empty/error/retry states, and the student/professor navigation shortcuts. It serves synthetic notifications on loopback port 18088 with no database connection or hosted API calls. Build/install the automation APK with `-PtestApiBaseUrl=http://127.0.0.1:18088/api/`, forward `adb reverse tcp:18088 tcp:18088`, and run Appium on port 4729 (or set `APPIUM_SERVER`). `ANDROID_SERIAL` defaults to `emulator-5554`.

This is an Android UI change only: summaries are derived from existing notification messages, full text is preserved in the detail view, and the existing mark-read-on-open API behavior is retained. No backend or database migration is needed.

## Message action UI checks

`node automation-tests/chat-actions-ui.mjs` starts a loopback-only in-memory API on port 18087 and tests long-press Reply/Copy/Delete, clipboard paste, reply cancellation and rotation, failed-send draft retention, quoted reply rendering, delete confirmation, and both dashboard badge positions. It uses synthetic accounts and never accesses hosted data. Install the automation debug APK built with `-PtestApiBaseUrl=http://127.0.0.1:18087/api/`, forward `adb reverse tcp:18087 tcp:18087`, and run Appium on port 4727 (or set `APPIUM_SERVER`). `ANDROID_SERIAL` defaults to `emulator-5554`.

Quoted replies use a readable text quote in the existing message payload, so they survive reloads and remain readable on older clients without a server migration. Delete remains private to the current user. The separate `chat-delete-ui.mjs` suite retains the isolated real-backend deletion checks and now follows the long-press and conversation-options menus.

## Checks without login credentials

With the debug APK installed and Appium running, use `node automation-tests/unauthenticated-ui.mjs`. It checks login role switching, required fields, remember-me interaction, password-recovery navigation and validation, and registration navigation and validation. It submits no accounts or password-reset emails. Set `ANDROID_SERIAL` for the target device (default `emulator-5554`) and optionally `APPIUM_SERVER` (default `http://127.0.0.1:4723`). Start from the login screen without a remembered session. Reports, screenshots and UI hierarchies are saved under `artifacts/`.

The smoke/functional runner also accepts emulators when explicitly selected, for example `node automation-tests/smoke.mjs --udid emulator-5554 --functional`. Without `--udid`, it continues to select physical phones only. Emulator startup may require the extended UiAutomator2 startup timeout used by these runners.

For the new weekly availability feature, see [backend/RECURRING_AVAILABILITY.md](../backend/RECURRING_AVAILABILITY.md). It covers isolated local setup, recurrence API tests, the phone UI test, and hosting rollout. The earlier smoke/functional tests below describe the original launch checks.

## Read-only student and professor checks

Set `STUDENT_ID`, `STUDENT_PASSWORD`, `PROFESSOR_ID`, and `PROFESSOR_PASSWORD` in the current process environment, then run:

```powershell
node .\automation-tests\smoke.mjs --functional
```

Credentials are not hardcoded in the files. Login screenshots/hierarchies are skipped in functional mode. Appium's own server logging is controlled by your server and may log WebDriver input; protect those logs. Screens captured after login can contain profile information.

The suite checks role-specific login, navigation, student booking-mode switching, appointment filter interaction, professor availability/transfer form visibility, chat lists, profile identity, and logout. It never submits bookings, schedule changes, transfers or messages, and does not open conversations (which could mark messages read). These are UI checks, not proof of backend data correctness. Test results and screenshots use the same timestamped artifact folders as the smoke test.

The first functional run was blocked before login: the phone denied `android.permission.INJECT_EVENTS` on the first Appium tap. Device interaction must be enabled before these checks can run. The prior launch/screenshot smoke test passed independently. The original run also found that student Schedule had no navigation handler. After physical-device verification on October 22, the unused tab and weekly screen were removed; the dashboard shortcut now opens Appointments. The functional suite verifies that the unused tab is absent.

Run from the project root in PowerShell:

```powershell
.\automation-tests\run-smoke.ps1
# If multiple physical phones are connected:
.\automation-tests\run-smoke.ps1 -Udid 'YOUR_ADB_SERIAL'
```

Requires Node.js 18+, the project's existing Java/Gradle/Android SDK setup, an unlocked and authorized physical Android phone, and the existing Appium server at http://127.0.0.1:4723 with UiAutomator2 installed. No npm dependencies are needed: the script uses Appium's W3C WebDriver HTTP API directly. It does not start or stop the Appium server.

Application ID: `com.example.citecschedulingapp`. Launcher: `com.example.citecschedulingapp.LoginActivity` (confirmed from Gradle and the manifest, then checked on the installed APK).

The test builds `:app:assembleDebug`, installs with `adb install -r`, launches through Appium/UiAutomator2, and verifies the foreground package/activity and visible resource IDs. Login requires its title, identifier/password inputs and login button. An existing remembered session can instead load HomeActivity or FacultyHomeActivity, where the navigation bar is checked. This is a launch smoke test; it does not submit credentials or verify backend functionality.

Appium uses `noReset: true` and never clears app storage or uninstalls the application. A signing mismatch stops installation; uninstalling is not attempted. The application's own startup/session behavior still applies. UiAutomator2 may install its normal helper packages on the phone.

Each run writes build/install logs, a JSON report, screenshot and UI hierarchy under `automation-tests/artifacts/<UTC timestamp>/`. Screenshots and hierarchies may contain on-screen account data; review before sharing. Failed sessions also attempt to capture the screen. The app is left open after the session is released.

Only automation files are maintained here. Normal Gradle build outputs/caches are generated by building; source, XML layouts, Gradle configuration and Git metadata are not edited. Nothing is committed or pushed.

The connected phone restricts hidden-API policy changes. `appium:ignoreHiddenApiPolicyError: true` lets UiAutomator2 continue when those changes are denied; it does not grant secure-settings permission.

`appium:skipDeviceInitialization: true` skips Appium Settings helper initialization, because this phone rejects its install-time grant-all-permissions flag. This smoke test only needs UiAutomator2 launch, element inspection and screenshots; it does not need Settings-helper features. The script checks ADB connectivity itself. Unlock the phone before running.

Driver reference: https://github.com/appium/appium-uiautomator2-driver#capabilities
