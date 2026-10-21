import { spawnSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import path from 'node:path';

const pkg = 'com.example.citecschedulingapp.automation';
const adb = 'C:/Users/gibag/AppData/Local/Android/Sdk/platform-tools/adb.exe';
const out = path.resolve('automation-tests/artifacts', `weekly-ui-${new Date().toISOString().replace(/[:.]/g,'-')}`);
mkdirSync(out,{recursive:true});
const tag = `WEEKLY-UI-${Date.now()}`;
let session, ruleId, version, token;
const results=[];
async function request(method,route,body) {
  const r=await fetch('http://127.0.0.1:4723'+route,{method,headers:{'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(180000)});
  const data=await r.json();if(!r.ok||data.value?.error)throw new Error(data.value?.message||JSON.stringify(data));return data.value;
}
const api=(method,route,body)=>request(method,`/session/${session}${route}`,body);
const key='element-6066-11e4-a52e-4f735466cecf';
const find=async(value,using='id')=>(await api('POST','/element',{using,value:using==='id'&&!value.includes(':id/')?`${pkg}:id/${value}`:value}))[key];
const clickElement=e=>api('POST',`/element/${e}/click`,{});
async function click(id,using='id') {
 try {
   let e; try { e=await find(id,using); if(!await api('GET',`/element/${e}/displayed`)) throw new Error('offscreen'); }
   catch(error) { if(using!=='id'||id.includes(':id/'))throw error; try { await find(`new UiScrollable(new UiSelector().className("android.widget.ScrollView").scrollable(true)).scrollIntoView(new UiSelector().resourceId("${pkg}:id/${id}"))`,'-android uiautomator'); } catch {} await pause(600); e=await find(id,using); }
   return await clickElement(e);
 } catch(error) { throw new Error(`Click ${id}: ${error.message}`); }
}
const text=async id=>api('GET',`/element/${await find(id)}/text`);
const pause=ms=>new Promise(r=>setTimeout(r,ms));
async function wait(fn,timeout=30000){let err;const end=Date.now()+timeout;do{try{return await fn();}catch(e){err=e;await pause(500);}}while(Date.now()<end);throw err;}
async function scrollDescription(description){try {const e=await find(description,'accessibility id'); if(await api('GET',`/element/${e}/displayed`))return e;}catch{}try{await find(`new UiScrollable(new UiSelector().className("android.widget.ScrollView").scrollable(true)).scrollIntoView(new UiSelector().description(${JSON.stringify(description)}))`,'-android uiautomator');}catch{}await pause(400);return find(description,'accessibility id');}
async function type(id,value,description=false){const e=description?await scrollDescription(id):await find(id);await api('POST',`/element/${e}/clear`,{});await api('POST',`/element/${e}/value`,{text:value});}
async function capture(name){writeFileSync(path.join(out,`${name}.png`),Buffer.from(await api('GET','/screenshot'),'base64'));writeFileSync(path.join(out,`${name}.xml`),await api('GET','/source'));}
async function check(name,fn){console.log('RUN',name);await fn();results.push({name,status:'passed'});console.log('PASS',name);await capture(name);}
async function backend(action,fields={}){const r=await fetch('http://127.0.0.1:8087/api/schedules.php',{method:'POST',body:new URLSearchParams({action,auth_token:token,...fields})});return r.json();}
async function login(){await wait(()=>find('btnLogin'));await click('btnRoleFaculty');await type('etIdentifier','9876');await type('etPassword',process.env.TEST_PASSWORD);await click('btnLogin');await wait(()=>find('bottomNavigation'),45000);try{await click('com.android.permissioncontroller:id/permission_deny_button');}catch{}}
async function openCalendar(){await click('nav_faculty_availability');await click('btnWeeklyAvailability');await wait(()=>find('btnNextMonth'));await click('btnNextMonth');await wait(async()=>{assert.equal(await text('tvAvailabilityMonth'),monthTitle);});}
const next=new Date();next.setUTCDate(1);next.setUTCMonth(next.getUTCMonth()+1);
const month=next.toISOString().slice(0,7), monthTitle=new Intl.DateTimeFormat('en-US',{month:'long',year:'numeric',timeZone:'UTC'}).format(next);
try{
 const devices=spawnSync(adb,['devices'],{encoding:'utf8',windowsHide:true}).stdout.split(/\r?\n/).filter(x=>/\sdevice$/.test(x)&&!x.startsWith('emulator-')).map(x=>x.split(/\s/)[0]);assert.equal(devices.length,1);
 const installed=spawnSync(adb,['-s',devices[0],'install','-r','app/build/outputs/apk/debug/app-debug.apk'],{encoding:'utf8',windowsHide:true});assert.equal(installed.status,0,installed.stderr);
 const created=await request('POST','/session',{capabilities:{alwaysMatch:{platformName:'Android','appium:automationName':'UiAutomator2','appium:udid':devices[0],'appium:appPackage':pkg,'appium:appActivity':'com.example.citecschedulingapp.LoginActivity','appium:noReset':true,'appium:forceAppLaunch':true,'appium:ignoreHiddenApiPolicyError':true,'appium:skipDeviceInitialization':true,'appium:shouldTerminateApp':false},firstMatch:[{}]}});session=created.sessionId;
 const auth=await (await fetch('http://127.0.0.1:8087/api/login.php',{method:'POST',body:new URLSearchParams({identifier:'9876',password:process.env.TEST_PASSWORD,role:'FACULTY'})})).json();assert.equal(auth.success,true);token=auth.auth_token;
 await check('professor-login',login);
 await check('open-monthly-calendar',openCalendar);
 await check('create-tuesday-rule',async()=>{
   await click('btnAddWeekly');await wait(()=>find('Day of week','accessibility id'));console.log('Selecting Tuesday');await click('Day of week','accessibility id');await wait(()=>click('//*[@text="Tuesday"]','xpath'));await pause(400);
   console.log('Entering category');await type('Category',tag,true);console.log('Entering location');await type('Location','Local test office',true);try{await api('POST','/appium/device/hide_keyboard',{});}catch{}
   await click('android:id/button1');
   await wait(async()=>{const r=await backend('recurring_list');const rule=r.data?.find(x=>x.category===tag);assert.ok(rule,'Created rule not persisted');ruleId=rule.recurrence_id;version=rule.version;});
   await wait(async()=>assert.match(await text('android:id/message'),/available dates saved/));await click('android:id/button1');
   const calendar=await backend('recurring_calendar',{month});const rows=calendar.data.filter(x=>x.recurrence_id===ruleId);assert.ok(rows.length>=4);assert.ok(rows.every(x=>new Date(x.schedule_date+'T00:00:00Z').getUTCDay()===2));
 });
 await check('calendar-shows-tuesday-time',async()=>{const r=await backend('recurring_calendar',{month});const first=r.data.find(x=>x.recurrence_id===ruleId);await click(`${first.schedule_date}, 1 available slots, 0 appointments`,'accessibility id');await wait(async()=>assert.ok((await api('GET','/source')).includes('09:00 AM - 10:00 AM')));});
 await check('edit-weekly-rule',async()=>{await clickElement(await scrollDescription(`Edit weekly schedule ${ruleId}`));await type('Location','Updated local test office',true);try{await api('POST','/appium/device/hide_keyboard',{});}catch{}await click('android:id/button1');await wait(async()=>{const r=await backend('recurring_list');const rule=r.data.find(x=>x.recurrence_id===ruleId);assert.equal(rule.location,'Updated local test office');version=rule.version;});await wait(async()=>assert.match(await text('android:id/message'),/available dates saved/));await click('android:id/button1');});
 await check('reload-after-logout',async()=>{
   await api('POST','/back',{});await wait(()=>find('nav_faculty_profile'));await click('nav_faculty_profile');await click('btnLogout');await login();await openCalendar();
   await scrollDescription(`Edit weekly schedule ${ruleId}`);
   await wait(async()=>assert.ok((await api('GET','/source')).includes(tag)));
 });
 await check('delete-weekly-rule',async()=>{await clickElement(await scrollDescription(`Delete weekly schedule ${ruleId}`));await wait(()=>click('android:id/button1'));await wait(async()=>{assert.ok(!(await backend('recurring_list')).data.some(x=>x.recurrence_id===ruleId));assert.equal((await backend('recurring_calendar',{month})).data.filter(x=>x.recurrence_id===ruleId).length,0);});await wait(async()=>assert.match(await text('android:id/message'),/Recurring schedule removed/));await click('android:id/button1');});
}catch(e){results.push({name:'execution',status:'failed',error:e.message});console.error(e.message);process.exitCode=1;if(session)try{await capture('failure');}catch{}}
finally{
 if(ruleId&&token){const r=await backend('recurring_list');const rule=r.data?.find(x=>x.recurrence_id===ruleId);if(rule)await backend('recurring_delete',{recurrence_id:ruleId,version:rule.version});}
 if(session)try{await request('DELETE',`/session/${session}`);}catch{}
 writeFileSync(path.join(out,'report.json'),JSON.stringify({tag,package:pkg,month,results},null,2));console.log('Report:',path.join(out,'report.json'));
}
