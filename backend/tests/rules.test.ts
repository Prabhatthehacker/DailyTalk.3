import { readFileSync } from 'node:fs';
import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, assertSucceeds, initializeTestEnvironment, RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { get, ref, remove, serverTimestamp, set, update } from 'firebase/database';

let env: RulesTestEnvironment;
const hour = 3600_000;

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-dailytalk',
    database: { rules: readFileSync(new URL('../../database.rules.json', import.meta.url), 'utf8') },
  });
});
afterAll(async () => { await env.cleanup(); });

const as = (uid: string) => env.authenticatedContext(uid).database();
const anon = () => env.unauthenticatedContext().database();
const seed = (data: Record<string, unknown>) =>
  env.withSecurityRulesDisabled(async (ctx) => { await update(ref(ctx.database()), data); });

beforeEach(async () => {
  await env.clearDatabase();
  await seed({
    'conversations/c1/meta': { type: 'direct', createdBy: 'alice', createdAt: Date.now() },
    'conversations/c1/members/alice': true,
    'conversations/c1/members/bob': true,
    'conversations/g1/meta': { type: 'group', name: 'Team', createdBy: 'alice', createdAt: Date.now() },
    'conversations/g1/members/alice': true, 'conversations/g1/members/bob': true, 'conversations/g1/members/carol': true,
    'conversations/g1/admins/alice': true,
    'conversations/c1/messages/m1': { senderId: 'alice', senderName: 'Alice', kind: 'text', text: 'hi', createdAt: Date.now(), readBy: { alice: true } },
    'statuses/expired': { authorId: 'alice', authorName: 'Alice', text: 'old', createdAt: Date.now() - 30 * hour, expiresAt: Date.now() - 6 * hour, viewers: { bob: true } },
    'statuses/live': { authorId: 'alice', authorName: 'Alice', text: 'new', createdAt: Date.now(), expiresAt: Date.now() + 20 * hour, viewers: { bob: true } },
    'calls/call1': { callerId: 'alice', calleeId: 'bob', conversationId: 'c1', type: 'voice', state: 'ringing', offer: { type: 'offer', sdp: 'x' }, createdAt: Date.now() },
    'phoneIndex/4815162342': 'dave',
    'users/dave': { displayName: 'Dave', phone: '4815162342', createdAt: Date.now() },
  });
});

const msg = (senderId: string, extra: Record<string, unknown> = {}) => ({
  senderId, senderName: 'X', kind: 'text', text: 'hello', createdAt: serverTimestamp(), readBy: { [senderId]: true }, ...extra,
});

