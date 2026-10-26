import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { useStore } from '../store';
import * as api from '../lib/api';
import { formatNumber } from '../lib/number';
import { peerNumberOf } from '../lib/labels';
import type { Conversation } from '../lib/types';
import { Avatar } from './Logo';

export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const prev = document.activeElement as HTMLElement | null;
    ref.current?.querySelector<HTMLElement>('input,select,textarea,button')?.focus();
    const esc = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    document.addEventListener('keydown', esc);
    return () => { document.removeEventListener('keydown', esc); prev?.focus(); };
  }, [onClose]);
  return (
    <div className="scrim" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" role="dialog" aria-modal="true" aria-label={title} ref={ref}>
        <header><h2>{title}</h2><button className="icon-btn" onClick={onClose} aria-label="Close">✕</button></header>
        {children}
      </div>
    </div>
  );
}

export function Confirm({ title, body, action, danger, onConfirm, onClose }: {
  title: string; body: string; action: string; danger?: boolean; onConfirm: () => Promise<void> | void; onClose: () => void;
}) {
  const [busy, setBusy] = useState(false); const { toast } = useStore();
  return (
    <Modal title={title} onClose={onClose}>
      <p>{body}</p>
      <div className="row end">
        <button className="btn outline" onClick={onClose}>Cancel</button>
        <button className={`btn ${danger ? 'danger' : 'primary'}`} disabled={busy} onClick={async () => {
          setBusy(true);
          try { await onConfirm(); onClose(); } catch (e) { toast(e instanceof Error ? e.message : 'That did not work. Try again.'); setBusy(false); }
        }}>{action}</button>
      </div>
    </Modal>
  );
}

export function StartChatDialog({ onClose, onOpen }: { onClose: () => void; onOpen: (cid: string) => void }) {
  const { profile } = useStore();
  const [number, setNumber] = useState(''); const [error, setError] = useState(''); const [busy, setBusy] = useState(false);
  async function submit(e: FormEvent) {
    e.preventDefault(); if (!profile) return; setBusy(true); setError('');
    try { const cid = await api.startChatWithNumber(profile, number); onOpen(cid); onClose(); }
    catch (err) { setError(err instanceof Error ? err.message : 'Could not start the chat.'); setBusy(false); }
  }
  return (
    <Modal title="Add a number" onClose={onClose}>
      <form onSubmit={submit} className="stack">
        <label>Daily Talk number<input inputMode="numeric" autoComplete="off" value={number} onChange={(e) => setNumber(e.target.value)} placeholder="12345 67890" /></label>
        <p className="hint">Enter the 10-digit number the other person sees in their Settings. It shows as the contact's name until you save a name for it.</p>
        {error && <p className="error" role="alert">{error}</p>}
        <div className="row end"><button type="button" className="btn outline" onClick={onClose}>Cancel</button><button className="btn primary" disabled={busy || !number.trim()}>Start chat</button></div>
      </form>
    </Modal>
  );
}

export function NewGroupDialog({ onClose, onOpen }: { onClose: () => void; onOpen: (cid: string) => void }) {
  const { profile, toast } = useStore();
  const [name, setName] = useState(''); const [numbers, setNumbers] = useState(''); const [error, setError] = useState(''); const [busy, setBusy] = useState(false);
  async function submit(e: FormEvent) {
    e.preventDefault(); if (!profile) return; setBusy(true); setError('');
    try {
      const { id, missing } = await api.createGroup(profile, name, numbers.split(/[\n,;]+/));
      if (missing.length) toast(`Not added (no account with this number): ${missing.join(', ')}`);
      onOpen(id); onClose();
    } catch (err) { setError(err instanceof Error ? err.message : 'Could not create the group.'); setBusy(false); }
  }
  return (
    <Modal title="Add a group" onClose={onClose}>
      <form onSubmit={submit} className="stack">
        <label>Group name<input value={name} maxLength={60} onChange={(e) => setName(e.target.value)} /></label>
        <label>Participants' numbers<textarea rows={4} value={numbers} onChange={(e) => setNumbers(e.target.value)} placeholder="One number per line" /></label>
        {error && <p className="error" role="alert">{error}</p>}
        <div className="row end"><button type="button" className="btn outline" onClick={onClose}>Cancel</button><button className="btn primary" disabled={busy || !name.trim()}>Create group</button></div>
      </form>
    </Modal>
  );
}

/** Rename a saved contact, or save the number under a name ("Manage saved number"). */
export function ContactDialog({ conv, onClose }: { conv: Conversation; onClose: () => void }) {
  const { user, profiles, contacts, toast } = useStore();
  const number = peerNumberOf(conv, user!.uid, profiles);
  const saved = number ? contacts.get(number) : undefined;
  const [name, setName] = useState(saved?.name ?? '');
  if (!number) return <Modal title="Contact" onClose={onClose}><p>This person's number is not available, so there is nothing to save.</p></Modal>;
  return (
    <Modal title={saved ? 'Rename contact' : 'Save number'} onClose={onClose}>
      <form className="stack" onSubmit={async (e) => {
        e.preventDefault();
        try { await api.saveContact(user!.uid, number, name); onClose(); } catch (err) { toast(err instanceof Error ? err.message : 'Could not save.'); }
      }}>
        <p className="muted">{formatNumber(number)}</p>
        <label>Name<input value={name} maxLength={60} onChange={(e) => setName(e.target.value)} /></label>
        <p className="hint">Leave the number as the name by deleting the saved contact from the chat menu.</p>
        <div className="row end"><button type="button" className="btn outline" onClick={onClose}>Cancel</button><button className="btn primary" disabled={!name.trim()}>Save</button></div>
      </form>
    </Modal>
  );
}

