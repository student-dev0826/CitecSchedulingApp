// UI-only fixture server. No database, hosted API, or real accounts are used.
import http from 'node:http';
import assert from 'node:assert/strict';
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';

const pkg = 'com.example.citecschedulingapp.automation';
const out = path.resolve('automation-tests/artifacts', `notifications-${Date.now()}`);
mkdirSync(out, { recursive: true });
const accepted = 'Your Academic Advising appointment with Prof. Demo on Nov 24, 2026 (09:00 AM - 10:00 AM) has been ACCEPTED!';
const declined = 'Your Thesis Consultation request with Prof. Demo on Nov 25, 2026 was declined. Reason: Department meeting. You can book another slot.';
const requested = 'Demo Student requested Academic Advising on Nov 24, 2026 (09:00 AM - 10:00 AM). Open Schedule to accept or decline.';
const custom = 'New appointment request from Demo Student for Nov 25, 2026 (01:00 PM - 02:00 PM). Open Schedule to accept or decline.';
const notice = 'Office update: Please contact the CITEC office if you need further information about your consultation. Bring your student ID and any relevant documents.';
const data = {
  student: [accepted, declined, 'Prof. Demo cancelled your Academic Advising appointment on Nov 26, 2026. Reason: Meeting.',
    'Your appointment request on Nov 20, 2026 expired because it was not approved in time.',
    'Your consultation appointment on Nov 27, 2026 was transferred from Prof. Demo to Prof. Sample.', notice],
  faculty: [requested, custom, 'Demo Student cancelled the Academic Advising appointment on Nov 24, 2026.',
    'Demo Student withdrew the request for Academic Advising on Nov 25, 2026.',
    "Prof. Sample transferred Demo Student's consultation appointment on Nov 27, 2026 to you."]
};
let mode = 'normal', delay = 0, session;
const readCalls = [], checks = [];
const server = http.createServer(async (req, res) => {
  let raw = ''; for await (const c of req) raw += c;
  const f = Object.fromEntries(new URLSearchParams(raw));
  const role = f.auth_token === 'faculty' ? 'faculty' : 'student';
  let reply = { success: true, message: 'OK', data: [] };
  if (req.url === '/api/login.php') {
    const faculty = f.role === 'FACULTY';
    reply = { success: true, message: 'OK', auth_token: faculty ? 'faculty' : 'student', user: {
      user_id: faculty ? 52 : 51, student_id: faculty ? 'DEMO-FACULTY' : 'DEMO-STUDENT',
      full_name: faculty ? 'Demo Professor' : 'Demo Student', role: faculty ? 'FACULTY' : 'STUDENT', email: 'demo@example.test'
    } };
  } else if (req.url === '/api/schedules.php') {
    if (f.action === 'list_notifications') {
      if (delay) await new Promise(r => setTimeout(r, delay));
      if (mode === 'error') reply = { success: false, message: 'Demo connection error. Please try again.' };
      else reply.data = mode === 'empty' ? [] : data[role].map((message, i) => ({ notification_id: i + 1, message,
        created_at: `2026-11-${22 - i} 09:30:00`, is_read: i === 1 ? 1 : 0 }));
    } else if (f.action === 'mark_read') readCalls.push(role);
  }
  res.writeHead(200, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(reply));
});
await new Promise((resolve, reject) => { server.once('error', reject); server.listen(18088, '127.0.0.1', resolve); });
const base = process.env.APPIUM_SERVER || 'http://127.0.0.1:4729';
async function request(method, route, body) {
  const r = await fetch(base + route, { method, headers: { 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(240000) });
  const j = await r.json(); if (!r.ok || j.value?.error) throw Error(j.value?.message || r.statusText); return j.value;
}
const api = (method, route, body) => request(method, `/session/${session}${route}`, body);
const key = 'element-6066-11e4-a52e-4f735466cecf';
const el = async (using, value) => (await api('POST', '/element', { using, value }))[key];
const find = id => el('id', `${pkg}:id/${id}`);
const textEl = text => el('xpath', `//*[@text=${JSON.stringify(text)}]`);
const tap = async id => api('POST', `/element/${await find(id)}/click`, {});
const getText = async id => api('GET', `/element/${await find(id)}/text`);
const source = () => api('GET', '/source');
async function wait(fn) { let error; for (let i = 0; i < 40; i++) { try { return await fn(); } catch (e) { error = e; await new Promise(r => setTimeout(r, 250)); } } throw error; }
async function check(name, fn) { await fn(); checks.push(name); console.log('PASS', name); }
async function capture(name) {
  writeFileSync(path.join(out, `${name}.png`), Buffer.from(await api('GET', '/screenshot'), 'base64'));
  writeFileSync(path.join(out, `${name}.xml`), await source());
}
async function openCard(summary) {
  let card;
  try { card = await el('xpath', `//*[@text=${JSON.stringify(summary)}]/ancestor::*[@clickable="true"][1]`); }
  catch {
    await el('-android uiautomator', `new UiScrollable(new UiSelector().resourceId("${pkg}:id/notificationListScroll")).scrollIntoView(new UiSelector().text(${JSON.stringify(summary)}))`);
    card = await el('xpath', `//*[@text=${JSON.stringify(summary)}]/ancestor::*[@clickable="true"][1]`);
  }
  await api('POST', `/element/${card}/click`, {});
  await wait(() => find('tvNotificationFullMessage'));
}
async function login(faculty) {
  await tap(faculty ? 'btnRoleFaculty' : 'btnRoleStudent');
  for (const [id, text] of [['etIdentifier', faculty ? 'DEMO-FACULTY' : 'DEMO-STUDENT'], ['etPassword', 'demo-password']]) {
    const e = await find(id); await api('POST', `/element/${e}/clear`, {}); await api('POST', `/element/${e}/value`, { text });
  }
  try { await api('POST', '/appium/device/hide_keyboard', {}); } catch {}
  await tap('btnLogin'); await wait(() => find('ivNotification'));
  try { const e = await el('id', 'com.android.permissioncontroller:id/permission_deny_button'); await api('POST', `/element/${e}/click`, {}); } catch {}
}
try {
  const created = await request('POST', '/session', { capabilities: { alwaysMatch: {
    platformName: 'Android', 'appium:automationName': 'UiAutomator2', 'appium:udid': process.env.ANDROID_SERIAL || 'emulator-5554',
    'appium:appPackage': pkg, 'appium:appActivity': 'com.example.citecschedulingapp.LoginActivity',
    'appium:noReset': true, 'appium:forceAppLaunch': true, 'appium:skipDeviceInitialization': true,
    'appium:ignoreHiddenApiPolicyError': true, 'appium:uiautomator2ServerLaunchTimeout': 120000, 'appium:adbExecTimeout': 120000
  }, firstMatch: [{}] } }); session = created.sessionId;
  await api('POST', '/timeouts', { implicit: 700 });
  await login(false); await tap('ivNotification');
  await wait(() => textEl('The professor accepted your appointment'));
  await check('student-summary-only-inbox', async () => {
    const xml = await source(); assert.ok(!xml.includes(accepted) && !xml.includes('Department meeting'));
    await textEl('The professor declined your request'); await capture('student-inbox');
  });
  await openCard('The professor accepted your appointment');
  await check('full-original-message-on-tap', async () => { assert.equal(await getText('tvNotificationFullMessage'), accepted); assert.equal(await getText('btnNotificationOpenAppointments'), 'Open appointments'); await capture('accepted-details'); });
  await tap('btnNotificationBack');
  await check('back-retains-inbox', async () => { await textEl('Notifications'); await textEl('Viewed'); });
  await openCard('The professor declined your request');
  await check('decline-reason-preserved', async () => { assert.equal(await getText('tvNotificationFullMessage'), declined); await capture('declined-details'); });
  await api('POST', '/back', {});
  await check('system-back-returns-to-summaries', async () => { await textEl('Notifications'); await textEl('The professor declined your request'); });
  await openCard('The professor accepted your appointment');
  await api('POST', '/orientation', { orientation: 'LANDSCAPE' });
  await api('POST', '/orientation', { orientation: 'PORTRAIT' });
  await check('detail-restored-after-rotation', async () => { await wait(async () => assert.equal(await getText('tvNotificationFullMessage'), accepted)); });
  await tap('btnNotificationBack'); await openCard('You have a consultation update');
  await check('unknown-message-uses-safe-summary', async () => { assert.equal(await getText('tvNotificationFullMessage'), notice); assert.ok(!(await source()).includes(`resource-id="${pkg}:id/btnNotificationOpenAppointments"`)); });
  await tap('btnNotificationBack'); await openCard('The professor accepted your appointment'); await tap('btnNotificationOpenAppointments');
  await check('student-open-appointments', async () => { await wait(() => find('btnFilterAll')); });
  await tap('nav_home'); mode = 'empty'; await tap('ivNotification');
  await check('themed-empty-state', async () => { await wait(() => textEl('You are all caught up')); await capture('empty-state'); });
  await tap('btnCloseNotifications'); mode = 'error'; await tap('ivNotification');
  await check('themed-error-state', async () => { await wait(() => textEl('Could not load notifications')); await find('btnRetryNotifications'); await capture('error-state'); });
  mode = 'normal'; await tap('btnRetryNotifications');
  await check('retry-loads-notifications', async () => { await wait(() => textEl('The professor accepted your appointment')); });
  await tap('btnCloseNotifications'); await tap('nav_profile'); await tap('btnLogout'); await wait(() => find('btnLogin'));
  await login(true); await tap('ivNotification'); await wait(() => textEl('A student requested your slot'));
  await check('faculty-request-summaries', async () => { await textEl('A student sent an appointment request'); assert.ok(!(await source()).includes(requested)); await capture('faculty-inbox'); });
  await openCard('A student requested your slot');
  await check('faculty-full-request-details', async () => { assert.equal(await getText('tvNotificationFullMessage'), requested); assert.equal(await getText('btnNotificationOpenAppointments'), 'Open schedules'); await capture('faculty-details'); });
  await tap('btnNotificationOpenAppointments');
  await check('faculty-open-schedules', async () => { await wait(() => textEl('Student Consultations')); });
  await check('existing-read-api-preserved', async () => { assert.ok(readCalls.includes('student') && readCalls.includes('faculty')); });
  writeFileSync(path.join(out, 'report.json'), JSON.stringify({ status: 'passed', scope: 'Local in-memory fixtures only; no database connections or hosted API calls', checks }, null, 2));
} catch (error) {
  try { if (session) await capture('failure'); } catch {}
  writeFileSync(path.join(out, 'report.json'), JSON.stringify({ status: 'failed', checks, error: error.message }, null, 2));
  console.error(error.message); process.exitCode = 1;
} finally {
  if (session) try { await request('DELETE', `/session/${session}`); } catch {}
  await new Promise(resolve => server.close(resolve)); console.log('Report:', out);
}
