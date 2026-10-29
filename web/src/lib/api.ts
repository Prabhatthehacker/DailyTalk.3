import {
  get, increment, limitToLast, onValue, orderByChild, push, query, ref, remove, serverTimestamp, set, startAt, update,
  type Unsubscribe,
} from 'firebase/database';
import { createUserWithEmailAndPassword, deleteUser, GoogleAuthProvider, sendPasswordResetEmail, signInWithEmailAndPassword, signInWithPopup, signOut, updateProfile, type User } from 'firebase/auth';
import { auth, db } from './firebase';
import { generateNumber, normalizeNumber } from './number';
import { HOSTED_URL } from './media';
import type { Contact, Conversation, Message, MessageKind, Profile, Status } from './types';

/* Realtime Database layout (see database.rules.json):
 *  users/{uid}                      displayName, phone, createdAt        (public profile)
 *  phoneIndex/{number} = uid        create-only, so numbers can never be taken over
 *  contacts/{uid}/{number} = name   private
 *  cleared/{uid}/{cid} = time       per-user "delete conversation"
 *  userChats/{uid}/{cid}            the user's chat-list entry (type, name, members, admins, last, updatedAt, unread)
 *  conversations/{cid}/{meta,members,admins,messages}
 *  statuses/{sid}, statusFeed/{uid}/{sid}, statusViews/{sid}/{uid}
 *  calls/{id}, callInbox/{uid}/{id}
 *  */
export const R = (path = '') => (path ? ref(db, path) : ref(db));
const keysOf = (o: unknown): string[] => Object.keys((o as Record<string, unknown> | null) ?? {});
const denied = (e: unknown) => String((e as { code?: string; message?: string })?.code ?? '') === 'PERMISSION_DENIED' || String((e as Error)?.message ?? e).includes('PERMISSION_DENIED');

// ---------------------------------------------------------------- auth
export function authMessage(err: unknown): string {
  const code = (err as { code?: string })?.code ?? '';
  const map: Record<string, string> = {
    'auth/invalid-email': 'That email address is not valid.',
    'auth/missing-password': 'Enter your password.',
    'auth/weak-password': 'Choose a password at least 6 characters long.',
    'auth/email-already-in-use': 'An account with this email already exists. Sign in instead.',
    'auth/invalid-credential': 'Email or password is incorrect.',
    'auth/user-not-found': 'Email or password is incorrect.',
    'auth/wrong-password': 'Email or password is incorrect.',
    'auth/too-many-requests': 'Too many attempts. Try again later.',
    'auth/network-request-failed': 'Cannot reach the server. Check your connection.',
    'auth/requires-recent-login': 'For safety, sign out, sign in again, then retry.',
    'auth/operation-not-allowed': 'This sign-in method is not enabled in the Firebase Console.',
    'auth/popup-closed-by-user': 'The Google sign-in window was closed before finishing.',
    'auth/cancelled-popup-request': 'The Google sign-in window was closed before finishing.',
    'auth/popup-blocked': 'Your browser blocked the sign-in popup. Allow pop-ups and try again.',
    'auth/unauthorized-domain': 'This website address is not allowed to sign in yet. Add it under Authentication, Settings, Authorized domains in Firebase.',
    'auth/account-exists-with-different-credential': 'An account with this email already exists. Sign in with your email and password instead.',
  };
  return map[code] ?? 'Something went wrong. Please try again.';
}

export async function register(name: string, email: string, password: string): Promise<void> {
  const cred = await createUserWithEmailAndPassword(auth, email.trim(), password);
  await updateProfile(cred.user, { displayName: name.trim() });
  await ensureAccount(cred.user.uid, name.trim());
}
export const login = (email: string, password: string) => signInWithEmailAndPassword(auth, email.trim(), password);
export const signInWithGoogle = () => signInWithPopup(auth, new GoogleAuthProvider());
export async function logout(): Promise<void> {
  profileCache.clear();
  await signOut(auth);
}
export const resetPassword = (email: string) => sendPasswordResetEmail(auth, email.trim());