export function GroupInfoDialog({ conv, onClose }: { conv: Conversation; onClose: () => void }) {
  const { user, profiles, toast } = useStore();
  const me = user!.uid; const admin = conv.admins?.includes(me) ?? false;
  const [name, setName] = useState(conv.name ?? ''); const [numbers, setNumbers] = useState(''); const [busy, setBusy] = useState(false);
  const [leave, setLeave] = useState(false);
  const run = async (fn: () => Promise<void>) => { setBusy(true); try { await fn(); } catch (e) { toast(e instanceof Error ? e.message : 'That did not work.'); } finally { setBusy(false); } };
  return (
    <Modal title="Group details" onClose={onClose}>
      <div className="stack">
        {admin ? (
          <form className="row" onSubmit={(e) => { e.preventDefault(); void run(() => api.renameGroup(conv, name)); }}>
            <input value={name} maxLength={60} onChange={(e) => setName(e.target.value)} aria-label="Group name" />
            <button className="btn outline" disabled={busy || !name.trim() || name === conv.name}>Rename</button>
          </form>
        ) : <h3>{conv.name}</h3>}
        <p className="muted">{conv.members.length} members</p>
        <ul className="members">
          {conv.members.map((m) => {
            const p = profiles.get(m); const label = m === me ? 'You' : p?.displayName ?? 'Member';
            return <li key={m}><Avatar name={label} size={36} /><span>{label}{p?.phone && m !== me ? <small> {formatNumber(p.phone)}</small> : null}</span>{conv.admins?.includes(m) && <em>Admin</em>}</li>;
          })}
        </ul>
        {admin && (
          <form className="stack" onSubmit={(e) => { e.preventDefault(); void run(async () => {
            const r = await api.addMembers(conv, numbers.split(/[\n,;]+/), me);
            setNumbers(''); toast(`${r.added} added${r.missing.length ? `. No account with this number: ${r.missing.join(', ')}` : ''}`);
          }); }}>
            <label>Add participants<textarea rows={2} value={numbers} onChange={(e) => setNumbers(e.target.value)} placeholder="One number per line" /></label>
            <button className="btn outline" disabled={busy || !numbers.trim()}>Add member</button>
          </form>
        )}
        <button className="btn danger-outline" onClick={() => setLeave(true)}>Leave group</button>
      </div>
      {leave && <Confirm title="Leave this group?" body="You will stop receiving messages from it. You cannot rejoin unless an admin adds you again." action="Leave group" danger
        onConfirm={() => api.leaveGroup(conv, me)} onClose={() => { setLeave(false); onClose(); }} />}
    </Modal>
  );
}

export function SettingsDialog({ onClose }: { onClose: () => void }) {
  const { user, profile, toast } = useStore();
  const [name, setName] = useState(profile?.displayName ?? '');
  const [busy, setBusy] = useState(false); const [del, setDel] = useState(false);
  if (!user || !profile) return null;
  const copy = () => { void navigator.clipboard?.writeText(profile.phone ?? '').then(() => toast('Number copied'), () => toast('Could not copy. Select the number and copy it by hand.')); };
  return (
    <Modal title="Settings" onClose={onClose}>
      <div className="stack">
        <div className="number-card">
          <small>Your Daily Talk number</small>
          <strong className="big-number" aria-label={profile.phone ?? ''}>{profile.phone ? formatNumber(profile.phone) : 'Creating…'}</strong>
          <p className="hint">This is your number from now on. It was given to you when you signed up and cannot be changed. Write it down, and share it so people can chat with you.</p>
          <button type="button" className="btn outline small" onClick={copy} disabled={!profile.phone}>Copy number</button>
        </div>
        <form className="stack" onSubmit={async (e) => {
          e.preventDefault(); setBusy(true);
          try { await api.updateDisplayName(user.uid, name); toast('Saved'); onClose(); }
          catch (err) { toast(err instanceof Error ? err.message : 'Could not save.'); } finally { setBusy(false); }
        }}>
          <p className="muted">{user.email}</p>
          <label>Your name<input value={name} maxLength={60} onChange={(e) => setName(e.target.value)} /></label>
          <p className="hint">Privacy: your name and number are visible to anyone signed in who has your number, and to people you chat with.</p>
          <div className="row end"><button className="btn primary" disabled={busy}>Save name</button></div>
        </form>
        <hr />
        <div className="row">
          <button type="button" className="btn outline" onClick={() => void api.logout()}>Log out</button>
          <button type="button" className="btn danger-outline" onClick={() => setDel(true)}>Delete account</button>
        </div>
      </div>
      {del && <Confirm title="Delete your account?" body="This removes your profile, your number, saved contacts and sign-in. Messages you already sent stay in other people's chats. Your number stops working and cannot be recovered. This cannot be undone." action="Delete account" danger
        onConfirm={() => api.deleteAccount(user, profile)} onClose={() => setDel(false)} />}
    </Modal>
  );
}