describe('conversations & messages', () => {
  it('denies unauthenticated reads', async () => {
    await assertFails(get(ref(anon(), 'conversations/c1')));
  });
  it('lets members read and denies non-members', async () => {
    await assertSucceeds(get(ref(as('bob'), 'conversations/c1/messages/m1')));
    await assertFails(get(ref(as('mallory'), 'conversations/c1/messages/m1')));
    await assertFails(get(ref(as('mallory'), 'conversations/c1')));
  });
  it('lets a member send and blocks spoofed senders and non-members', async () => {
    await assertSucceeds(set(ref(as('bob'), 'conversations/c1/messages/new1'), msg('bob')));
    await assertFails(set(ref(as('bob'), 'conversations/c1/messages/new2'), msg('alice')));
    await assertFails(set(ref(as('mallory'), 'conversations/c1/messages/new3'), msg('mallory')));
  });
  it('rejects oversize text, bad GIF hosts and overwriting a message', async () => {
    await assertFails(set(ref(as('bob'), 'conversations/c1/messages/n1'), msg('bob', { text: 'x'.repeat(4001) })));
    await assertFails(set(ref(as('bob'), 'conversations/c1/messages/n2'), msg('bob', { kind: 'gif', url: 'https://evil.example/x.gif', text: null })));
    await assertFails(set(ref(as('alice'), 'conversations/c1/messages/m1'), msg('alice', { text: 'edited' })));
  });
  it('accepts photo/video links only from the file host', async () => {
    const media = (kind: string, url?: string) => { const { text: _t, ...rest } = msg('bob'); return { ...rest, kind, ...(url ? { url } : {}) }; };
    await assertSucceeds(set(ref(as('bob'), 'conversations/c1/messages/p1'), media('image', 'https://files.catbox.moe/abc123.png')));
    await assertSucceeds(set(ref(as('bob'), 'conversations/c1/messages/p2'), media('video', 'https://files.catbox.moe/abc123.mp4')));
    await assertFails(set(ref(as('bob'), 'conversations/c1/messages/p3'), media('image', 'https://evil.example/a.png')));
    await assertFails(set(ref(as('bob'), 'conversations/c1/messages/p4'), media('image', 'http://files.catbox.moe/a.png')));
    await assertFails(set(ref(as('bob'), 'conversations/c1/messages/p5'), media('video')));
    await assertFails(set(ref(as('bob'), 'conversations/c1/messages/p6'), media('file', 'https://files.catbox.moe/abc123.pdf')));
  });
  it('allows only your own uid in readBy', async () => {
    await assertSucceeds(set(ref(as('bob'), 'conversations/c1/messages/m1/readBy/bob'), true));
    await assertFails(set(ref(as('bob'), 'conversations/c1/messages/m1/readBy/carol'), true));
    await assertFails(set(ref(as('mallory'), 'conversations/c1/messages/m1/readBy/mallory'), true));
    await assertFails(set(ref(as('bob'), 'conversations/c1/messages/m1/text'), 'edited'));
  });
});

describe('assigned numbers', () => {
  it('lets a new user claim a free 10-digit number and attach it to their profile', async () => {
    const db = as('erin');
    await assertSucceeds(set(ref(db, 'phoneIndex/1234567890'), 'erin'));
    await assertSucceeds(set(ref(db, 'users/erin'), { displayName: 'Erin', phone: '1234567890', createdAt: serverTimestamp() }));
  });
  it('refuses a number that is already taken, or claimed for someone else', async () => {
    await assertFails(set(ref(as('erin'), 'phoneIndex/4815162342'), 'erin'));
    await assertFails(set(ref(as('erin'), 'phoneIndex/5555555555'), 'dave'));
  });
  it('refuses malformed numbers and profiles pointing at numbers you do not own', async () => {
    await assertFails(set(ref(as('erin'), 'phoneIndex/0123456789'), 'erin'));
    await assertFails(set(ref(as('erin'), 'phoneIndex/12345'), 'erin'));
    await assertFails(set(ref(as('erin'), 'users/erin'), { displayName: 'Erin', phone: '4815162342', createdAt: serverTimestamp() }));
  });
  it('never lets a number change, be hijacked or be listed', async () => {
    await assertFails(update(ref(as('dave'), 'users/dave'), { phone: '1111111111' }));
    await assertSucceeds(update(ref(as('dave'), 'users/dave'), { displayName: 'Dave 2' }));
    await assertFails(set(ref(as('mallory'), 'phoneIndex/4815162342'), 'mallory'));
    await assertFails(remove(ref(as('mallory'), 'phoneIndex/4815162342')));
    await assertSucceeds(get(ref(as('mallory'), 'phoneIndex/4815162342')));
    await assertFails(get(ref(as('mallory'), 'phoneIndex')));
  });
});

describe('groups', () => {
  it('lets only admins rename and add members', async () => {
    await assertFails(set(ref(as('bob'), 'conversations/g1/meta/name'), 'Hacked'));
    await assertSucceeds(set(ref(as('alice'), 'conversations/g1/meta/name'), 'Renamed'));
    await assertSucceeds(set(ref(as('alice'), 'conversations/g1/members/dave'), true));
    await assertFails(set(ref(as('bob'), 'conversations/g1/members/eve'), true));
  });
  it('lets a member leave but not remove others', async () => {
    await assertFails(remove(ref(as('alice'), 'conversations/g1/members/bob')));
    await assertSucceeds(remove(ref(as('carol'), 'conversations/g1/members/carol')));
  });
  it('lets the creator set up a brand-new conversation once', async () => {
    const db = as('frank');
    await assertSucceeds(set(ref(db, 'conversations/new/meta'), { type: 'direct', createdBy: 'frank', createdAt: serverTimestamp() }));
    await assertSucceeds(update(ref(db), { 'conversations/new/members/frank': true, 'conversations/new/members/gina': true }));
    await assertFails(set(ref(as('mallory'), 'conversations/new/members/mallory'), true));
    await assertFails(set(ref(as('mallory'), 'conversations/new/meta'), { type: 'direct', createdBy: 'mallory', createdAt: serverTimestamp() }));
  });
});

