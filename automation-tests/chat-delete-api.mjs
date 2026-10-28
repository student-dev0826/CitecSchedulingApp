import assert from 'node:assert/strict';
import { mkdirSync, writeFileSync } from 'node:fs';
const base = 'http://127.0.0.1:8087/api/';
const results = [];
const post = async (file, fields) => (await fetch(base + file, { method: 'POST', body: new URLSearchParams(fields) })).json();
const login = async (id, role) => {
  const r = await post('login.php', { identifier: id, role, password: process.env.TEST_PASSWORD });
  assert.equal(r.success, true, r.message); return r;
};
const student = await login('98765', 'STUDENT'), professor = await login('9876', 'FACULTY');
const sid = '98765', fid = `PROF-${professor.user.user_id}`;
const api = (who, action, fields = {}) => post('chat.php', { action, auth_token: who.auth_token, ...fields });
const fetchChat = (who, other) => api(who, 'fetch', { user2: other });
const send = async (who, other, text) => {
  const r = await api(who, 'send', { recipient_id: other, sender_name: 'Automation', recipient_name: 'Automation', message_text: text });
  assert.equal(r.success, true, r.message); return r.data.message_id;
};
const run = async (name, fn) => { await fn(); results.push(name); console.log('PASS', name); };
const tag = `DELETE TEST ${Date.now()}`;
try {
  await run('missing authentication rejected', async () => assert.equal((await post('chat.php', { action: 'delete_message', message_id: 1 })).success, false));
  await run('forged actor rejected for deletion and reads', async () => {
    for (const action of ['delete_message', 'delete_conversation', 'conversations', 'mark_read']) {
      assert.equal((await api(student, action, { user_id: fid, message_id: 1, contact_id: sid, through_message_id: 1 })).success, false);
    }
    assert.equal((await api(student, 'fetch', { user1: fid, user2: sid })).success, false);
    assert.equal((await api(student, 'send', { sender_id: fid, recipient_id: sid, message_text: tag })).success, false);
  });
  await run('invalid and nonexistent IDs rejected', async () => {
    for (const id of ['abc', '-1', '2147483647']) assert.equal((await api(student, 'delete_message', { message_id: id })).success, false);
  });
  const sent = await send(student, fid, tag + ' sent');
  const incoming = await send(professor, sid, tag + ' incoming');
  await run('remove sent message only from own view, idempotently', async () => {
    for (let i = 0; i < 2; i++) assert.equal((await api(student, 'delete_message', { message_id: sent })).success, true);
    assert.ok(!(await fetchChat(student, fid)).data.some(x => +x.message_id === sent));
    assert.ok((await fetchChat(professor, sid)).data.some(x => +x.message_id === sent));
  });
  await run('remove received message and update unread counts', async () => {
    const before = (await api(student, 'conversations')).data.find(x => x.contact_id === fid).unread;
    assert.equal((await api(student, 'delete_message', { message_id: incoming })).success, true);
    const after = (await api(student, 'conversations')).data.find(x => x.contact_id === fid)?.unread || 0;
    assert.equal(after, before - 1);
    assert.ok((await fetchChat(professor, sid)).data.some(x => +x.message_id === incoming));
  });
  await run('hidden received messages are not falsely marked read', async () => {
    assert.equal((await api(student, 'mark_read', { contact_id: fid })).success, true);
    assert.equal(+(await fetchChat(professor, sid)).data.find(x => +x.message_id === incoming).is_read, 0);
  });
  const boundary = await send(student, fid, tag + ' history');
  const later = await send(professor, sid, tag + ' later');
  await run('conversation removal preserves new arrivals and recipient copy', async () => {
    assert.equal((await api(student, 'delete_conversation', { contact_id: fid, through_message_id: boundary })).success, true);
    assert.deepEqual((await fetchChat(student, fid)).data.map(x => +x.message_id), [later]);
    assert.ok((await fetchChat(professor, sid)).data.some(x => +x.message_id === boundary));
  });
  await run('removal persists after fresh login', async () => {
    const again = await login('98765', 'STUDENT');
    assert.deepEqual((await fetchChat(again, fid)).data.map(x => +x.message_id), [later]);
  });
  await run('whole conversation disappears from inbox', async () => {
    assert.equal((await api(student, 'delete_conversation', { contact_id: fid, through_message_id: later })).success, true);
    assert.equal((await fetchChat(student, fid)).data.length, 0);
    assert.ok(!(await api(student, 'conversations')).data.some(x => x.contact_id === fid));
  });
  await run('professor can remove own conversation independently', async () => {
    assert.equal((await api(professor, 'delete_conversation', { contact_id: sid, through_message_id: later })).success, true);
    assert.equal((await fetchChat(professor, sid)).data.length, 0);
  });
} finally {
  mkdirSync('automation-tests/artifacts/chat-delete-api', { recursive: true });
  writeFileSync('automation-tests/artifacts/chat-delete-api/report.json', JSON.stringify({ passed: results.length, results }, null, 2));
}
