import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import path from 'node:path';

export async function runRecordTests({ api, artifacts, report, pkg }) {
  const statePath = process.env.RECORD_STATE || path.join(artifacts, 'record-state.json');
  const state = existsSync(statePath) ? JSON.parse(readFileSync(statePath, 'utf8')) : { tag: `APPIUM-TEST-${Date.now()}`, completed: [], accounts: { student: process.env.STUDENT_ID, professor: process.env.PROFESSOR_ID } };
  if (state.accounts.student !== process.env.STUDENT_ID || state.accounts.professor !== process.env.PROFESSOR_ID) throw new Error('Resume accounts differ from original run');
  report.recordState = statePath; report.recordCases = []; report.testTag = state.tag;
  const save = () => writeFileSync(statePath, JSON.stringify(state, null, 2));
  save();
  const key = 'element-6066-11e4-a52e-4f735466cecf';
  const pause = ms => new Promise(r => setTimeout(r, ms));
  const find = async (value, using = 'id') => (await api('POST', '/element', { using, value: using === 'id' && !value.includes(':id/') ? `${pkg}:id/${value}` : value }))[key];
  const source = () => api('GET', '/source');
  const clickElement = element => api('POST', `/element/${element}/click`, {});
  const click = async id => clickElement(await find(id));
  const xp = value => find(value, 'xpath');
  const clickText = async text => clickElement(await xp(`//*[@text=${JSON.stringify(text)}]`));
  const text = async id => api('GET', `/element/${await find(id)}/text`);
  async function wait(fn, timeout = 25000) { let err; const end = Date.now() + timeout; do { try { return await fn(); } catch(e) { err = e; await pause(700); } } while(Date.now() < end); throw err; }
  async function visible(id) { const e = await find(id); if (!await api('GET', `/element/${e}/displayed`)) throw new Error(`${id} not visible`); return e; }
  async function scrollTo(id) {
    try { return await visible(id); } catch {}
    return find(`new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(new UiSelector().resourceId("${pkg}:id/${id}"))`, '-android uiautomator');
  }
  async function type(id, value) { const e = await scrollTo(id); await api('POST', `/element/${e}/clear`, {}); await api('POST', `/element/${e}/value`, { text: value }); }
  async function hideKeyboard() { try { await api('POST', '/appium/device/hide_keyboard', {}); } catch {} }
  async function capture(name) {
    if ((await api('GET', '/appium/device/current_activity')).endsWith('LoginActivity')) return;
    writeFileSync(path.join(artifacts, `${name}.xml`), await source());
    writeFileSync(path.join(artifacts, `${name}.png`), Buffer.from(await api('GET', '/screenshot'), 'base64'));
  }
  async function step(name, fn) {
    if (state.completed.includes(name)) return;
    console.log(`RUN ${name}`);
    try { await fn(); await capture(name); state.completed.push(name); save(); report.recordCases.push({ name, status: 'passed' }); console.log(`PASS ${name}`); }
    catch(e) { await capture(`failed-${name}`); report.recordCases.push({ name, status: 'failed', error: e.message }); save(); throw e; }
  }
  async function logout() {
    let activity = await api('GET', '/appium/device/current_activity');
    if (activity.endsWith('ChatDetailActivity')) { await click('ivBack'); activity = await api('GET', '/appium/device/current_activity'); }
    if (activity.endsWith('LoginActivity')) return;
    await click(activity.endsWith('FacultyHomeActivity') ? 'nav_faculty_profile' : 'nav_profile');
    await clickElement(await scrollTo('btnLogout')); await wait(() => visible('btnLogin'));
  }
  async function login(role) {
    await logout();
    await click(role === 'professor' ? 'btnRoleFaculty' : 'btnRoleStudent');
    await type('etIdentifier', process.env[`${role.toUpperCase()}_ID`]);
    await type('etPassword', process.env[`${role.toUpperCase()}_PASSWORD`]);
    await click('btnLogin');
    await wait(() => visible('bottomNavigation'), 45000);
    await click(role === 'professor' ? 'nav_faculty_profile' : 'nav_profile');
    await wait(() => visible('tvProfileStudentId'));
    if (!(await text('tvProfileStudentId')).includes(process.env[`${role.toUpperCase()}_ID`])) throw new Error('Wrong account identity');
    const name = await text('tvProfileName');
    if (state[`${role}Name`] && state[`${role}Name`] !== name) throw new Error('Account name changed');
    state[`${role}Name`] = name; save();
  }
  async function markedCard() {
    // The purpose is a direct child of the card's vertical layout. Restrict every mutation to this unique marker.
    const selector = `//*[contains(@text,"${state.tag}") and starts-with(@text,"Purpose:")]/..`;
    try { return await xp(selector); } catch {}
    await find(`new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(new UiSelector().textContains("${state.tag}"))`, '-android uiautomator');
    return xp(selector);
  }
  async function cardHas(status) {
    const card = await markedCard();
    const result = await api('POST', `/element/${card}/element`, { using: 'xpath', value: `.//*[@text=${JSON.stringify(status)}]` });
    if (!result[key]) throw new Error(`Test record missing status ${status}`);
  }
  async function cardClick(label) {
    const card = await markedCard();
    const result = await api('POST', `/element/${card}/element`, { using: 'xpath', value: `.//*[@text=${JSON.stringify(label)}]` });
    await clickElement(result[key]);
  }
  try {
    await login('professor');
    await step('identify-professor', async () => { if (!state.professorName) throw new Error('Professor name missing'); });
    await login('student');
    await step('create-custom-request', async () => {
      // Refuse automatic resubmission after an ambiguous network result.
      if (state.submissionAttempted) throw new Error('Previous submission may exist; inspect it before resubmitting');
      await click('nav_book'); await click('btnModeCustom');
      await type('etSearchProfessor', state.professorName); await hideKeyboard();
      await wait(async () => { if (!(await text('actCustomProfessor')).toLowerCase().includes(state.professorName.toLowerCase())) throw new Error('Target professor not selected'); });
      state.selectedProfessor = await text('actCustomProfessor');
      // Pick tomorrow from the standard date picker, avoiding a time slot that has already passed.
      await click('etCustomDate');
      const now = new Date(); const dateParts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Manila', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(now);
      const part = type => dateParts.find(p => p.type === type).value;
      const tomorrow = new Date(Date.UTC(+part('year'), +part('month') - 1, +part('day') + 1));
      state.date = tomorrow.toISOString().slice(0, 10); save();
      if (tomorrow.getUTCDate() === 1) await click('android:id/next');
      const day = String(tomorrow.getUTCDate());
      await clickText(day); await click('android:id/button1');
      await type('etCustomPurpose', `${state.tag} - two-account appointment lifecycle test`); await hideKeyboard();
      await clickElement(await scrollTo('btnSubmitCustomRequest'));
      state.submissionAttempted = true; save();
      await wait(() => visible('btnFilterAll'));
      await wait(() => cardHas('PENDING APPROVAL'));
      state.recordCreated = true; save();
    });
    await step('student-send-message', async () => {
      await click('nav_chat');
      await wait(async () => clickElement(await xp(`//*[contains(@text,${JSON.stringify(state.professorName)})]`)));
      await wait(() => visible('etMessageInput'));
      if (!(await text('tvChatContactName')).includes(state.professorName)) throw new Error('Wrong chat recipient');
      await type('etMessageInput', `${state.tag} student message`); await click('btnSend'); await hideKeyboard();
      await wait(async () => { if (!(await source()).includes(`${state.tag} student message`)) throw new Error('Sent message not visible'); });
      await click('ivBack');
    });
    await login('professor');
    await step('professor-accept-request', async () => {
      await click('nav_faculty_schedule'); await wait(() => cardHas('PENDING REQUEST'));
      const card = await markedCard();
      await api('POST', `/element/${card}/element`, { using: 'xpath', value: `.//*[contains(@text,"(${state.accounts.student})")]` });
      await cardClick('ACCEPT REQUEST'); await wait(() => cardHas('ACCEPTED'));
    });
    await step('professor-receive-and-reply', async () => {
      await click('nav_faculty_chat');
      await wait(async () => clickElement(await xp(`//*[contains(@text,"(${state.accounts.student})")]`)));
      await wait(async () => { if (!(await source()).includes(`${state.tag} student message`)) throw new Error('Student message not delivered'); });
      await type('etMessageInput', `${state.tag} professor reply`); await click('btnSend'); await hideKeyboard();
      await wait(async () => { if (!(await source()).includes(`${state.tag} professor reply`)) throw new Error('Reply not visible'); });
      await click('ivBack');
    });
    await login('student');
    await step('student-verifies-acceptance', async () => { await click('nav_appointments'); await wait(() => cardHas('ACCEPTED / CONFIRMED')); });
    await step('student-receives-reply', async () => {
      await click('nav_chat'); await wait(async () => clickElement(await xp(`//*[contains(@text,${JSON.stringify(state.professorName)})]`)));
      await wait(async () => { if (!(await source()).includes(`${state.tag} professor reply`)) throw new Error('Professor reply not delivered'); });
      await click('ivBack');
    });
    await login('professor');
    await step('professor-cancels-test-appointment', async () => {
      await click('nav_faculty_schedule'); await wait(() => cardHas('ACCEPTED'));
      await cardClick('Cancel Appointment'); await clickText('Others');
      const input = await xp('//*[@class="android.widget.EditText"]');
      await api('POST', `/element/${input}/value`, { text: `${state.tag} test complete` }); await hideKeyboard();
      await click('android:id/button1'); await wait(() => cardHas('CANCELLED'));
    });
    await login('student');
    await step('student-verifies-cancellation', async () => { await click('nav_appointments'); await wait(() => cardHas('CANCELLED BY PROFESSOR')); });
    await logout();
    report.recordScope = 'Only the supplied accounts: custom request, accept, cancel, and bidirectional chat. Cancelled test appointment and labeled chat messages retained as evidence. Transfers excluded because they require another professor.';
  } finally { save(); }
}