// ---------------------------------------------------------------- profile & assigned number
export function watchProfile(uid: string, cb: (p: Profile | null) => void): Unsubscribe {
  return onValue(R(`users/${uid}`), (s) => cb(s.exists() ? {
    uid,
    displayName: s.val().displayName ?? 'Unknown',
    phone: s.val().phone ?? null,
    photoURL: s.val().photoURL ?? null,
  } : null), () => cb(null));
}
const profileCache = new Map<string, Profile>();
export async function fetchProfiles(uids: string[]): Promise<Profile[]> {
  return Promise.all(uids.map(async (uid) => {
    const hit = profileCache.get(uid);
    if (hit) return hit;
    const s = await get(R(`users/${uid}`));
    const p: Profile = {
      uid,
      displayName: s.exists() ? s.val().displayName ?? 'Unknown' : 'Unknown',
      phone: s.exists() ? s.val().phone ?? null : null,
      photoURL: s.exists() ? s.val().photoURL ?? null : null,
    };
    profileCache.set(uid, p);
    return p;
  }));
}
export const dropProfileCache = (uid: string) => profileCache.delete(uid);

export async function ensureAccount(uid: string, name: string): Promise<string> {
  let lastError: unknown;
  for (let attempt = 0; attempt < 12; attempt++) {
    const existing = await get(R(`users/${uid}`));
    if (existing.exists() && existing.val().phone) return existing.val().phone as string;
    const number = generateNumber();
    try {
      await set(R(`phoneIndex/${number}`), uid);
    } catch (e) {
      lastError = e;
      if (!denied(e)) throw e;
      continue;
    }
    try {
      await update(R(`users/${uid}`), existing.exists() ? { phone: number } : { displayName: name, phone: number, photoURL: auth.currentUser?.photoURL ?? null, createdAt: serverTimestamp() });
      dropProfileCache(uid);
      return number;
    } catch (e) {
      lastError = e;
      await remove(R(`phoneIndex/${number}`)).catch(() => undefined);
      if (!denied(e)) throw e;
    }
  }
  throw lastError ?? new Error('Could not create your number. Please try again.');
}

export async function updateDisplayName(uid: string, name: string) {
  if (!name.trim()) throw new Error('Enter your name.');
  const clean = name.trim();
  await update(R(`users/${uid}`), { displayName: clean, photoURL: auth.currentUser?.photoURL ?? null });
  if (auth.currentUser) await updateProfile(auth.currentUser, { displayName: clean, photoURL: auth.currentUser.photoURL ?? null });
  dropProfileCache(uid);
}

// ---------------------------------------------------------------- contacts
export function watchContacts(uid: string, cb: (c: Contact[]) => void): Unsubscribe {
  return onValue(R(`contacts/${uid}`), (s) => cb(Object.entries((s.val() ?? {}) as Record<string, string>).map(([number, name]) => ({ number, name }))), () => cb([]));
}
export async function saveContact(uid: string, number: string, name: string) {
  if (!name.trim()) throw new Error('Enter a name.');
  await set(R(`contacts/${uid}/${number}`), name.trim());
}
export const deleteContact = (uid: string, number: string) => remove(R(`contacts/${uid}/${number}`));

// ---------------------------------------------------------------- conversations
interface ChatEntry {
  type: 'direct' | 'group'; name?: string; members?: Record<string, true>; admins?: Record<string, true>;
  last?: { text: string; senderId: string }; updatedAt?: number; unread?: number;
}

export function watchConversations(uid: string, cb: (c: Conversation[]) => void): Unsubscribe {
  return onValue(R(`userChats/${uid}`), (s) => {
    const all = (s.val() ?? {}) as Record<string, ChatEntry>;
    cb(Object.entries(all).map(([id, v]): Conversation => ({
      id, type: v.type, members: keysOf(v.members), admins: keysOf(v.admins), name: v.name,
      lastMessage: v.last ? { text: v.last.text, senderId: v.last.senderId } : undefined,
      updatedAt: v.updatedAt ?? 0, unread: { [uid]: v.unread ?? 0 },
    })).sort((a, b) => b.updatedAt - a.updatedAt));
  }, () => cb([]));
}
export function watchCleared(uid: string, cb: (m: Map<string, number>) => void): Unsubscribe {
  return onValue(R(`cleared/${uid}`), (s) => cb(new Map(Object.entries((s.val() ?? {}) as Record<string, number>))), () => cb(new Map()));
}

