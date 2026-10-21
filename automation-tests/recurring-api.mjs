import assert from 'node:assert/strict';
import { writeFileSync, mkdirSync } from 'node:fs';
const base = 'http://127.0.0.1:8087/api/';
const results = [];
async function post(file, fields) { return (await fetch(base + file, { method: 'POST', body: new URLSearchParams(fields), signal: AbortSignal.timeout(30000) })).json(); }
const login = (identifier, role) => post('login.php', { identifier, role, password: process.env.TEST_PASSWORD });
const f = await login('9876', 'FACULTY'), s = await login('98765', 'STUDENT');
assert.equal(f.success, true); assert.equal(s.success, true); assert.ok(f.auth_token); assert.ok(s.auth_token);
const fid = f.user.user_id, sid = s.user.user_id;
const api = (action, fields = {}, token = f.auth_token) => post('schedules.php', { action, auth_token: token, ...fields });
const run = async (name, fn) => { try { await fn(); results.push({ name, status: 'passed' }); console.log('PASS', name); } catch(error) { results.push({ name, status: 'failed', error:error.message }); throw error; } };
const month = new Date(); month.setUTCDate(1); month.setUTCMonth(month.getUTCMonth() + 1);
const ym = month.toISOString().slice(0, 7); const last = new Date(Date.UTC(month.getUTCFullYear(), month.getUTCMonth() + 1, 0)).toISOString().slice(0, 10);
const plan = { weekday: '2', date_from: `${ym}-01`, date_to: last, start_time: '09:00', end_time: '10:00', category: 'APPIUM API TEST', location: 'Isolated test room' };
let recurrence, booked, createdIds = [];
try {
  await run('missing session rejected', async () => assert.equal((await api('recurring_list', {}, '')).success, false));
  await run('student cannot manage recurrence', async () => assert.equal((await api('recurring_save', plan, s.auth_token)).success, false));
  await run('forged professor ID rejected', async () => assert.equal((await api('recurring_save', { ...plan, faculty_id: fid + 999 })).success, false));
  await run('generate every Tuesday', async () => { const r = await api('recurring_save', plan); assert.equal(r.success, true, r.message); recurrence = r.data.recurrence_id; createdIds.push(recurrence); const dates = []; for(let day=1; day<=+last.slice(-2);day++) { const d = new Date(`${ym}-${String(day).padStart(2,'0')}T00:00:00Z`); if(d.getUTCDay()===2) dates.push(d.toISOString().slice(0,10)); } const calendar = await api('recurring_calendar', { month: ym }); assert.deepEqual(calendar.data.filter(x=>x.recurrence_id===recurrence).map(x=>x.schedule_date), dates); });
  await run('new login reloads persisted rule', async () => { const again = await login('9876','FACULTY'); const r=await api('recurring_list',{},again.auth_token); assert.ok(r.data.some(x=>x.recurrence_id===recurrence)); });
  await run('overlap rejected and rolled back', async () => { const before=await api('recurring_list'); const r=await api('recurring_save',{...plan,start_time:'09:30',end_time:'10:30'}); assert.equal(r.success,false); assert.match(r.message,/overlap/i); assert.equal((await api('recurring_list')).data.length,before.data.length); });
  await run('different weekday and adjacent times allowed', async () => { for(const p of [{weekday:'1',start_time:'13:00',end_time:'15:00'},{weekday:'3',start_time:'10:00',end_time:'12:00'},{weekday:'2',start_time:'10:00',end_time:'11:00'}]) {const r=await api('recurring_save',{...plan,...p}); assert.equal(r.success,true,r.message); createdIds.push(r.data.recurrence_id);} });
  await run('failed edit restores original occurrences and version',async()=>{const before=await api('recurring_calendar',{month:ym}); const r=await api('recurring_save',{...plan,recurrence_id:recurrence,version:1,start_time:'09:30',end_time:'10:30'});assert.equal(r.success,false);assert.match(r.message,/overlap/i);assert.deepEqual((await api('recurring_calendar',{month:ym})).data,before.data);assert.equal((await api('recurring_list')).data.find(x=>x.recurrence_id===recurrence).version,1);});
  await run('generated availability visible to student booking list',async()=>{const r=await api('list_open',{},s.auth_token);assert.ok(r.data.some(x=>x.recurrence_id===recurrence));});
  await run('student books generated occurrence', async () => {const c=await api('recurring_calendar',{month:ym}); booked=c.data.find(x=>x.recurrence_id===recurrence); const r=await api('book',{schedule_id:booked.schedule_id,student_id:sid,purpose:'APPIUM API TEST'},s.auth_token); assert.equal(r.success,true,r.message);});
  await run('manual overlap and custom request rejected',async()=>{const fields={faculty_id:fid,schedule_date:booked.schedule_date,time_slot:'09:15 AM - 09:45 AM',category:'test',location:'test',student_id:sid,purpose:'test'}; assert.equal((await api('post',fields)).success,false);assert.equal((await api('request_custom_appointment',fields,s.auth_token)).success,false);});
  await run('edit preserves pending booking', async()=>{const r=await api('recurring_save',{...plan,recurrence_id:recurrence,version:1,start_time:'08:00',end_time:'09:00'});assert.equal(r.success,true,r.message); assert.equal(r.data.protected,1); const c=await api('recurring_calendar',{month:ym});const kept=c.data.find(x=>x.schedule_id===booked.schedule_id);assert.equal(kept.status,'PENDING');assert.equal(kept.start_at,booked.start_at);});
  await run('stale edit rejected',async()=>assert.equal((await api('recurring_save',{...plan,recurrence_id:recurrence,version:1})).success,false));
  await run('professor accepts pending occurrence',async()=>assert.equal((await api('respond_appointment',{faculty_id:fid,schedule_id:booked.schedule_id,status:'ACCEPTED'})).success,true));
  await run('delete keeps accepted booking',async()=>{const r=await api('recurring_delete',{recurrence_id:recurrence,version:2});assert.equal(r.success,true,r.message);const c=await api('recurring_calendar',{month:ym}); const rows=c.data.filter(x=>x.recurrence_id===recurrence);assert.equal(rows.length,1);assert.equal(rows[0].status,'ACCEPTED');});
  await run('cancel after delete never reopens removed series',async()=>{assert.equal((await api('cancel',{schedule_id:booked.schedule_id,student_id:sid},s.auth_token)).success,true);const c=await api('recurring_calendar',{month:ym});assert.equal(c.data.filter(x=>x.recurrence_id===recurrence).length,0);});
  await run('declined history allows replacement without reopening duplicates',async()=>{
    const one={...plan,date_from:booked.schedule_date,date_to:booked.schedule_date,start_time:'15:00',end_time:'16:00'};
    const original=await api('recurring_save',one);assert.equal(original.success,true,original.message);createdIds.push(original.data.recurrence_id);
    let calendar=await api('recurring_calendar',{month:ym});const old=calendar.data.find(x=>x.recurrence_id===original.data.recurrence_id);
    assert.equal((await api('book',{schedule_id:old.schedule_id,student_id:sid,purpose:'APPIUM API TEST'},s.auth_token)).success,true);
    assert.equal((await api('respond_appointment',{schedule_id:old.schedule_id,faculty_id:fid,status:'DECLINED',decline_reason:'APPIUM API TEST'})).success,true);
    const replacement=await api('recurring_save',one);assert.equal(replacement.success,true,replacement.message);createdIds.push(replacement.data.recurrence_id);
    assert.equal((await api('cancel',{schedule_id:old.schedule_id,student_id:sid},s.auth_token)).success,true);
    calendar=await api('recurring_calendar',{month:ym});assert.ok(!calendar.data.some(x=>x.schedule_id===old.schedule_id));assert.equal(calendar.data.filter(x=>x.recurrence_id===replacement.data.recurrence_id&&x.status==='OPEN').length,1);
  });
  await run('logout revokes only the supplied session',async()=>{const other=await login('9876','FACULTY');assert.equal((await api('auth_logout',{},other.auth_token)).success,true);assert.equal((await api('recurring_list',{},other.auth_token)).success,false);assert.equal((await api('recurring_list')).success,true);});
} finally {
  for(const id of createdIds) {const list=await api('recurring_list');const rule=list.data?.find(x=>x.recurrence_id===id);if(rule)await api('recurring_delete',{recurrence_id:id,version:rule.version});}
  mkdirSync('automation-tests/artifacts/recurring-api',{recursive:true});writeFileSync('automation-tests/artifacts/recurring-api/report.json',JSON.stringify({month:ym,results},null,2));
}
