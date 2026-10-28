import assert from 'node:assert/strict';
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';

// Uses the separate local-backend APK; credentials are supplied at runtime.
const pkg = 'com.example.citecschedulingapp.automation';
const dir = path.resolve('automation-tests/artifacts', `ui-redesign-${Date.now()}`);
mkdirSync(dir, { recursive: true });
const key = 'element-6066-11e4-a52e-4f735466cecf';
const results = [];
const authOnly = process.argv.includes('--auth-only');
let session;
const fixtures = [];
let student, professor;
const post = async (file, fields) => (await fetch('http://127.0.0.1:8087/api/' + file, {
  method: 'POST', body: new URLSearchParams(fields)
})).json();
const schedule = (actor, action, fields = {}) => post('schedules.php', { action, auth_token: actor.auth_token, ...fields });
async function seed() {
  student = await post('login.php', { identifier: '98765', role: 'STUDENT', password: process.env.TEST_PASSWORD });
  professor = await post('login.php', { identifier: '9876', role: 'FACULTY', password: process.env.TEST_PASSWORD });
  assert.ok(student.success && professor.success);
  const date = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Manila' }).format(new Date(Date.now() + 86400000));
  for (const [index, time] of ['09:00 AM - 10:00 AM', '10:00 AM - 11:00 AM'].entries()) {
    const created = await schedule(professor, 'post', { faculty_id: professor.user.user_id,
      schedule_date: date, time_slot: time, category: index ? 'Open consultation' : 'Academic Advising', location: 'CITEC Office' });
    assert.equal(created.success, true, created.message);
    const id = created.data.schedule_id; fixtures.push(id);
    if (!index) {
      const booked = await schedule(student, 'book', { schedule_id: id, student_id: student.user.user_id, purpose: 'Local UI verification' });
      assert.equal(booked.success, true, booked.message);
    }
  }
}
async function request(method, route, body) {
  const response = await fetch(`http://127.0.0.1:4723${route}`, { method,
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(120000) });
  const data = await response.json();
  if (!response.ok || data.value?.error) throw new Error(data.value?.message || response.statusText);
  return data.value;
}
const api = (method, route, body) => request(method, `/session/${session}${route}`, body);
const find = async id => (await api('POST', '/element', { using: 'id', value: `${pkg}:id/${id}` }))[key];
async function wait(fn) {
  let error;
  for (let i = 0; i < 30; i++) {
    try { return await fn(); } catch (e) { error = e; await new Promise(r => setTimeout(r, 500)); }
  }
  throw error;
}
async function click(id) {
  let el;
  try { el = await find(id); } catch {
    await api('POST', '/element', { using: '-android uiautomator',
      value: `new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(new UiSelector().resourceId("${pkg}:id/${id}"))` });
    el = await find(id);
  }
  if (!(await api('GET', `/element/${el}/displayed`))) {
    await api('POST', '/element', { using: '-android uiautomator',
      value: `new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(new UiSelector().resourceId("${pkg}:id/${id}"))` });
    el = await find(id);
  }
  await api('POST', `/element/${el}/click`, {});
}
async function capture(name) {
  writeFileSync(path.join(dir, `${name}.png`), Buffer.from(await api('GET', '/screenshot'), 'base64'));
}
async function check(name, fn) {
  console.log('RUN', name); await fn(); results.push(name); await capture(name); console.log('PASS', name);
}
async function nav(id, expected) {
  await wait(async () => {
    await click(id);
    if (expected.startsWith('text:')) {
      await api('POST', '/element', { using: 'xpath', value: `//*[@text="${expected.slice(5)}"]` });
    } else await find(expected);
  });
}
async function login(faculty) {
  await click(faculty ? 'btnRoleFaculty' : 'btnRoleStudent');
  for (const [id, text] of [['etIdentifier', faculty ? '9876' : '98765'], ['etPassword', process.env.TEST_PASSWORD]]) {
    const el = await find(id); await api('POST', `/element/${el}/clear`, {}); await api('POST', `/element/${el}/value`, { text });
  }
  await click('btnLogin'); await wait(() => find('bottomNavigation'));
  const permission = await api('POST', '/elements', { using: 'id', value: 'com.android.permissioncontroller:id/permission_deny_button' });
  if (permission.length) await api('POST', `/element/${permission[0][key]}/click`, {});
}
try {
  assert.ok(process.env.TEST_PASSWORD, 'Supply TEST_PASSWORD');
  if (!authOnly) await seed();
  const created = await request('POST', '/session', { capabilities: { alwaysMatch: {
    platformName: 'Android', 'appium:automationName': 'UiAutomator2',
    'appium:udid': process.env.ANDROID_UDID || 'emulator-5554', 'appium:appPackage': pkg,
    'appium:appActivity': 'com.example.citecschedulingapp.LoginActivity',
    'appium:appWaitActivity': 'com.example.citecschedulingapp.*', 'appium:noReset': true,
    'appium:forceAppLaunch': true, 'appium:skipDeviceInitialization': true,
    'appium:ignoreHiddenApiPolicyError': true, 'appium:shouldTerminateApp': false
  }, firstMatch: [{}] } }); session = created.sessionId;
  await api('POST', '/appium/settings', { settings: { waitForIdleTimeout: 500 } });
  await check('login-reference', async () => { await wait(() => find('toggleRoleGroup')); });
  if (authOnly) {
    await check('forgot-password', async () => { await click('tvForgotPassword'); await wait(() => find('etFpEmail')); });
    await api('POST', '/back', {});
    await wait(() => find('etIdentifier'));
    await check('registration', async () => { await click('tvRegisterLink'); await wait(() => find('etStudentId')); });
    await api('POST', '/back', {});
  } else {
  await check('student-home', async () => { await login(false); await nav('nav_home', 'tvWelcomeName'); });
  await check('student-booking-calendar', async () => { await nav('nav_book', 'gridBookingCalendar'); });
  await check('student-custom-request', async () => { await click('btnModeCustom'); await wait(() => find('actCustomProfessor')); });
  await check('student-appointments', async () => { await nav('nav_appointments', 'btnFilterAll'); await click('btnFilterUpcoming'); });
  await check('student-chat', async () => { await nav('nav_chat', 'containerFacultyContacts'); });
  await check('student-profile', async () => { await nav('nav_profile', 'tvProfileEmail'); });
  await click('btnLogout'); await wait(() => find('toggleRoleGroup'));
  await check('professor-home', async () => { await login(true); await nav('nav_faculty_home', 'tvFacultyId'); });
  await check('professor-schedules', async () => { await nav('nav_faculty_schedule', 'text:Student Consultations'); });
  await check('professor-availability', async () => { await nav('nav_faculty_availability', 'btnWeeklyAvailability'); });
  await check('professor-weekly-calendar', async () => { await click('btnWeeklyAvailability'); await wait(() => find('gridAvailabilityMonth')); });
  await api('POST', '/back', {});
  await check('professor-transfer', async () => { await nav('nav_faculty_transfer', 'actStudentSchedule'); });
  await check('professor-chat', async () => { await nav('nav_faculty_chat', 'text:Student Conversations'); });
  await check('professor-profile', async () => { await nav('nav_faculty_profile', 'tvProfileEmail'); });
  await click('btnLogout'); await wait(() => find('toggleRoleGroup'));
  }
} catch (e) {
  results.push({ error: e.message }); console.error(e.message); process.exitCode = 1;
  if (session) try { await capture('failure'); } catch {}
} finally {
  if (session) await request('DELETE', `/session/${session}`);
  if (student && professor) {
    for (const id of fixtures) {
      await schedule(student, 'cancel', { schedule_id: id, student_id: student.user.user_id });
      const removed = await schedule(professor, 'delete', { schedule_id: id, faculty_id: professor.user.user_id });
      assert.equal(removed.success, true, removed.message);
    }
  }
  writeFileSync(path.join(dir, 'report.json'), JSON.stringify({ results }, null, 2));
  console.log(dir);
}
