import { spawnSync } from 'node:child_process';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const option = (name, fallback) => { const i = process.argv.indexOf(name); return i < 0 ? fallback : (process.argv[i + 1] || fallback); };
const server = option('--server', 'http://127.0.0.1:4723').replace(/\/$/, '');
const artifacts = path.join(root, 'automation-tests', 'artifacts', new Date().toISOString().replace(/[:.]/g, '-'));
mkdirSync(artifacts, { recursive: true });
const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || readFileSync(path.join(root, 'local.properties'), 'utf8').match(/^sdk.dir=(.+)$/m)?.[1].trim().replace(/\\([\\:])/g, '$1');
const adb = path.join(sdk, 'platform-tools', 'adb.exe');
const pkg = 'com.example.citecschedulingapp';
const activity = `${pkg}.LoginActivity`;
const report = { status: 'failed', startedAt: new Date().toISOString(), server, package: pkg, launchActivity: activity, artifacts, steps: [] };
let session;
function command(executable, args, log, timeout = 120000) {
  const result = spawnSync(executable, args, { cwd: root, encoding: 'utf8', timeout, windowsHide: true });
  const output = (result.stdout || '') + (result.stderr || '');
  if (log) writeFileSync(path.join(artifacts, log), output);
  if (result.error || result.status !== 0) throw new Error(`${executable} failed: ${result.error?.message || output}`);
  return output;
}
async function request(method, route, body) {
  const response = await fetch(server + route, { method, headers: { 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(180000) });
  const result = await response.json();
  if (!response.ok || result.value?.error) throw new Error(`${method} ${route}: ${result.value?.message || JSON.stringify(result)}`);
  return result.value;
}
const api = (method, route, body) => request(method, `/session/${session}${route}`, body);
async function capture() {
  if ((process.argv.includes('--functional') || process.argv.includes('--records')) && (await api('GET', '/appium/device/current_activity')).endsWith('LoginActivity')) return;
  writeFileSync(path.join(artifacts, 'screen.png'), Buffer.from(await api('GET', '/screenshot'), 'base64'));
  writeFileSync(path.join(artifacts, 'screen.xml'), await api('GET', '/source'));
}
try {
  if (!(await request('GET', '/status')).ready) throw new Error('Appium server is not ready.');
  const devices = command(adb, ['devices', '-l'], 'devices.log').split(/\r?\n/).filter(line => /^\S+\s+device\s/.test(line)).map(line => line.split(/\s+/)[0]).filter(id => !id.startsWith('emulator-'));
  const udid = option('--udid', devices.length === 1 ? devices[0] : '');
  if (!udid || !devices.includes(udid)) throw new Error('Connect and authorize one physical phone, or specify --udid for a connected physical phone.');
  report.udid = udid;
  console.log(`Building debug APK for ${udid}...`);
  command('cmd.exe', ['/d', '/c', 'gradlew.bat :app:assembleDebug --console=plain'], 'build.log', 900000);
  report.steps.push('Debug APK built');
  const apk = path.join(root, 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk');
  report.apk = apk;
  console.log('Installing APK (preserving app data)...');
  command(adb, ['-s', udid, 'install', '-r', apk], 'install.log');
  report.steps.push('APK installed');
  const resolved = command(adb, ['-s', udid, 'shell', 'cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.LAUNCHER', pkg], 'launcher.log');
  if (!resolved.includes('/' + '.LoginActivity') && !resolved.includes('/' + activity)) throw new Error(`Unexpected installed launcher: ${resolved}`);
  console.log('Starting Appium UiAutomator2 session...');
  const created = await request('POST', '/session', { capabilities: { alwaysMatch: {
    platformName: 'Android', 'appium:automationName': 'UiAutomator2', 'appium:udid': udid,
    'appium:appPackage': pkg, 'appium:appActivity': activity,
    'appium:appWaitActivity': `${activity},${pkg}.HomeActivity,${pkg}.FacultyHomeActivity`,
    'appium:noReset': true, 'appium:fullReset': false, 'appium:forceAppLaunch': true,
    'appium:ignoreHiddenApiPolicyError': true,
    'appium:skipDeviceInitialization': true,
    'appium:shouldTerminateApp': false, 'appium:newCommandTimeout': 120
  }, firstMatch: [{}] } });
  session = created.sessionId;
  report.steps.push('App launched through UiAutomator2');
  const screens = { LoginActivity: ['tvWelcomeTitle', 'etIdentifier', 'etPassword', 'btnLogin'], HomeActivity: ['bottomNavigation'], FacultyHomeActivity: ['bottomNavigation'] };
  const deadline = Date.now() + 45000;
  let lastError;
  while (Date.now() < deadline) {
    try {
      const currentPackage = await api('GET', '/appium/device/current_package');
      const currentActivity = await api('GET', '/appium/device/current_activity');
      const screen = currentActivity.split('.').at(-1);
      if (currentPackage !== pkg || !screens[screen]) throw new Error(`Unexpected foreground: ${currentPackage}/${currentActivity}`);
      for (const id of screens[screen]) {
        const element = await api('POST', '/element', { using: 'id', value: `${pkg}:id/${id}` });
        const elementId = element['element-6066-11e4-a52e-4f735466cecf'];
        if (!(await api('GET', `/element/${elementId}/displayed`))) throw new Error(`${id} is not visible`);
      }
      report.screen = screen;
      report.verifiedResourceIds = screens[screen];
      break;
    } catch (error) { lastError = error; await new Promise(resolve => setTimeout(resolve, 1000)); }
  }
  if (!report.screen) throw lastError || new Error('Main screen did not load');
  await capture();
  report.steps.push('Screen controls visible; screenshot and UI hierarchy captured');
  if (process.argv.includes('--functional')) {
    const { runFunctions } = await import('./functions.mjs');
    await runFunctions({ api, artifacts, report, pkg });
  }
  if (process.argv.includes('--records')) {
    const { runRecordTests } = await import('./records.mjs');
    await runRecordTests({ api, artifacts, report, pkg });
  }
  report.status = 'passed';
  console.log(`PASS: ${report.screen}. Screenshot: ${path.join(artifacts, 'screen.png')}`);
} catch (error) {
  report.error = error.message;
  console.error(error.message);
  process.exitCode = 1;
  if (session) { try { await capture(); } catch (captureError) { report.captureError = captureError.message; } }
} finally {
  if (session) { try { await request('DELETE', `/session/${session}`); } catch (error) { report.cleanupError = error.message; process.exitCode = 1; report.status = 'failed'; } }
  report.finishedAt = new Date().toISOString();
  writeFileSync(path.join(artifacts, 'report.json'), JSON.stringify(report, null, 2));
  console.log(`Report: ${path.join(artifacts, 'report.json')}`);
}