describe('chat list entries', () => {
  it('lets you read only your own list, and write to fellow members of a conversation', async () => {
    await assertSucceeds(set(ref(as('alice'), 'userChats/alice/c1/type'), 'direct'));
    await assertFails(get(ref(as('bob'), 'userChats/alice')));
    await assertSucceeds(set(ref(as('alice'), 'userChats/bob/c1/updatedAt'), serverTimestamp()));
    await assertFails(set(ref(as('mallory'), 'userChats/bob/c1/updatedAt'), serverTimestamp()));
  });
});

describe('status', () => {
  it('hides expired statuses and statuses from non-audience users', async () => {
    await assertSucceeds(get(ref(as('bob'), 'statuses/live')));
    await assertFails(get(ref(as('bob'), 'statuses/expired')));
    await assertFails(get(ref(as('mallory'), 'statuses/live')));
  });
  const status = (expiresInMs: number) => ({
    authorId: 'alice', authorName: 'Alice', text: 'hey', viewers: { bob: true },
    createdAt: serverTimestamp(), expiresAt: Date.now() + expiresInMs,
  });
  it('forces a ~24h lifetime on creation', async () => {
    await assertSucceeds(set(ref(as('alice'), 'statuses/s1'), status(24 * hour)));
    await assertFails(set(ref(as('alice'), 'statuses/s2'), status(72 * hour)));
    await assertFails(set(ref(as('alice'), 'statuses/s3'), status(1 * hour)));
  });
  it('lets only the author delete', async () => {
    await assertFails(remove(ref(as('bob'), 'statuses/live')));
    await assertSucceeds(remove(ref(as('alice'), 'statuses/live')));
  });
  it('shows a status feed entry only to the audience', async () => {
    await assertSucceeds(set(ref(as('alice'), 'statusFeed/bob/live'), { authorId: 'alice', createdAt: 1, expiresAt: Date.now() + hour }));
    await assertFails(set(ref(as('alice'), 'statusFeed/mallory/live'), { authorId: 'alice', createdAt: 1, expiresAt: Date.now() + hour }));
  });
});

describe('call signaling', () => {
  it('is visible to the two participants only', async () => {
    await assertSucceeds(get(ref(as('bob'), 'calls/call1')));
    await assertFails(get(ref(as('mallory'), 'calls/call1')));
    await assertFails(set(ref(as('mallory'), 'calls/call1/candidates/x'), { from: 'mallory', candidate: 'c' }));
  });
  it('only allows calls between two members of a conversation', async () => {
    const call = (callee: string) => ({
      callerId: 'alice', calleeId: callee, conversationId: 'c1', type: 'voice', state: 'ringing',
      offer: { type: 'offer', sdp: 'x' }, createdAt: serverTimestamp(),
    });
    await assertSucceeds(set(ref(as('alice'), 'calls/ok'), call('bob')));
    await assertFails(set(ref(as('alice'), 'calls/bad'), call('mallory')));
  });
  it('lets only the callee answer', async () => {
    await assertSucceeds(set(ref(as('bob'), 'calls/call1/answer'), { type: 'answer', sdp: 'y' }));
    await assertFails(set(ref(as('alice'), 'calls/call1/answer'), { type: 'answer', sdp: 'y' }));
    await assertSucceeds(set(ref(as('alice'), 'calls/call1/state'), 'ended'));
    await assertFails(set(ref(as('mallory'), 'calls/call1/state'), 'ended'));
  });
});
