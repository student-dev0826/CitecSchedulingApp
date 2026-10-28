# Message and conversation removal

In a chat, tap **Remove message** under a message (or long-press its bubble), or tap **Remove conversation** at the top. Confirm **Remove for me**. Cancel leaves everything unchanged.

Removal affects only the authenticated account. It is saved in MySQL, survives logout/login, and does not erase the other participant's copy. Both incoming and outgoing messages can be removed. There is no undo or “unsend for everyone”. New messages arriving after the confirmed conversation snapshot remain visible. Hidden messages are excluded from previews and unread counts, and are not marked read by subsequent viewing.

Students still see the professor directory after clearing a conversation, with “Tap to start conversation...” for professors without visible history. Professors' conversation lists omit cleared conversations until a new message arrives.

## x10hosting update

1. Back up the current `api/chat.php`, then upload this project's `backend/api/chat.php` in its place.
2. Keep your existing `api/db.php` and database credentials. The endpoint supports `db.php` with `$pdo`; it falls back to `config/database.php` only when needed.
3. Keep `api/auth.php` from the weekly availability update. Your working login endpoint must continue returning `auth_token` from `cs_issue_token`.
4. The new `chat_message_hidden` table is created automatically on the first chat request. No existing message rows or booking tables are deleted. `backend/database.sql` includes the table for new installations; do not reimport the full SQL file over your hosted database.
5. Install the new normal debug APK, then sign out and sign in online to obtain a session token.

Deploy the APK and endpoint together: the new endpoint requires authentication for every chat action, so older APKs that omit the token must be updated. The new APK's removal buttons require the new endpoint. Local testing does not deploy anything to x10hosting.

## Verification

Verified on 2026-10-28 using the Medium_Phone emulator (`emulator-5554`), Appium/UiAutomator2, and the isolated local database:

- All 12 UI checks passed across student and professor accounts, including cancellation, message removal, conversation removal, preservation of the other participant's copy, and persistence after logout/login.
- All 10 backend checks passed.
- Normal debug build and all 10 unit tests passed. Android lint reported zero errors and 212 warnings.
- UI screenshots and the report are in `automation-tests/artifacts/chat-delete-ui-1793195839057/`; backend results are in `automation-tests/artifacts/chat-delete-api/report.json`.

The emulator initially lost its ADB reverse connection after restart; restoring port 8087 resolved login connectivity. The UI test now waits for the chat screen after tapping navigation to tolerate activity transitions. No hosted records were changed and nothing was deployed or pushed.

`automation-tests/chat-delete-api.mjs` checks authentication, forged actor rejection, invalid IDs, idempotent removal, sent/received removal, unread counts, read receipts, conversation snapshot boundaries, recipient preservation, and persistence.

`automation-tests/chat-delete-ui.mjs` runs on a physical phone or Android emulator through Appium at port 4723 using the separate `.automation` APK and isolated backend at `127.0.0.1:8087`. It checks both accounts' cancellation, single-message removal, conversation removal, and persistence after login. Supply `TEST_PASSWORD` through the environment. Set `ANDROID_UDID` to select a device explicitly (for example, `emulator-5554`) and run `adb -s <device> reverse tcp:8087 tcp:8087` before testing. It does not modify hosted conversations.
