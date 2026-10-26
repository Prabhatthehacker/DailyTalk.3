import { useEffect, useMemo, useState } from 'react';
import { useStore } from '../store';
import * as api from '../lib/api';
import { formatTime } from '../lib/labels';
import type { Status } from '../lib/types';
import { Avatar } from './Logo';
import { Confirm, Modal } from './Dialogs';

const JOKES = [
  'I told my computer I needed a break. Now it will not stop sending me vacation ads.',
  'Why do programmers prefer dark mode? Because light attracts bugs.',
  'There are 10 kinds of people: those who understand binary and those who do not.',
];

export function StatusView({ onBack }: { onBack: () => void }) {
  const { user, statuses } = useStore();
  const me = user!.uid;
  const [composer, setComposer] = useState(false);
  const [viewing, setViewing] = useState<Status | null>(null);
  const [del, setDel] = useState<Status | null>(null);
  const [, tick] = useState(0);
  useEffect(() => { const t = setInterval(() => tick((n) => n + 1), 60_000); return () => clearInterval(t); }, []);

  const live = statuses.filter((s) => s.expiresAt > Date.now());
  const mine = live.filter((s) => s.authorId === me);
  const others = useMemo(() => {
    const m = new Map<string, Status[]>();
    live.filter((s) => s.authorId !== me).forEach((s) => m.set(s.authorId, [...(m.get(s.authorId) ?? []), s]));
    return [...m.values()];
  }, [live, me]);

  return (
    <section className="status-pane">
      <header className="chat-head">
        <button className="icon-btn back" onClick={onBack} aria-label="Back">←</button>
        <strong className="grow">Status</strong>
        <button className="btn primary small" onClick={() => setComposer(true)}>New status</button>
      </header>
      <div className="status-body">
        <h3>My status</h3>
        {mine.length === 0 && <p className="muted">You have not shared a status in the last 24 hours.</p>}
        <ul className="status-list">{mine.map((s) => <StatusRow key={s.id} s={s} mine onOpen={() => setViewing(s)} onDelete={() => setDel(s)} />)}</ul>
        <h3>Recent updates</h3>
        {others.length === 0 && <p className="muted">When people you chat with post a status, it appears here for 24 hours.</p>}
        <ul className="status-list">{others.map((g) => <StatusRow key={g[0].authorId} s={g[0]} count={g.length} onOpen={() => setViewing(g[0])} />)}</ul>
      </div>
      {composer && <StatusComposer onClose={() => setComposer(false)} />}
      {viewing && <StatusViewer list={live.filter((s) => s.authorId === viewing.authorId).sort((a, b) => a.createdAt - b.createdAt)} start={viewing.id} onClose={() => setViewing(null)} />}
      {del && <Confirm title="Delete this status?" body="It will disappear for everyone right away." action="Delete status" danger
        onConfirm={() => api.deleteStatus(del)} onClose={() => setDel(null)} />}
    </section>
  );
}

function StatusRow({ s, mine, count, onOpen, onDelete }: { s: Status; mine?: boolean; count?: number; onOpen: () => void; onDelete?: () => void }) {
  const [views, setViews] = useState(0);
  useEffect(() => (mine ? api.watchStatusViews(s.id, setViews) : undefined), [s.id, mine]);
  const left = Math.max(0, Math.round((s.expiresAt - Date.now()) / 3600_000));
  return (
    <li className="status-row">
      <button className="status-open" onClick={onOpen}>
        <span className="ring"><Avatar name={s.authorName} size={46} /></span>
        <span className="chat-main">
          <strong>{mine ? 'You' : s.authorName}{count && count > 1 ? ` (${count})` : ''}</strong>
          <small>{s.text} · {formatTime(s.createdAt)}{mine ? ` · ${views} viewed · ${left}h left` : ''}</small>
        </span>
      </button>
      {mine && <button className="icon-btn" onClick={onDelete} aria-label="Delete status">🗑</button>}
    </li>
  );
}

function StatusComposer({ onClose }: { onClose: () => void }) {
  const { profile, conversations, toast } = useStore();
  const [text, setText] = useState(''); const [busy, setBusy] = useState(false);
  const audience = useMemo(() => [...new Set(conversations.filter((c) => c.type === 'direct').flatMap((c) => c.members))], [conversations]);
  return (
    <Modal title="New status" onClose={onClose}>
      <form className="stack" onSubmit={async (e) => {
        e.preventDefault(); setBusy(true);
        try { await api.publishStatus(profile!, audience, text); onClose(); }
        catch (err) { toast(err instanceof Error ? err.message : 'Could not post your status.'); setBusy(false); }
      }}>
        <textarea rows={4} maxLength={700} value={text} onChange={(e) => setText(e.target.value)} placeholder="What's on your mind?" aria-label="Status text" />
        <div className="row">
          <button type="button" className="btn outline small" onClick={() => setText(JOKES[Math.floor(Math.random() * JOKES.length)])}>Add a joke</button>
        </div>
        <p className="hint">Visible for 24 hours to the {audience.length > 1 ? audience.length - 1 : 'people'} you chat with.</p>
        <div className="row end"><button type="button" className="btn outline" onClick={onClose}>Cancel</button><button className="btn primary" disabled={busy || !text.trim()}>{busy ? 'Posting…' : 'Post status'}</button></div>
      </form>
    </Modal>
  );
}

function StatusViewer({ list, start, onClose }: { list: Status[]; start: string; onClose: () => void }) {
  const { user } = useStore();
  const [i, setI] = useState(Math.max(0, list.findIndex((s) => s.id === start)));
  const s = list[i];
  useEffect(() => { if (s && s.authorId !== user!.uid) void api.markStatusViewed(s.id, user!.uid); }, [s, user]);
  if (!s) return null;
  return (
    <Modal title={`${s.authorName} · ${formatTime(s.createdAt)}`} onClose={onClose}>
      <div className="status-card"><p className="status-text">{s.text}</p></div>
      <div className="row end">
        <button className="btn outline" disabled={i === 0} onClick={() => setI(i - 1)}>Previous</button>
        <button className="btn outline" disabled={i >= list.length - 1} onClick={() => setI(i + 1)}>Next</button>
      </div>
    </Modal>
  );
}
