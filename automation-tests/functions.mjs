import { writeFileSync } from 'node:fs';
import path from 'node:path';

export async function runFunctions({ api, artifacts, report, pkg }) {
  report.cases = [];
  const pause = ms => new Promise(r => setTimeout(r, ms));
  const key = 'element-6066-11e4-a52e-4f735466cecf';
  const find = async id => (await api('POST', '/element', { using: 'id', value: id.includes(':id/') ? id : `${pkg}:id/${id}` }))[key];
  const click = async id => api('POST', `/element/${await find(id)}/click`, {});
  const text = async id => api('GET', `/element/${await find(id)}/text`);
  async function visible(id) { if (!await api('GET', `/element/${await find(id)}/displayed`)) throw new Error(`${id} is not visible`); }
  async function waitFor(fn, timeout = 12000) {
    const end = Date.now() + timeout; let error;
    do { try { return await fn(); } catch (e) { error = e; await pause(500); } } while (Date.now() < end);
    throw error;
  }
  async function screen(name) {
    const current = await api('GET', '/appium/device/current_activity');
    if (current.endsWith('LoginActivity')) return;
    writeFileSync(path.join(artifacts, `${name}.png`), Buffer.from(await api('GET', '/screenshot'), 'base64'));
    writeFileSync(path.join(artifacts, `${name}.xml`), await api('GET', '/source'));
  }
  async function test(name, fn) {
    try { await fn(); await screen(name); report.cases.push({ name, status: 'passed' }); console.log(`PASS ${name}`); return true; }
    catch (e) { report.cases.push({ name, status: 'failed', error: e.message }); console.log(`FAIL ${name}: ${e.message}`); try { await screen(name); } catch {} return false; }
  }
  async function tab(id, expected) { await click(id); await waitFor(async () => { for (const x of expected) await visible(x); }); }
  async function type(id, value) { const element = await find(id); await api('POST', `/element/${element}/clear`, {}); await api('POST', `/element/${element}/value`, { text: value }); }
  for (const role of ['student', 'professor']) {
    const prefix = role.toUpperCase();
    if (!process.env[`${prefix}_ID`] || !process.env[`${prefix}_PASSWORD`]) throw new Error(`Set ${prefix}_ID and ${prefix}_PASSWORD environment variables`);
  }
  for (const role of ['student', 'professor']) {
    const faculty = role === 'professor';
    const prefix = role.toUpperCase();
    const profileTab = faculty ? 'nav_faculty_profile' : 'nav_profile';
    const login = await test(`${role}-login`, async () => {
      await visible('btnLogin');
      await click(faculty ? 'btnRoleFaculty' : 'btnRoleStudent');
      await type('etIdentifier', process.env[`${prefix}_ID`]);
      await type('etPassword', process.env[`${prefix}_PASSWORD`]);
      // Ensure this test does not enable persistent auto-login.
      if (await api('GET', `/element/${await find('cbRememberMe')}/attribute/checked`) === 'true') await click('cbRememberMe');
      await click('btnLogin');
      await waitFor(async () => {
        const current = await api('GET', '/appium/device/current_activity');
        if (current !== `${pkg}.${faculty ? 'FacultyHomeActivity' : 'HomeActivity'}` && current !== `.${faculty ? 'FacultyHomeActivity' : 'HomeActivity'}`) throw new Error('Expected role dashboard did not open');
        await visible('bottomNavigation');
      }, 40000);
      // Dismiss a first-run notification prompt without granting permissions.
      try { await click('com.android.permissioncontroller:id/permission_deny_button'); } catch {}
      await visible('tvWelcomeName');
    });
    if (!login) break;
    if (!faculty) {
      await test('student-schedule', () => tab('nav_schedule', ['tvWeekRange', 'btnDayMon']));
      await test('student-book-posted', () => tab('nav_book', ['gridBookingCalendar', 'tvBookingMonth', 'tvBookingMonthCount']));
      await test('student-book-custom', async () => { await click('btnModeCustom'); await visible('actCustomProfessor'); await visible('etCustomDate'); });
      await test('student-book-switch-back', async () => { await click('btnModePosted'); await visible('gridBookingCalendar'); });
      await test('student-appointments', () => tab('nav_appointments', ['btnFilterAll', 'btnFilterUpcoming', 'btnFilterCompleted']));
      for (const filter of ['Upcoming', 'Completed', 'All']) await test(`student-filter-${filter.toLowerCase()}`, async () => { await click(`btnFilter${filter}`); await visible(`btnFilter${filter}`); await pause(700); });
      await test('student-chat-list', async () => { await click('nav_chat'); await waitFor(async () => { const source = await api('GET', '/source'); if (!source.includes('Faculty Conversations')) throw new Error('Chat list heading missing'); }); });
    } else {
      await test('professor-consultations', async () => { await click('nav_faculty_schedule'); await waitFor(async () => { if (!(await api('GET', '/source')).includes('Student Consultations')) throw new Error('Consultations heading missing'); }); });
      await test('professor-availability-form', () => tab('nav_faculty_availability', ['etPostDate', 'actPostTimeSlot', 'actPostCategory', 'etPostLocation']));
      await test('professor-transfer-form', () => tab('nav_faculty_transfer', ['actStudentSchedule', 'actTargetFaculty', 'etTransferReason']));
      await test('professor-chat-list', async () => { await click('nav_faculty_chat'); await waitFor(async () => { if (!(await api('GET', '/source')).includes('Student Conversations')) throw new Error('Chat list heading missing'); }); });
    }
    await test(`${role}-profile`, async () => {
      await tab(profileTab, ['tvProfileName', 'tvProfileStudentId', 'tvProfileEmail', 'btnLogout']);
      if (!(await text('tvProfileName')).trim()) throw new Error('Profile name is empty');
      if (!(await text('tvProfileStudentId')).includes(process.env[`${prefix}_ID`])) throw new Error('Profile ID does not match signed-in account');
    });
    const logout = await test(`${role}-logout`, async () => { await click(profileTab); await click('btnLogout'); await waitFor(() => visible('btnLogin')); });
    if (!logout) break;
  }
  report.scope = 'Read-only UI checks; no bookings, schedule changes, transfers, or messages submitted. Filter checks verify interaction only, not dataset correctness.';
  if (report.cases.some(c => c.status === 'failed')) throw new Error('One or more functional checks failed; see cases in report.json');
}
