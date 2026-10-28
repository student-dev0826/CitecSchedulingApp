import { spawnSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const adb = path.join(process.env.ANDROID_HOME, 'platform-tools', 'adb.exe');
const pkg = 'com.example.citecschedulingapp';
const output = path.join(root, 'automation-tests/artifacts', `optimization-${Date.now()}`);
mkdirSync(output, { recursive: true });
function command(args) {
  const result = spawnSync(adb, args, { encoding: 'utf8', windowsHide: true, timeout: 120000 });
  if (result.status !== 0) throw new Error(result.stderr || result.error?.message);
  return result.stdout;
}
async function request(method, route, body) {
  const response = await fetch('http://127.0.0.1:4723' + route, {
    method, headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(180000)
  });
  const result = await response.json();
  if (!response.ok || result.value?.error) throw new Error(result.value?.message || response.statusText);
  return result.value;
}
let session;
try {
  const devices = command(['devices']).split(/\r?\n/).filter(line => /\sdevice$/.test(line)).map(line => line.split(/\s/)[0]);
  if (devices.length !== 1) throw new Error('Exactly one authorized phone is required.');
  command(['-s', devices[0], 'install', '-r', path.join(root, 'app/build/outputs/apk/debug/app-debug.apk')]);
  const created = await request('POST', '/session', { capabilities: { alwaysMatch: {
    platformName: 'Android', 'appium:automationName': 'UiAutomator2', 'appium:udid': devices[0],
    'appium:appPackage': pkg, 'appium:appActivity': `${pkg}.LoginActivity`,
    'appium:appWaitActivity': `${pkg}.*`, 'appium:noReset': true, 'appium:forceAppLaunch': true,
    'appium:ignoreHiddenApiPolicyError': true, 'appium:skipDeviceInitialization': true,
    'appium:shouldTerminateApp': false
  }, firstMatch: [{}] } });
  session = created.sessionId;
  const api = (method, route, body) => request(method, `/session/${session}${route}`, body);
  let source;
  for (let i = 0; i < 15; i++) {
    source = await api('GET', '/source');
    if (source.includes(`${pkg}:id/btnLogin`) || source.includes(`${pkg}:id/bottomNavigation`)) break;
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  if (!source.includes(`${pkg}:id/btnLogin`) && !source.includes(`${pkg}:id/bottomNavigation`)) throw new Error('Expected login or dashboard did not appear; check phone lock screen.');
  writeFileSync(path.join(output, 'screen.png'), Buffer.from(await api('GET', '/screenshot'), 'base64'));
  writeFileSync(path.join(output, 'report.json'), JSON.stringify({ status: 'passed', activity: await api('GET', '/appium/device/current_activity'), checks: ['Installed debug APK preserving app data', 'Launched through UiAutomator2', 'Login or dashboard controls visible'], timestamp: new Date().toISOString() }, null, 2));
  console.log(`PASS: ${output}`);
} finally {
  if (session) await request('DELETE', `/session/${session}`);
}
