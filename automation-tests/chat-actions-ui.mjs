// Uses an isolated automation APK and in-memory API fixtures, never hosted data.
import http from 'node:http';
import assert from 'node:assert/strict';
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';

const pkg = 'com.example.citecschedulingapp.automation';
const output = path.resolve('automation-tests/artifacts', `chat-actions-${Date.now()}`);
mkdirSync(output, { recursive: true });
let session, nextId = 3, failNextSend = false;
const results = [];
const messages = [
  { message_id: '1', sender_id: 'PROF-42', sender_name: 'Demo Professor', sender_role: 'FACULTY', recipient_id: 'DEMO-STUDENT', recipient_name: 'Demo Student', message_text: 'Please bring your proposal.', timestamp: '09:00 AM', is_read: 1 },
  { message_id: '2', sender_id: 'DEMO-STUDENT', sender_name: 'Demo Student', sender_role: 'STUDENT', recipient_id: 'PROF-42', recipient_name: 'Demo Professor', message_text: 'Thank you, professor.', timestamp: '09:01 AM', is_read: 1 }
];
const hidden = { student: new Set(), faculty: new Set() };
const sent = [];
const server = http.createServer(async (req, res) => {
  let raw = ''; for await (const chunk of req) raw += chunk;
  const f = Object.fromEntries(new URLSearchParams(raw));
  const role = f.auth_token === 'faculty' ? 'faculty' : 'student';
  let reply = { success: true, message: 'OK', data: [] };
  if (req.url === '/api/login.php') {
    const faculty = f.role === 'FACULTY';
    reply = { success: true, message: 'OK', auth_token: faculty ? 'faculty' : 'student', user: {
      user_id: faculty ? 42 : 41, student_id: faculty ? 'DEMO-FACULTY' : 'DEMO-STUDENT',
      first_name: 'Demo', last_name: faculty ? 'Professor' : 'Student', full_name: faculty ? 'Demo Professor' : 'Demo Student',
      email: faculty ? 'professor@example.test' : 'student@example.test', role: faculty ? 'FACULTY' : 'STUDENT'
    } };
  } else if (req.url === '/api/schedules.php') {
    if (f.action === 'list_faculty') reply.data = [{ faculty_id: 42, full_name: 'Demo Professor', department: 'CITEC' }];
    if (f.action === 'list_notifications') reply.data = [{ notification_id: 1, message: 'Demo notification', is_read: 0, created_at: '2026-11-22' }];
  } else if (req.url === '/api/chat.php') {
    const visible = () => messages.filter(m => !hidden[role].has(m.message_id));
    if (f.action === 'fetch') reply.data = visible();
    if (f.action === 'conversations') {
      const last = visible().at(-1);
      reply.data = last ? [{ contact_id: role === 'faculty' ? 'DEMO-STUDENT' : 'PROF-42', contact_name: role === 'faculty' ? 'Demo Student' : 'Demo Professor',
        last_id: Number(last.message_id), last_message: last.message_text, last_sender_id: last.sender_id, last_time: '09:01 AM', unread: 0 }] : [];
    }
    if (f.action === 'send') {
      if (failNextSend) { failNextSend = false; reply = { success: false, message: 'Demo send failure. Please retry.' }; }
      else { const m = { message_id: String(nextId++), ...f, timestamp: '09:02 AM', is_read: 1 }; messages.push(m); sent.push(m); reply.data = { message_id: Number(m.message_id) }; }
    }
    if (f.action === 'delete_message') hidden[role].add(f.message_id);
    if (f.action === 'delete_conversation') messages.filter(m => Number(m.message_id) <= Number(f.through_message_id)).forEach(m => hidden[role].add(m.message_id));
  }
  res.writeHead(200, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(reply));
});
await new Promise((resolve, reject) => { server.once('error', reject); server.listen(18087, '127.0.0.1', resolve); });
const base = process.env.APPIUM_SERVER || 'http://127.0.0.1:4727';
async function request(method, route, body) {
  const r = await fetch(base + route, { method, headers: { 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(240000) });
  const j = await r.json(); if (!r.ok || j.value?.error) throw Error(j.value?.message || r.statusText); return j.value;
}
const api = (method, route, body) => request(method, `/session/${session}${route}`, body);
const key = 'element-6066-11e4-a52e-4f735466cecf';
const element = async (using, value) => (await api('POST', '/element', { using, value }))[key];
const find = id => element('id', `${pkg}:id/${id}`);
const byText = text => element('xpath', `//*[@text=${JSON.stringify(text)}]`);
const tap = async id => api('POST', `/element/${await find(id)}/click`, {});
const tapText = async text => api('POST', `/element/${await byText(text)}/click`, {});
const source = () => api('GET', '/source');
const shown = async id => api('GET', `/element/${await find(id)}/displayed`);
const type = async (id, text) => { const e = await find(id); await api('POST', `/element/${e}/clear`, {}); await api('POST', `/element/${e}/value`, { text }); };
async function wait(fn) {
  let error;
  for (let i = 0; i < 35; i++) { try { return await fn(); } catch (e) { error = e; await new Promise(r => setTimeout(r, 300)); } }
  throw error;
}
async function capture(name) {
  writeFileSync(path.join(output, `${name}.png`), Buffer.from(await api('GET', '/screenshot'), 'base64'));
  writeFileSync(path.join(output, `${name}.xml`), await source());
}
async function check(name, fn) { await fn(); results.push(name); console.log('PASS', name); }
async function hold(text) {
  const e = await element('xpath', `//*[@text=${JSON.stringify(text)}]/ancestor::*[@long-clickable="true"][1]`);
  await api('POST', '/execute/sync', { script: 'mobile: longClickGesture', args: [{ elementId: e, duration: 800 }] });
  await wait(() => find('btnReplyMessage'));
}
async function login(faculty) {
  await tap(faculty ? 'btnRoleFaculty' : 'btnRoleStudent');
  await type('etIdentifier', faculty ? 'DEMO-FACULTY' : 'DEMO-STUDENT');
  await type('etPassword', 'demo-password');
  try { await api('POST', '/appium/device/hide_keyboard', {}); } catch {}
  await tap('btnLogin'); await wait(() => find('bottomNavigation'));
  try { const e = await element('id', 'com.android.permissioncontroller:id/permission_deny_button'); await api('POST', `/element/${e}/click`, {}); } catch {}
}
try {
  const created = await request('POST', '/session', { capabilities: { alwaysMatch: {
    platformName: 'Android', 'appium:automationName': 'UiAutomator2', 'appium:udid': process.env.ANDROID_SERIAL || 'emulator-5554',
    'appium:appPackage': pkg, 'appium:appActivity': 'com.example.citecschedulingapp.LoginActivity',
    'appium:noReset': true, 'appium:forceAppLaunch': true, 'appium:skipDeviceInitialization': true,
    'appium:ignoreHiddenApiPolicyError': true, 'appium:uiautomator2ServerLaunchTimeout': 120000, 'appium:adbExecTimeout': 120000
  }, firstMatch: [{}] } }); session = created.sessionId;
  await api('POST', '/timeouts', { implicit: 800 });
  await login(false);
  await check('student-badge-attached-to-bell', async () => {
    await wait(async () => assert.equal(await shown('tvNotifBadge'), true));
    const bell = await api('GET', `/element/${await find('ivNotification')}/rect`);
    const badge = await api('GET', `/element/${await find('tvNotifBadge')}/rect`);
    assert.ok(badge.y > bell.y && badge.y + badge.height < bell.y + bell.height);
    await capture('student-badge');
  });
  await tap('nav_chat');
  await wait(async () => { const e = await element('xpath', '//*[contains(@text,"Demo Professor")]/ancestor::*[@clickable="true"][1]'); await api('POST', `/element/${e}/click`, {}); await find('etMessageInput'); });
  await wait(() => byText('Please bring your proposal.'));
  await check('no-remove-buttons-under-bubbles', async () => { assert.ok(!(await source()).includes('text="Remove message"')); await capture('clean-conversation'); });
  await hold('Please bring your proposal.');
  await check('long-press-opens-reply-copy-delete', async () => { await find('btnReplyMessage'); await find('btnCopyMessage'); await find('btnDeleteMessage'); await capture('message-options'); });
  await check('copy-selected-message', async () => {
    await tap('btnCopyMessage');
    await tap('etMessageInput');
    await api('POST', '/appium/device/press_keycode', { keycode: 279 }); // Android KEYCODE_PASTE
    assert.equal(await api('GET', `/element/${await find('etMessageInput')}/text`), 'Please bring your proposal.');
    await api('POST', `/element/${await find('etMessageInput')}/clear`, {});
    try { await api('POST', '/appium/device/hide_keyboard', {}); } catch {}
  });
  await hold('Please bring your proposal.'); await tap('btnReplyMessage');
  await check('reply-preview-and-cancel', async () => { assert.equal(await shown('replyPreview'), true); await tap('btnCancelReply'); assert.equal((await source()).includes(`resource-id="${pkg}:id/replyPreview"`), false); });
  await hold('Please bring your proposal.'); await tap('btnReplyMessage');
  await type('etMessageInput', 'I will bring it.');
  await check('reply-survives-rotation', async () => { await api('POST', '/orientation', { orientation: 'LANDSCAPE' }); await wait(() => find('tvReplyAuthor')); await api('POST', '/orientation', { orientation: 'PORTRAIT' }); await wait(() => find('tvReplyAuthor')); assert.equal(await api('GET', `/element/${await find('etMessageInput')}/text`), 'I will bring it.'); });
  failNextSend = true;
  await tap('btnSend');
  await check('failed-send-keeps-reply-and-draft', async () => { await wait(async () => assert.equal(await api('GET', `/element/${await find('btnSend')}/enabled`), true)); assert.equal(await shown('replyPreview'), true); assert.equal(await api('GET', `/element/${await find('etMessageInput')}/text`), 'I will bring it.'); });
  await tap('btnSend');
  await check('reply-sends-and-renders-context', async () => { await wait(() => byText('I will bring it.')); assert.ok(sent.at(-1).message_text.startsWith('↪ Reply to Demo Professor\n“Please bring your proposal.”\n\n')); try { await api('POST', '/appium/device/hide_keyboard', {}); } catch {} await capture('sent-reply'); });
  await hold('Thank you, professor.'); await tap('btnDeleteMessage');
  await check('delete-cancel-keeps-message', async () => { await tapText('Cancel'); await byText('Thank you, professor.'); });
  await hold('Thank you, professor.'); await tap('btnDeleteMessage'); await tapText('Delete for me');
  await check('delete-hides-only-selected-message', async () => { await wait(async () => assert.ok(!(await source()).includes('Thank you, professor.'))); assert.ok(hidden.student.has('2')); assert.ok(!hidden.faculty.has('2')); await byText('Please bring your proposal.'); await byText('I will bring it.'); });
  await tap('btnRemoveConversation'); await tapText('Delete conversation'); await tapText('Cancel');
  await check('conversation-options-keep-delete-confirmation', async () => { await byText('I will bring it.'); });
  await api('POST', '/back', {}); await tap('nav_profile'); await tap('btnLogout'); await wait(() => find('btnLogin'));
  await login(true);
  await check('faculty-badge-attached-to-bell', async () => { await wait(async () => assert.equal(await shown('tvNotifBadge'), true)); const bell = await api('GET', `/element/${await find('ivNotification')}/rect`); const badge = await api('GET', `/element/${await find('tvNotifBadge')}/rect`); assert.ok(badge.y > bell.y && badge.y + badge.height < bell.y + bell.height); await capture('faculty-badge'); });
  await tap('nav_faculty_chat');
  await wait(async () => { const contact = await element('xpath', '//*[starts-with(@text,"Demo Student (")]/ancestor::*[@clickable="true"][1]'); await api('POST', `/element/${contact}/click`, {}); await find('etMessageInput'); });
  await wait(() => byText('Thank you, professor.'));
  await hold('I will bring it.');
  await check('faculty-has-same-message-actions', async () => { await find('btnCopyMessage'); await find('btnDeleteMessage'); await tap('btnReplyMessage'); assert.equal(await shown('replyPreview'), true); await capture('faculty-reply-composer'); await tap('btnCancelReply'); });
  writeFileSync(path.join(output, 'report.json'), JSON.stringify({ status: 'passed', scope: 'Appium UI with isolated in-memory API fixtures; no hosted messages or accounts changed', checks: results }, null, 2));
} catch (e) {
  try { if (session) await capture('failure'); } catch {}
  writeFileSync(path.join(output, 'report.json'), JSON.stringify({ status: 'failed', checks: results, error: e.message }, null, 2));
  console.error(e.message); process.exitCode = 1;
} finally {
  if (session) try { await request('DELETE', `/session/${session}`); } catch {}
  await new Promise(r => server.close(r)); console.log('Report:', output);
}
