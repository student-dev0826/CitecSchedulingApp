import assert from 'node:assert/strict';
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
const pkg = 'com.example.citecschedulingapp.automation';
const output = path.resolve('automation-tests/artifacts', `chat-delete-ui-${Date.now()}`);
mkdirSync(output, { recursive: true });
const results = [], tag = `CHAT-UI-${Date.now()}`;
let session;
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
async function request(method, route, body) {
  const r = await fetch('http://127.0.0.1:4723' + route, { method, headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(180000) });
  const d = await r.json(); if (!r.ok || d.value?.error) throw new Error(d.value?.message || r.statusText); return d.value;
}
const api = (method, route, body) => request(method, `/session/${session}${route}`, body);
const find = async (value, using = 'id') => (await api('POST', '/element', { using,
  value: using === 'id' && !value.includes(':id/') ? `${pkg}:id/${value}` : value }))['element-6066-11e4-a52e-4f735466cecf'];
async function click(value, using = 'id') {
  try {
    let element;
    try {
      element = using === 'id' && !value.includes(':id/')
        ? await find(value, using) : await wait(() => find(value, using));
    } catch (error) {
      if (using !== 'id' || value.includes(':id/')) throw error;
      await find(`new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(new UiSelector().resourceId("${pkg}:id/${value}"))`, '-android uiautomator');
      element = await find(value, using);
    }
    if (!await api('GET', `/element/${element}/displayed`) && using === 'id' && !value.includes(':id/')) {
      await find(`new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(new UiSelector().resourceId("${pkg}:id/${value}"))`, '-android uiautomator');
      element = await find(value, using);
    }
    await api('POST', `/element/${element}/click`, {});
  } catch (error) { throw new Error(`Click ${value}: ${error.message}`); }
}
async function type(id, text) { const e = await wait(() => find(id)); await api('POST', `/element/${e}/clear`, {}); await api('POST', `/element/${e}/value`, { text }); }
async function wait(fn) { let err; for (let i = 0; i < 60; i++) { try { return await fn(); } catch (e) { err = e; await pause(500); } } throw err; }
const source = () => api('GET', '/source');
async function capture(name) { writeFileSync(path.join(output, name + '.png'), Buffer.from(await api('GET', '/screenshot'), 'base64')); }
async function check(name, fn) { console.log('RUN', name); await fn(); results.push(name); console.log('PASS', name); await capture(name); }
const post = async (file, fields) => (await fetch('http://127.0.0.1:8087/api/' + file, { method: 'POST', body: new URLSearchParams(fields) })).json();
const auth = async (identifier, role) => { const r = await post('login.php', { identifier, role, password: process.env.TEST_PASSWORD }); assert.equal(r.success, true); return r; };
const student = await auth('98765', 'STUDENT'), professor = await auth('9876', 'FACULTY');
const sid = '98765', fid = `PROF-${professor.user.user_id}`;
const chat = (actor, action, fields = {}) => post('chat.php', { action, auth_token: actor.auth_token, ...fields });
const messages = async (actor, other) => { const r = await chat(actor, 'fetch', { user2: other }); assert.equal(r.success, true, r.message); return r.data; };
async function seed(actor, other, text) {
  const r = await chat(actor, 'send', { recipient_id: other, sender_name: actor.user.full_name, recipient_name: 'Automation', message_text: text });
  assert.equal(r.success, true, r.message); return r.data.message_id;
}
async function login(faculty) {
  await wait(() => find('toggleRoleGroup'));
  await click(faculty ? 'btnRoleFaculty' : 'btnRoleStudent');
  await type('etIdentifier', faculty ? '9876' : '98765'); await type('etPassword', process.env.TEST_PASSWORD);
  await click('btnLogin'); await wait(() => find('bottomNavigation'));
  const permissions = await api('POST', '/elements', { using: 'id', value: 'com.android.permissioncontroller:id/permission_deny_button' });
  if (permissions.length) await api('POST', `/element/${permissions[0]['element-6066-11e4-a52e-4f735466cecf']}/click`, {});
}
async function openChat(faculty, marker) {
  // A newly launched activity can expose navigation before its transition ends.
  await wait(async () => {
    await click(faculty ? 'nav_faculty_chat' : 'nav_chat');
    await find(faculty ? 'containerStudentContacts' : 'containerFacultyContacts');
  });
  await click(`//*[@text="${marker}" or @text="You: ${marker}"]`, 'xpath');
  await wait(() => find('btnRemoveConversation'));
}
try {
  const created = await request('POST', '/session', { capabilities: { alwaysMatch: {
    platformName: 'Android', 'appium:automationName': 'UiAutomator2', 'appium:appPackage': pkg,
    ...(process.env.ANDROID_UDID ? { 'appium:udid': process.env.ANDROID_UDID } : {}),
    'appium:appActivity': 'com.example.citecschedulingapp.LoginActivity', 'appium:appWaitActivity': 'com.example.citecschedulingapp.*',
    'appium:noReset': true, 'appium:forceAppLaunch': true, 'appium:skipDeviceInitialization': true,
    'appium:ignoreHiddenApiPolicyError': true, 'appium:shouldTerminateApp': false
  }, firstMatch: [{}] } }); session = created.sessionId;
  await api('POST', '/appium/settings', { settings: { waitForIdleTimeout: 500 } });
  writeFileSync(path.join(output, 'session.json'), JSON.stringify({ session }));
  // Exit an existing local fixture session without touching the production app.
  const activity = await api('GET', '/appium/device/current_activity');
  if (activity.endsWith('ChatDetailActivity')) await api('POST', '/back', {});
  const current = await api('GET', '/appium/device/current_activity');
  if (current.endsWith('HomeActivity')) {
    await click(current.endsWith('FacultyHomeActivity') ? 'nav_faculty_profile' : 'nav_profile');
    await click('btnLogout');
  }
  for (const faculty of [false, true]) {
    const role = faculty ? 'professor' : 'student', actor = faculty ? professor : student;
    const peer = faculty ? student : professor, other = faculty ? sid : fid, me = faculty ? fid : sid;
    const ownText = `${tag}-${role}-own`, incomingText = `${tag}-${role}-incoming`;
    const incomingId = await seed(peer, me, incomingText);
    const ownId = await seed(actor, other, ownText);
    await check(`${role}-open-conversation`, async () => { await login(faculty); await openChat(faculty, ownText); await wait(() => find(`Remove message ${ownId}`, 'accessibility id')); });
    await check(`${role}-cancel-keeps-message`, async () => {
      await click(`Remove message ${ownId}`, 'accessibility id'); await click('android:id/button2');
      assert.ok((await messages(actor, other)).some(x => +x.message_id === ownId));
    });
    await check(`${role}-remove-message`, async () => {
      await click(`Remove message ${ownId}`, 'accessibility id'); await click('android:id/button1');
      await wait(async () => assert.ok(!(await source()).includes(ownText)));
      assert.ok(!(await messages(actor, other)).some(x => +x.message_id === ownId));
      assert.ok((await messages(peer, me)).some(x => +x.message_id === ownId));
    });
    await check(`${role}-cancel-keeps-conversation`, async () => {
      await click('btnRemoveConversation'); await click('android:id/button2');
      assert.ok((await messages(actor, other)).some(x => +x.message_id === incomingId));
    });
    await check(`${role}-remove-conversation`, async () => {
      await click('btnRemoveConversation'); await click('android:id/button1'); await wait(() => find('bottomNavigation'));
      assert.equal((await messages(actor, other)).length, 0);
      assert.ok((await messages(peer, me)).some(x => +x.message_id === incomingId));
    });
    await click(faculty ? 'nav_faculty_profile' : 'nav_profile'); await click('btnLogout');
    await login(faculty);
    await check(`${role}-removal-persists-after-login`, async () => {
      await click(faculty ? 'nav_faculty_chat' : 'nav_chat');
      await wait(() => find(faculty ? 'containerStudentContacts' : 'containerFacultyContacts'));
      await wait(async () => assert.ok(!(await source()).includes(ownText)));
      assert.equal((await messages(actor, other)).length, 0);
    });
    await click(faculty ? 'nav_faculty_profile' : 'nav_profile'); await click('btnLogout');
  }
} catch (error) {
  results.push({ error: error.message }); console.error(error.message); process.exitCode = 1;
  if (session) try { await capture('failure'); } catch { }
} finally {
  // Hide only fixtures from this run on both sides; never touch hosted records.
  for (const [actor, other] of [[student, fid], [professor, sid]]) {
    for (const message of await messages(actor, other)) if (message.message_text.startsWith(tag)) {
      await chat(actor, 'delete_message', { message_id: message.message_id });
    }
  }
  if (session) await request('DELETE', `/session/${session}`);
  writeFileSync(path.join(output, 'report.json'), JSON.stringify({ results }, null, 2));
  console.log(output);
}