const entryFor = (type: 'direct' | 'group', members: string[], admins: string[], name?: string) => ({
  type, ...(name ? { name } : {}), members: Object.fromEntries(members.map((m) => [m, true])),
  ...(admins.length ? { admins: Object.fromEntries(admins.map((m) => [m, true])) } : {}), updatedAt: serverTimestamp(), unread: 0,
});

async function ensureConversation(cid: string, type: 'direct' | 'group', members: string[], me: string, name?: string) {
  const mine = await get(R(`conversations/${cid}/members/${me}`)).then((s) => s.val() === true).catch(() => false);
  if (!mine) {
    await set(R(`conversations/${cid}/meta`), { type, createdBy: me, createdAt: serverTimestamp(), ...(name ? { name } : {}) });
    const base: Record<string, unknown> = {};
    members.forEach((m) => { base[`conversations/${cid}/members/${m}`] = true; });
    if (type === 'group') base[`conversations/${cid}/admins/${me}`] = true;
    await update(R(), base);
    const entries: Record<string, unknown> = {};
    members.forEach((m) => { entries[`userChats/${m}/${cid}`] = entryFor(type, members, type === 'group' ? [me] : [], name); });
    await update(R(), entries);
  }
  await remove(R(`cleared/${me}/${cid}`)).catch(() => undefined);
}

export async function startChatWithNumber(me: Profile, raw: string): Promise<string> {
  const number = normalizeNumber(raw);
  if (!number) throw new Error('Enter a 10-digit Daily Talk number.');
  if (number === me.phone) throw new Error('That is your own number.');
  const idx = await get(R(`phoneIndex/${number}`));
  if (!idx.exists()) throw new Error('No Daily Talk account has this number. Check it and try again.');
  const peerUid = idx.val() as string;
  const cid = `d_${[me.uid, peerUid].sort().join('_')}`;
  await ensureConversation(cid, 'direct', [me.uid, peerUid], me.uid);
  return cid;
}

export async function resolveNumbers(numbers: string[], self: string) {
  const uids: string[] = []; const missing: string[] = [];
  for (const raw of numbers.map((n) => n.trim()).filter(Boolean)) {
    const number = normalizeNumber(raw);
    if (!number) { missing.push(raw); continue; }
    const idx = await get(R(`phoneIndex/${number}`));
    const uid: string | undefined = idx.exists() ? (idx.val() as string) : undefined;
    if (!uid) missing.push(raw); else if (uid !== self && !uids.includes(uid)) uids.push(uid);
  }
  return { uids, missing };
}

export async function createGroup(me: Profile, name: string, numbers: string[]) {
  if (!name.trim()) throw new Error('Enter a group name.');
  const { uids, missing } = await resolveNumbers(numbers, me.uid);
  const id = push(R('conversations')).key!;
  await ensureConversation(id, 'group', [me.uid, ...uids], me.uid, name.trim());
  return { id, missing };
}

