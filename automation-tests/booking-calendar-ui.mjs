import { spawnSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import path from 'node:path';

// Runs only against the isolated PHP/MySQL backend and separate .automation app.
const pkg = 'com.example.citecschedulingapp.automation';
const adb = 'C:/Users/gibag/AppData/Local/Android/Sdk/platform-tools/adb.exe';
const output = path.resolve('automation-tests/artifacts', `booking-calendar-${new Date().toISOString().replace(/[:.]/g, '-')}`);
mkdirSync(output, { recursive: true });
const tag = `CALENDAR-${Date.now()}`;
const key = 'element-6066-11e4-a52e-4f735466cecf';
const cases = [], rules = [];
let session, professor, student, slots = [];
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
async function request(method, route, body) {
    const response = await fetch('http://127.0.0.1:4723' + route, { method, headers: { 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(180000) });
    const data = await response.json();
    if (!response.ok || data.value?.error) throw new Error(data.value?.message || 'Appium request failed');
    return data.value;
}
const api = (method, route, body) => request(method, `/session/${session}${route}`, body);
const find = async (value, using = 'id') => (await api('POST', '/element', { using, value: using === 'id' && !value.includes(':id/') ? `${pkg}:id/${value}` : value }))[key];
async function wait(fn, timeout = 30000) {
    let error; const end = Date.now() + timeout;
    do { try { return await fn(); } catch (e) { error = e; await pause(500); } } while (Date.now() < end);
    throw error;
}
async function reveal(value, using = 'id') {
    try { const e = await find(value, using); if (await api('GET', `/element/${e}/displayed`)) return e; } catch { }
    const selector = using === 'id' ? `resourceId("${pkg}:id/${value}")` : `description(${JSON.stringify(value)})`;
    try { await find(`new UiScrollable(new UiSelector().className("android.widget.ScrollView").scrollable(true)).scrollIntoView(new UiSelector().${selector})`, '-android uiautomator'); } catch { }
    await pause(500); return find(value, using);
}
async function click(value, using = 'id') { const e = await reveal(value, using); await api('POST', `/element/${e}/click`, {}); }
const text = async id => api('GET', `/element/${await reveal(id)}/text`);
async function type(id, value) { const e = await reveal(id); await api('POST', `/element/${e}/clear`, {}); await api('POST', `/element/${e}/value`, { text: value }); }
async function hideKeyboard() { try { await api('POST', '/appium/device/hide_keyboard', {}); } catch { } }
async function source() { return api('GET', '/source'); }
async function capture(name) {
    if ((await api('GET', '/appium/device/current_activity')).endsWith('LoginActivity')) return;
    writeFileSync(path.join(output, `${name}.png`), Buffer.from(await api('GET', '/screenshot'), 'base64'));
    writeFileSync(path.join(output, `${name}.xml`), await source());
}
async function check(name, fn) { console.log('RUN', name); await fn(); cases.push({ name, status: 'passed' }); console.log('PASS', name); await capture(name); }
async function post(file, data) { return (await fetch('http://127.0.0.1:8087/api/' + file, { method: 'POST', body: new URLSearchParams(data), signal: AbortSignal.timeout(30000) })).json(); }
const backend = (action, fields = {}, actor = professor) => post('schedules.php', { action, auth_token: actor.auth_token, ...fields });
const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Manila', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
const part = type => +parts.find(p => p.type === type).value;
const day = offset => new Date(Date.UTC(part('year'), part('month') - 1, part('day') + offset));
const firstDate = day(1).toISOString().slice(0, 10), secondDate = day(2).toISOString().slice(0, 10);
const monthTitle = new Intl.DateTimeFormat('en-US', { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(day(1));
const dateCell = (date, count) => `${date}, ${count} available slot${count === 1 ? '' : 's'}`;
async function selectDate(date, count) { await click(dateCell(date, count), 'accessibility id'); }

try {
    if (!process.env.TEST_PASSWORD) throw new Error('Set TEST_PASSWORD for the local fixture accounts.');
    const screen = spawnSync(adb, ['shell', 'dumpsys', 'window'], { encoding: 'utf8', windowsHide: true }).stdout;
    if (/mAwake=false/.test(screen) || /mCurrentFocus=.*NotificationShade/.test(screen)) throw new Error('Unlock the connected phone before running the calendar test.');
    professor = await post('login.php', { identifier: '9876', password: process.env.TEST_PASSWORD, role: 'FACULTY' });
    student = await post('login.php', { identifier: '98765', password: process.env.TEST_PASSWORD, role: 'STUDENT' });
    assert.equal(professor.success, true); assert.equal(student.success, true);
    for (const [date, start, end, category] of [[firstDate, '09:00', '10:00', 'Academic Advising'], [firstDate, '10:00', '11:00', 'Thesis Consultation'], [secondDate, '14:00', '15:00', 'Project Review']]) {
        const result = await backend('recurring_save', { weekday: new Date(date + 'T00:00:00Z').getUTCDay() || 7, date_from: date, date_to: date, start_time: start, end_time: end, category: `${category} ${tag}`, location: 'Calendar Test Office' });
        assert.equal(result.success, true, result.message); rules.push(result.data.recurrence_id);
    }
    slots = (await backend('list_open')).data.filter(x => rules.includes(x.recurrence_id)); assert.equal(slots.length, 3);
    const devices = spawnSync(adb, ['devices'], { encoding: 'utf8', windowsHide: true }).stdout.split(/\r?\n/).filter(x => /\sdevice$/.test(x) && !x.startsWith('emulator-')).map(x => x.split(/\s/)[0]);
    assert.equal(devices.length, 1);
    const install = spawnSync(adb, ['-s', devices[0], 'install', '-r', 'app/build/outputs/apk/debug/app-debug.apk'], { encoding: 'utf8', windowsHide: true }); assert.equal(install.status, 0, install.stderr);
    spawnSync(adb, ['-s', devices[0], 'reverse', 'tcp:8087', 'tcp:8087'], { windowsHide: true });
    const created = await request('POST', '/session', { capabilities: { alwaysMatch: { platformName: 'Android', 'appium:automationName': 'UiAutomator2', 'appium:udid': devices[0], 'appium:appPackage': pkg, 'appium:appActivity': 'com.example.citecschedulingapp.LoginActivity', 'appium:noReset': true, 'appium:forceAppLaunch': true, 'appium:ignoreHiddenApiPolicyError': true, 'appium:skipDeviceInitialization': true, 'appium:shouldTerminateApp': false }, firstMatch: [{}] } }); session = created.sessionId;
    writeFileSync(path.join(output, 'session.json'), JSON.stringify({ session, tag, rules }, null, 2));
    await check('student-login', async () => { await wait(() => find('btnLogin')); await click('btnRoleStudent'); await type('etIdentifier', '98765'); await type('etPassword', process.env.TEST_PASSWORD); await click('btnLogin'); await wait(() => find('bottomNavigation'), 45000); try { const deny = await find('com.android.permissioncontroller:id/permission_deny_button'); await api('POST', `/element/${deny}/click`, {}); } catch { } });
    await check('calendar-counts', async () => {
        await click('nav_book'); await wait(() => find('gridBookingCalendar'));
        for (let i = 0; i < 2 && await text('tvBookingMonth') !== monthTitle; i++) await click('btnBookingNextMonth');
        assert.equal(await text('tvBookingMonth'), monthTitle);
        await wait(() => find(dateCell(firstDate, 2), 'accessibility id')); await find(dateCell(secondDate, 1), 'accessibility id');
        assert.match(await text('tvBookingMonthCount'), /3 available slots/);
    });
    await check('date-expands-time-category-professor', async () => {
        await selectDate(firstDate, 2); await reveal('Select booking slot ' + slots.find(x => x.time_slot.startsWith('10:00')).schedule_id, 'accessibility id');
        const xml = await source(); assert.ok(xml.includes('09:00 AM - 10:00 AM')); assert.ok(xml.includes('10:00 AM - 11:00 AM')); assert.ok(xml.includes('Academic Advising')); assert.ok(xml.includes('Thesis Consultation')); assert.ok(xml.includes(professor.user.full_name));
        assert.ok(!xml.includes('Request selected slot'), 'Booking form should wait for explicit selection');
    });
    await check('search-updates-counts-and-empty-state', async () => {
        await type('etSearchProfessor', 'no such professor'); await hideKeyboard(); await wait(async () => assert.match(await text('tvBookingMonthCount'), /^0 available slots/));
        await find(dateCell(firstDate, 0), 'accessibility id');
        await type('etSearchProfessor', professor.user.full_name); await hideKeyboard(); await wait(() => find(dateCell(firstDate, 2), 'accessibility id'));
    });
    await check('date-change-shows-only-its-slots', async () => { await selectDate(secondDate, 1); await reveal('Select booking slot ' + slots.find(x => x.schedule_date === secondDate).schedule_id, 'accessibility id'); assert.ok((await source()).includes('Project Review')); await selectDate(firstDate, 2); });
    const selected = slots.find(x => x.schedule_date === firstDate && x.time_slot.startsWith('10:00'));
    await check('explicit-selection-and-booking', async () => {
        await click('Select booking slot ' + selected.schedule_id, 'accessibility id');
        assert.ok((await text('tvSelectedBookingSlot')).includes('Thesis Consultation'));
        await type('etBookingReason', `${tag} student booking test`); await hideKeyboard(); await click('btnSubmitBooking');
        await wait(() => find('btnFilterAll'));
        await wait(async () => { const r = await backend('student_appointments', { student_id: student.user.user_id }, student); const row = r.data.find(x => x.schedule_id === selected.schedule_id); assert.equal(row?.status, 'PENDING'); assert.equal(row.purpose, `${tag} student booking test`); });
    });
    await check('booked-slot-removed-from-calendar-count', async () => { await click('nav_book'); if (await text('tvBookingMonth') !== monthTitle) await click('btnBookingNextMonth'); await wait(() => find(dateCell(firstDate, 1), 'accessibility id')); await selectDate(firstDate, 1); });
    await check('stale-removed-slot-refreshes-safely', async () => {
        const stale = slots.find(x => x.schedule_date === firstDate && x.time_slot.startsWith('09:00'));
        await click('Select booking slot ' + stale.schedule_id, 'accessibility id');
        assert.equal((await backend('delete', { schedule_id: stale.schedule_id, faculty_id: professor.user.user_id })).success, true);
        await type('etBookingReason', `${tag} stale selection test`); await hideKeyboard(); await click('btnSubmitBooking');
        await wait(() => find(dateCell(firstDate, 0), 'accessibility id'));
        await click('btnRefreshBookingSlots'); await wait(async () => assert.ok(!(await text('tvBookingCalendarStatus')).includes('Updating')));
        const r = await backend('student_appointments', { student_id: student.user.user_id }, student); assert.ok(!r.data.some(x => x.schedule_id === stale.schedule_id));
    });
    await check('custom-mode-still-available', async () => { await click('btnModeCustom'); await wait(() => find('actCustomProfessor')); await find('etCustomDate'); await click('btnModePosted'); await wait(() => find('gridBookingCalendar')); });
} catch (error) {
    cases.push({ name: 'execution', status: 'failed', error: error.message }); console.error(error.message); process.exitCode = 1;
    if (session) try { await capture('failure'); } catch { }
} finally {
    try {
        if (student) {
            const r = await backend('student_appointments', { student_id: student.user.user_id }, student);
            if (!r.success) throw new Error('Could not verify fixture appointments during cleanup');
            for (const row of r.data || []) if (slots.some(x => x.schedule_id === row.schedule_id) && row.purpose?.startsWith(tag)) assert.equal((await backend('cancel', { schedule_id: row.schedule_id, student_id: student.user.user_id }, student)).success, true);
        }
        if (professor) for (const id of rules) { const r = await backend('recurring_list'); if (!r.success) throw new Error('Could not verify fixture rules during cleanup'); const rule = r.data?.find(x => x.recurrence_id === id); if (rule) assert.equal((await backend('recurring_delete', { recurrence_id: id, version: rule.version })).success, true); }
    } catch (error) { cases.push({ name: 'cleanup', status: 'failed', error: error.message }); process.exitCode = 1; }
    if (session) try { await request('DELETE', `/session/${session}`); } catch { }
    writeFileSync(path.join(output, 'report.json'), JSON.stringify({ tag, package: pkg, firstDate, secondDate, cases }, null, 2)); console.log('Report:', path.join(output, 'report.json'));
}
