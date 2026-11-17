import assert from 'node:assert/strict';
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';

// Appium checks that require no credentials and submit no server-side changes.
const pkg = 'com.example.citecschedulingapp';
const udid = process.env.ANDROID_SERIAL || 'emulator-5554';
const server = process.env.APPIUM_SERVER || 'http://127.0.0.1:4723';
const dir = path.resolve('automation-tests/artifacts', `unauthenticated-${new Date().toISOString().replace(/[:.]/g, '-')}`);
mkdirSync(dir, { recursive: true });
const report = { status: 'failed', udid, package: pkg, startedAt: new Date().toISOString(), cases: [], scope: 'Unauthenticated UI only; no accounts, messages, bookings, or password reset emails submitted.' };
let session;
async function request(method, route, body) {
  const response = await fetch(`${server}${route}`, {
    method, headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(240000)
  });
  const result = await response.json();
  if (!response.ok || result.value?.error) throw new Error(result.value?.message || response.statusText);
  return result.value;
}
const api = (method, route, body) => request(method, `/session/${session}${route}`, body);
const find = async id => (await api('POST', '/element', { using: 'id', value: `${pkg}:id/${id}` }))['element-6066-11e4-a52e-4f735466cecf'];
const attr = async (id, name) => api('GET', `/element/${await find(id)}/attribute/${name}`);
const text = async id => api('GET', `/element/${await find(id)}/text`);
async function hideKeyboard() { try { await api('POST', '/appium/device/hide_keyboard', {}); } catch {} }
async function click(id) {
  await hideKeyboard();
  let element = await find(id);
  if (!(await api('GET', `/element/${element}/displayed`))) {
    element = (await api('POST', '/element', { using: '-android uiautomator', value: `new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(new UiSelector().resourceId("${pkg}:id/${id}"))` }))['element-6066-11e4-a52e-4f735466cecf'];
  }
  await api('POST', `/element/${element}/click`, {});
}
async function type(id, value) {
  const element = await find(id);
  await api('POST', `/element/${element}/clear`, {});
  if (value) await api('POST', `/element/${element}/value`, { text: value });
}
async function expectSource(value) {
  const deadline = Date.now() + 10000;
  do {
    if ((await api('GET', '/source')).includes(value)) return;
    await new Promise(r => setTimeout(r, 300));
  } while (Date.now() < deadline);
  throw new Error(`UI did not contain: ${value}`);
}
async function capture(name) {
  await hideKeyboard();
  writeFileSync(path.join(dir, `${name}.xml`), await api('GET', '/source'));
  writeFileSync(path.join(dir, `${name}.png`), Buffer.from(await api('GET', '/screenshot'), 'base64'));
}
async function test(name, fn) {
  try {
    await fn();
    report.cases.push({ name, status: 'passed' });
    console.log(`PASS ${name}`);
  } catch (error) {
    report.cases.push({ name, status: 'failed', error: error.message });
    console.log(`FAIL ${name}: ${error.message}`);
    try { await capture(`failed-${name}`); } catch {}
  }
}
try {
  assert.equal((await request('GET', '/status')).ready, true);
  const created = await request('POST', '/session', { capabilities: { alwaysMatch: {
    platformName: 'Android', 'appium:automationName': 'UiAutomator2', 'appium:udid': udid,
    'appium:appPackage': pkg, 'appium:appActivity': `${pkg}.LoginActivity`,
    'appium:noReset': true, 'appium:forceAppLaunch': true, 'appium:shouldTerminateApp': false,
    'appium:ignoreHiddenApiPolicyError': true, 'appium:skipDeviceInitialization': true,
    'appium:newCommandTimeout': 120, 'appium:uiautomator2ServerLaunchTimeout': 120000,
    'appium:adbExecTimeout': 120000
  }, firstMatch: [{}] } });
  session = created.sessionId;
  await api('POST', '/timeouts', { implicit: 3000 });
  await test('launch-login', async () => {
    await expectSource('tvWelcomeTitle');
    for (const id of ['etIdentifier', 'etPassword', 'btnLogin']) await find(id);
    assert.equal(await api('GET', '/appium/device/current_package'), pkg);
    await capture('login');
  });
  await test('faculty-role', async () => { await click('btnRoleFaculty'); assert.equal(await text('tvWelcomeTitle'), 'Faculty Login'); await expectSource('Faculty ID'); });
  await test('faculty-empty-identifier', async () => { await click('btnLogin'); await expectSource('Faculty ID or Email is required.'); });
  await test('student-role', async () => { await click('btnRoleStudent'); assert.equal(await text('tvWelcomeTitle'), 'Student Login'); });
  await test('student-empty-identifier', async () => { await click('btnLogin'); await expectSource('Student ID or Email is required.'); });
  await test('empty-password', async () => { await type('etIdentifier', 'appium-ui@example.invalid'); await click('btnLogin'); await expectSource('Password is required.'); });
  await test('remember-me-toggle', async () => {
    const before = await attr('cbRememberMe', 'checked');
    await click('cbRememberMe'); assert.notEqual(await attr('cbRememberMe', 'checked'), before);
    await click('cbRememberMe'); assert.equal(await attr('cbRememberMe', 'checked'), before);
  });
  await test('password-recovery-prefill', async () => { await click('tvForgotPassword'); await expectSource('etFpEmail'); assert.equal(await text('etFpEmail'), 'appium-ui@example.invalid'); await capture('password-recovery'); });
  await test('recovery-empty-email', async () => { await type('etFpEmail', ''); await click('btnSendCode'); await expectSource('Email is required.'); });
  await test('recovery-invalid-email', async () => { await type('etFpEmail', 'invalid'); await click('btnSendCode'); await expectSource('Please enter a valid email address.'); });
  await test('recovery-back-to-login', async () => { await click('tvBackToLogin'); await expectSource('tvWelcomeTitle'); });
  await test('registration-navigation', async () => { await click('tvRegisterLink'); await expectSource('etStudentId'); await find('btnRegister'); await capture('registration'); });
  await test('registration-required-student-id', async () => { await click('btnRegister'); await expectSource('Student ID is required.'); });
  await test('registration-faculty-mode', async () => { await click('btnRoleFaculty'); await expectSource('actDepartment'); await click('btnRegister'); await expectSource('Faculty ID is required.'); });
  await test('registration-default-department-and-required-name', async () => {
    assert.equal(await text('actDepartment'), 'College of Information Tech & Computing (CITEC)');
    await type('etStudentId', '0000'); await click('btnRegister'); await expectSource('First name is required.');
  });
  await test('registration-back-to-login', async () => { await click('tvLoginLink'); await expectSource('tvWelcomeTitle'); });
  report.status = report.cases.some(c => c.status === 'failed') ? 'failed' : 'passed';
} catch (error) {
  report.error = error.message;
  console.error(error.message);
} finally {
  if (session) try { await request('DELETE', `/session/${session}`); } catch (error) { report.cleanupError = error.message; report.status = 'failed'; }
  report.finishedAt = new Date().toISOString();
  writeFileSync(path.join(dir, 'report.json'), JSON.stringify(report, null, 2));
  console.log(`Report: ${path.join(dir, 'report.json')}`);
  if (report.status !== 'passed') process.exitCode = 1;
}