export async function addMembers(conv: Conversation, numbers: string[], self: string) {
  const { uids, missing } = await resolveNumbers(numbers, self);
  const add = uids.filter((u) => !conv.members.includes(u));
  if (add.length) {
    const grant: Record<string, unknown> = {};
    add.forEach((m) => { grant[`conversations/${conv.id}/members/${m}`] = true; });
    await update(R(), grant);
    const all = [...conv.members, ...add];
    const entries: Record<string, unknown> = {};
    add.forEach((m) => { entries[`userChats/${m}/${conv.id}`] = entryFor('group', all, conv.admins ?? [], conv.name); });
    conv.members.forEach((m) => add.forEach((n) => { entries[`userChats/${m}/${conv.id}/members/${n}`] = true; }));
    await update(R(), entries);
  }
  return { added: add.length, missing };
}
export async function renameGroup(conv: Conversation, name: string) {
  const n = name.trim();
  if (!n) throw new Error('Enter a group name.');
  const u: Record<string, unknown> = { [`conversations/${conv.id}/meta/name`]: n };
  conv.members.forEach((m) => { u[`userChats/${m}/${conv.id}/name`] = n; });
  await update(R(), u);
}
export async function leaveGroup(c: Conversation, uid: string) {
  const u: Record<string, unknown> = {
    [`conversations/${c.id}/members/${uid}`]: null, [`userChats/${uid}/${c.id}`]: null,
  };
  if (c.admins?.includes(uid)) u[`conversations/${c.id}/admins/${uid}`] = null;
  c.members.filter((m) => m !== uid).forEach((m) => {
    u[`userChats/${m}/${c.id}/members/${uid}`] = null;
    if (c.admins?.includes(uid)) u[`userChats/${m}/${c.id}/admins/${uid}`] = null;
  });
  await update(R(), u);
}
export const clearConversation = (uid: string, cid: string) => set(R(`cleared/${uid}/${cid}`), serverTimestamp());
export const markConversationRead = (cid: string, uid: string) => set(R(`userChats/${uid}/${cid}/unread`), 0);

// ---------------------------------------------------------------- messages (text, GIFs, photos, videos)
const toMsg = (id: string, d: Record<string, unknown>): Message => ({
  id, senderId: d.senderId as string, senderName: d.senderName as string, kind: d.kind as MessageKind,
  text: d.text as string | undefined, url: d.url as string | undefined,
  createdAt: (d.createdAt as number) ?? Date.now(), readBy: keysOf(d.readBy), state: 'sent',
});

export function watchMessages(cid: string, pageSize: number, after: number | undefined, cb: (m: Message[]) => void): Unsubscribe {
  const base = R(`conversations/${cid}/messages`);
  const q = after ? query(base, orderByChild('createdAt'), startAt(after + 1), limitToLast(pageSize)) : query(base, orderByChild('createdAt'), limitToLast(pageSize));
  return onValue(q, (s) => {
    const out: Message[] = [];
    s.forEach((c) => { out.push(toMsg(c.key!, c.val())); });
    cb(out.sort((a, b) => a.createdAt - b.createdAt));
  }, () => cb([]));
}

export interface OutgoingMessage { id: string; kind: MessageKind; text?: string; url?: string }
export const newMessageId = (cid: string) => push(R(`conversations/${cid}/messages`)).key!;

const previewOf = (m: OutgoingMessage, text?: string) =>
  m.kind === 'gif' ? 'GIF' : m.kind === 'image' ? 'Photo' : m.kind === 'video' ? 'Video' : (text ?? '').slice(0, 120);

export async function sendMessage(conv: Conversation, me: Profile, m: OutgoingMessage) {
  const body: Record<string, unknown> = {
    senderId: me.uid, senderName: me.displayName, kind: m.kind, createdAt: serverTimestamp(), readBy: { [me.uid]: true },
  };
  if (m.kind === 'text') {
    const text = (m.text ?? '').trim();
    if (!text || text.length > 4000) throw new Error('Messages must be 1–4000 characters.');
    body.text = text;
  } else {
    if (m.kind !== 'gif' && !(m.url && HOSTED_URL.test(m.url))) throw new Error('The photo or video is not uploaded yet.');
    body.url = m.url;
  }
  const u: Record<string, unknown> = { [`conversations/${conv.id}/messages/${m.id}`]: body };
  const last = { text: previewOf(m, body.text as string | undefined), senderId: me.uid };
  for (const member of conv.members) {
    u[`userChats/${member}/${conv.id}/last`] = last;
    u[`userChats/${member}/${conv.id}/updatedAt`] = serverTimestamp();
    if (member !== me.uid) u[`userChats/${member}/${conv.id}/unread`] = increment(1);
  }
  await update(R(), u);
}

