# Source optimization — October 22, 2026

## Changes

- Removed eight unreferenced Java files: the obsolete root-package networking/client response stack, unused JSON request models, and the empty `tst` class. Removed unused Retrofit overloads.
- Removed the unused OkHttp logging interceptor dependency. Kept directly used Gson, OkHttp, Retrofit, Material, AppCompat, and test dependencies.
- Removed 22 unused resources verified by lint and source searches (17 strings, four colors, one unused drawable; actual mipmap launcher assets remain).
- Reuse the Retrofit service proxy instead of constructing it on each access.
- Coalesce concurrent conversation reads per user without caching server results. Chat screens prevent overlapping polls, retry failed faculty loads, release view references, and reject callbacks from destroyed view generations.
- Preserve unchanged chat message bubbles, updating only changed/added rows. Read-status updates no longer force scrolling to the bottom. Hidden chat detail screens do not mark fetched messages read.
- Prevent current-tab reselection from recreating its fragment. Remove the duplicate faculty schedule request on initial view creation.
- Avoid redundant clock date and unread-badge view updates.
- Cache parsed schedule timestamps within each immutable response object; fresh responses still reflect changes. Reuse the month string while building booking calendar cells.

## Validation

`gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline --console=plain`

- Build passed. Ten unit tests passed, including four new request-sharing and timestamp regression tests.
- Lint: zero errors, 228 warnings (previous report: 254 warnings).
- Physical Android phone: installed the debug APK preserving app data, launched with Appium/UiAutomator2 at `http://127.0.0.1:4723`, and verified login controls and screenshot.
- Smoke report and screenshot: `artifacts/optimization-1792680249878/` (ignored local artifacts).
- Reusable smoke script: `optimization-smoke.mjs`; requires `ANDROID_HOME`, an existing debug APK, running Appium, and exactly one authorized phone.

## Limits

This is a targeted source optimization pass, not a measured startup/frame-time benchmark. The phone check covered installation and launch, not a full two-account messaging/booking regression. Existing calendar tests verify filtering and ordering. Long chat histories still use a LinearLayout and fetch the full conversation; pagination and RecyclerView virtualization remain future work. Release shrinking remains at its prior setting pending release-specific compatibility testing. PHP/database deployment and hosted records were not changed. Changes have not been committed or pushed.
