import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const mode = process.argv.includes('--before') ? 'before' : 'after';
const pkg = 'com.example.citecschedulingapp';
const dir = path.join(path.dirname(fileURLToPath(import.meta.url)), 'artifacts', `student-navigation-${mode}-${Date.now()}`);
mkdirSync(dir, { recursive: true });
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
const report = { mode, checks: [] };
let session;
async function request(method, route, body) {
  const response = await fetch('http://127.0.0.1:4723' + route, { method,
    headers: { 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(180000) });
  const result = await response.json();
  if (!response.ok || result.value?.error) throw new Error(result.value?.message || response.statusText);
  return result.value;
}
const api = (method, route, body) => request(method, `/session/${session}${route}`, body);
const elements = id => api('POST', '/elements', { using: 'id', value: `${pkg}:id/${id}` });
const elementKey = 'element-6066-11e4-a52e-4f735466cecf';
async function find(id) {
  const found = await elements(id);
  if (!found.length) throw new Error(`Missing ${id}`);
  return found[0][elementKey];
}
const click = async id => api('POST', `/element/${await find(id)}/click`, {});
async function waitFor(id) {
  for (let i = 0; i < 60; i++) {
    if ((await elements(id)).length) return;
    await pause(500);
  }
  throw new Error(`Timed out waiting for ${id}`);
}
async function screenshot(name) {
  writeFileSync(path.join(dir, name + '.png'), Buffer.from(await api('GET', '/screenshot'), 'base64'));
}
try {
  const created = await request('POST', '/session', { capabilities: { alwaysMatch: {
    platformName: 'Android', 'appium:automationName': 'UiAutomator2',
    'appium:appPackage': pkg, 'appium:appActivity': `${pkg}.LoginActivity`,
    'appium:appWaitActivity': `${pkg}.*`, 'appium:noReset': true, 'appium:forceAppLaunch': true,
    'appium:skipDeviceInitialization': true, 'appium:ignoreHiddenApiPolicyError': true,
    'appium:shouldTerminateApp': false
  }, firstMatch: [{}] } });
  session = created.sessionId;
  if ((await elements('btnLogin')).length) {
    if (!process.env.STUDENT_ID || !process.env.STUDENT_PASSWORD) throw new Error('Student credentials must be supplied in environment variables.');
    await click('btnRoleStudent');
    for (const [id, text] of [['etIdentifier', process.env.STUDENT_ID], ['etPassword', process.env.STUDENT_PASSWORD]]) {
      const el = await find(id);
      await api('POST', `/element/${el}/clear`, {});
      await api('POST', `/element/${el}/value`, { text });
    }
    await click('btnLogin');
  }
  await waitFor('nav_home');
  await click('nav_home');
  await waitFor('tvWelcomeName');
  if (mode === 'before') {
    await click('nav_schedule');
    await pause(1000);
    const stayedHome = (await elements('tvWelcomeName')).length > 0;
    const scheduleMissing = (await elements('tvWeekRange')).length === 0;
    report.checks.push({ name: 'Schedule tap leaves dashboard unchanged', observed: stayedHome && scheduleMissing });
    if (!stayedHome || !scheduleMissing) throw new Error('Schedule behavior differs from expected; review before changing it.');
    await screenshot('schedule-does-not-open');
    await click('nav_appointments');
    await waitFor('btnFilterUpcoming');
    await click('btnFilterUpcoming');
    report.checks.push({ name: 'Appointments already provides upcoming consultations', passed: true });
    await screenshot('existing-appointments');
  } else {
    if ((await elements('nav_schedule')).length) throw new Error('Schedule tab still exists');
    for (const [tab, expected] of [['nav_book', 'gridBookingCalendar'], ['nav_chat', 'containerFacultyContacts'],
      ['nav_appointments', 'btnFilterUpcoming'], ['nav_profile', 'btnLogout'], ['nav_home', 'tvWelcomeName']]) {
      await click(tab); await waitFor(expected);
      report.checks.push({ name: `${tab} opens`, passed: true });
    }
    // The dashboard shortcut may be below the fold on small phones.
    await api('POST', '/element', { using: '-android uiautomator', value: `new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(new UiSelector().resourceId("${pkg}:id/cardViewAppointments"))` });
    await screenshot('five-student-tabs');
    await click('cardViewAppointments');
    await waitFor('btnFilterUpcoming');
    report.checks.push({ name: 'Dashboard My Appointments shortcut opens appointments', passed: true });
    await screenshot('appointments-shortcut');
  }
  report.status = 'passed';
  console.log(`PASS ${dir}`);
} catch (error) {
  report.status = 'failed'; report.error = error.message;
  throw error;
} finally {
  writeFileSync(path.join(dir, 'report.json'), JSON.stringify(report, null, 2));
  if (session) await request('DELETE', `/session/${session}`);
}