export async function markMessagesRead(cid: string, uid: string, msgs: Message[]) {
  const todo = msgs.filter((m) => m.state === 'sent' && m.senderId !== uid && !m.readBy.includes(uid)).slice(0, 400);
  if (!todo.length) return;
  const u: Record<string, unknown> = {};
  todo.forEach((m) => { u[`conversations/${cid}/messages/${m.id}/readBy/${uid}`] = true; });
  await update(R(), u);
}

// ---------------------------------------------------------------- status (text only, 24 hours)
const toStatus = (id: string, d: Record<string, unknown>): Status => ({
  id, authorId: d.authorId as string, authorName: d.authorName as string, kind: 'text', text: (d.text as string) ?? '',
  createdAt: (d.createdAt as number) ?? 0, expiresAt: (d.expiresAt as number) ?? 0, viewers: keysOf(d.viewers),
});

export function watchStatuses(uid: string, cb: (s: Status[]) => void): Unsubscribe {
  const cache = new Map<string, Status | null>();
  let live = true;
  const off = onValue(R(`statusFeed/${uid}`), async (feed) => {
    const entries = (feed.val() ?? {}) as Record<string, { authorId: string; expiresAt: number }>;
    const now = Date.now();
    await Promise.all(Object.entries(entries).map(async ([sid, e]) => {
      if (e.expiresAt <= now) {
        void remove(R(`statusFeed/${uid}/${sid}`)).catch(() => undefined);
        if (e.authorId === uid) void remove(R(`statuses/${sid}`)).catch(() => undefined);
        cache.delete(sid); return;
      }
      if (cache.has(sid)) return;
      try {
        const s = await get(R(`statuses/${sid}`));
        cache.set(sid, s.exists() ? toStatus(sid, s.val()) : null);
        if (!s.exists()) void remove(R(`statusFeed/${uid}/${sid}`)).catch(() => undefined);
      } catch (err) {
        if (denied(err)) { cache.set(sid, null); void remove(R(`statusFeed/${uid}/${sid}`)).catch(() => undefined); }
      }
    }));
    if (!live) return;
    const ids = new Set(Object.keys(entries));
    cb([...cache.entries()].filter(([id, s]) => ids.has(id) && s && s.expiresAt > Date.now()).map(([, s]) => s as Status).sort((a, b) => b.createdAt - a.createdAt));
  }, () => cb([]));
  return () => { live = false; off(); };
}

export async function publishStatus(me: Profile, viewers: string[], text: string) {
  const t = text.trim();
  if (!t) throw new Error('Write something to share.');
  const sid = push(R('statuses')).key!;
  const audience = [...new Set(viewers.filter((v) => v !== me.uid))].slice(0, 500);
  const expiresAt = Date.now() + 24 * 3600_000;
  await set(R(`statuses/${sid}`), {
    authorId: me.uid, authorName: me.displayName, text: t.slice(0, 700), createdAt: serverTimestamp(), expiresAt,
    viewers: Object.fromEntries(audience.map((v) => [v, true])),
  });
  const feed: Record<string, unknown> = {};
  [me.uid, ...audience].forEach((v) => { feed[`statusFeed/${v}/${sid}`] = { authorId: me.uid, createdAt: serverTimestamp(), expiresAt }; });
  await update(R(), feed);
}
export async function deleteStatus(s: Status) {
  await update(R(), { [`statuses/${s.id}`]: null, [`statusViews/${s.id}`]: null, [`statusFeed/${s.authorId}/${s.id}`]: null });
}
export const markStatusViewed = (sid: string, uid: string) => set(R(`statusViews/${sid}/${uid}`), serverTimestamp()).catch(() => undefined);
export function watchStatusViews(sid: string, cb: (n: number) => void): Unsubscribe {
  return onValue(R(`statusViews/${sid}`), (s) => cb(s.size), () => cb(0));
}

export async function deleteAccount(user: User, profile: Profile) {
  const uid = user.uid;
  await update(R(), { [`contacts/${uid}`]: null, [`cleared/${uid}`]: null, [`userChats/${uid}`]: null, [`callInbox/${uid}`]: null });
  if (profile.phone) await remove(R(`phoneIndex/${profile.phone}`)).catch(() => undefined);
  await remove(R(`users/${uid}`));
  await deleteUser(user);
}
