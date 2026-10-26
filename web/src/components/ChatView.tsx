import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useStore } from '../store';
import * as api from '../lib/api';
import { conversationTitle, formatTime, peerNumberOf } from '../lib/labels';
import { formatNumber } from '../lib/number';
import { MAX_UPLOAD_MB, mediaKind, uploadMedia, validateMedia } from '../lib/media';
import { giphyConfigured, searchGifs, type Gif } from '../lib/giphy';
import type { Conversation, Message } from '../lib/types';
import { Avatar } from './Logo';
import { ChatMenu, type ChatAction } from './ChatMenu';
import { Confirm } from './Dialogs';

const EMOJI = '😀 😁 😂 🤣 😊 😍 😘 😎 🤔 😢 😭 😡 👍 👎 👏 🙏 💪 🎉 🔥 ❤️ 💛 🧡 ✨ 🌟 🎂 🍕 ☕ 🌅 📚 💻'.split(' ');
const PAGE = 40;

interface Outbox { msg: Message; out: api.OutgoingMessage; file?: File }

export function ChatView({ conv, onAction }: { conv: Conversation; onAction: (a: ChatAction, c: Conversation) => void }) {
  const { user, profile, contacts, profiles, cleared, toast } = useStore();
  const nav = useNavigate();
  const me = user!.uid;
  const after = cleared.get(conv.id);
  const [pages, setPages] = useState(1);
  const [server, setServer] = useState<Message[]>([]);
  const [outbox, setOutbox] = useState<Outbox[]>([]);
  const [text, setText] = useState('');
  const listRef = useRef<HTMLDivElement>(null);
  const stick = useRef(true);
  const title = conversationTitle(conv, me, contacts, profiles);
  const number = peerNumberOf(conv, me, profiles);

  useEffect(() => { setPages(1); setServer([]); setOutbox([]); stick.current = true; }, [conv.id]);
  useEffect(() => api.watchMessages(conv.id, PAGE * pages, after, setServer), [conv.id, pages, after]);

  const messages = useMemo(() => {
    const ids = new Set(server.map((m) => m.id));
    return [...server, ...outbox.map((o) => o.msg).filter((m) => !ids.has(m.id))];
  }, [server, outbox]);

  useEffect(() => { if (stick.current) listRef.current?.scrollTo({ top: listRef.current.scrollHeight }); }, [messages.length]);

  // Read receipts + clear unread badge while the conversation is on screen.
  useEffect(() => {
    if (document.visibilityState !== 'visible') return;
    void api.markMessagesRead(conv.id, me, server).catch(() => undefined);
    if ((conv.unread[me] ?? 0) > 0) void api.markConversationRead(conv.id, me).catch(() => undefined);
  }, [server, conv.id, conv.unread, me]);

  const patch = useCallback((id: string, p: Partial<Message>) => setOutbox((o) => o.map((x) => (x.msg.id === id ? { ...x, msg: { ...x.msg, ...p } } : x))), []);

  const dispatch = useCallback(async (entry: Outbox) => {
    patch(entry.msg.id, { state: 'sending', progress: 0 });
    try {
      // Photos/videos go to the file host first; the link is kept so a retry never uploads twice.
      if (entry.file && !entry.out.url) entry.out.url = await uploadMedia(entry.file, (p) => patch(entry.msg.id, { progress: p }));
      patch(entry.msg.id, { progress: 1 });
      await api.sendMessage(conv, profile!, entry.out);
      setOutbox((o) => o.filter((x) => x.msg.id !== entry.msg.id));
    } catch (e) {
      patch(entry.msg.id, { state: 'failed' });
      toast(e instanceof Error ? e.message : 'Message not sent.');
    }
  }, [conv, profile, patch, toast]);

  const enqueue = useCallback((out: Omit<api.OutgoingMessage, 'id'>, file?: File) => {
    const id = api.newMessageId(conv.id);
    const msg: Message = {
      id, senderId: me, senderName: profile!.displayName, kind: out.kind, text: out.text, url: out.url ?? (file ? URL.createObjectURL(file) : undefined),
      createdAt: Date.now(), readBy: [me], state: 'sending',
    };
    const entry: Outbox = { msg, out: { ...out, id }, file };
    stick.current = true;
    setOutbox((o) => [...o, entry]);
    void dispatch(entry);
  }, [conv.id, me, profile, dispatch]);

  const [noticeFor, setNoticeFor] = useState<FileList | null>(null);
  function sendFiles(files: FileList | null) {
    Array.from(files ?? []).forEach((f) => {
      const err = validateMedia(f);
      const kind = mediaKind(f.type);
      if (err || !kind) { toast(`${f.name}: ${err}`); return; }
      enqueue({ kind }, f);
    });
  }
  function onPickFiles(files: FileList | null) {
    if (!files?.length) return;
    // One-time reminder that the file host is public.
    if (localStorage.getItem('mediaNoticeAccepted') === '1') sendFiles(files); else setNoticeFor(files);
  }

  const send = (e: FormEvent) => { e.preventDefault(); if (!text.trim()) return; enqueue({ kind: 'text', text }); setText(''); };

  return (
    <section className="chat">
      <header className="chat-head">
        <button className="icon-btn back" onClick={() => nav('/chats')} aria-label="Back to chats">←</button>
        <Avatar name={title} group={conv.type === 'group'} size={40} />
        <button className="chat-title" onClick={() => onAction(conv.type === 'group' ? 'groupInfo' : 'manage', conv)}>
          <strong>{title}</strong>
          <small>{conv.type === 'group' ? `${conv.members.length} members` : number && title !== formatNumber(number) ? formatNumber(number) : ''}</small>
        </button>
        <button className="icon-btn" aria-label="Voice call" disabled={conv.type !== 'direct'} onClick={() => onAction('voice', conv)}>📞</button>
        <button className="icon-btn" aria-label="Video call" disabled={conv.type !== 'direct'} onClick={() => onAction('video', conv)}>🎥</button>
        <ChatMenu conv={conv} onAction={onAction} />
      </header>

      <div className="messages" ref={listRef} onScroll={(e) => { const el = e.currentTarget; stick.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80; }}>
        {server.length >= PAGE * pages && <button className="btn outline small center" onClick={() => setPages(pages + 1)}>Load earlier messages</button>}
        {!messages.length && <p className="empty">No messages yet. Say hello.</p>}
        {messages.map((m, i) => {
          const mine = m.senderId === me;
          const day = new Date(m.createdAt).toDateString();
          const newDay = i === 0 || day !== new Date(messages[i - 1].createdAt).toDateString();
          return (
            <div key={m.id}>
              {newDay && <div className="day">{new Date(m.createdAt).toLocaleDateString([], { weekday: 'long', month: 'short', day: 'numeric' })}</div>}
              <div className={`bubble ${mine ? 'mine' : 'theirs'} ${m.state}`}>
                {!mine && conv.type === 'group' && <span className="sender">{m.senderName}</span>}
                <Body m={m} />
                <span className="meta">
                  <time>{formatTime(m.createdAt)}</time>
                  {mine && <Tick m={m} />}
                </span>
                {m.state === 'sending' && m.progress !== undefined && m.progress > 0 && m.progress < 1 && <progress value={m.progress} max={1} aria-label="Uploading" />}
                {m.state === 'failed' && (
                  <button className="link retry" onClick={() => { const o = outbox.find((x) => x.msg.id === m.id); if (o) void dispatch(o); }}>Not sent. Retry</button>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {noticeFor && (
        <Confirm title="Photos and videos are public links" action="Upload anyway" danger={false}
          body={`Photos and videos are uploaded to a free public file host (catbox.moe), not to Daily Talk. Anyone who gets the link can open the file, and it cannot be deleted from here. Files up to ${MAX_UPLOAD_MB} MB are allowed. Only send media you are happy to have on a public site.`}
          onConfirm={() => { localStorage.setItem('mediaNoticeAccepted', '1'); sendFiles(noticeFor); }} onClose={() => setNoticeFor(null)} />
      )}
      <Composer text={text} setText={setText} onSubmit={send} onFiles={onPickFiles} onGif={(g) => enqueue({ kind: 'gif', url: g.url })} />
    </section>
  );
}

function Tick({ m }: { m: Message }) {
  if (m.state === 'sending') return <span className="tick" aria-label="Sending">…</span>;
  if (m.state === 'failed') return <span className="tick bad" aria-label="Failed">!</span>;
  const read = m.readBy.some((u) => u !== m.senderId);
  return <span className={`tick ${read ? 'read' : ''}`} aria-label={read ? 'Read' : 'Sent'}>{read ? '✓✓' : '✓'}</span>;
}

function Body({ m }: { m: Message }) {
  if (m.kind === 'gif' && m.url) return <img className="media" src={m.url} alt="GIF" loading="lazy" />;
  if (m.kind === 'image' && m.url) return <a href={m.url} target="_blank" rel="noreferrer noopener"><img className="media" src={m.url} alt="Photo" loading="lazy" referrerPolicy="no-referrer" /></a>;
  if (m.kind === 'video' && m.url) return <video className="media" src={m.url} controls preload="metadata" playsInline />;
  return <p className="text">{m.text}</p>;
}

function Composer({ text, setText, onSubmit, onFiles, onGif }: {
  text: string; setText: (t: string) => void; onSubmit: (e: FormEvent) => void; onFiles: (f: FileList | null) => void; onGif: (g: Gif) => void;
}) {
  const [panel, setPanel] = useState<null | 'emoji' | 'gif'>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const taRef = useRef<HTMLTextAreaElement>(null);
  return (
    <div className="composer-wrap">
      {panel === 'emoji' && <div className="panel emoji">{EMOJI.map((e) => <button key={e} type="button" onClick={() => { setText(text + e); taRef.current?.focus(); }}>{e}</button>)}</div>}
      {panel === 'gif' && <GifPanel onPick={(g) => { onGif(g); setPanel(null); }} />}
      <form className="composer" onSubmit={onSubmit}>
        <button type="button" className="icon-btn" aria-label="Emoji" aria-pressed={panel === 'emoji'} onClick={() => setPanel(panel === 'emoji' ? null : 'emoji')}>😊</button>
        <button type="button" className="icon-btn gif-btn" aria-label="GIF" aria-pressed={panel === 'gif'} onClick={() => setPanel(panel === 'gif' ? null : 'gif')}>GIF</button>
        <button type="button" className="icon-btn" aria-label="Send a photo or video" onClick={() => fileInput.current?.click()}>📎</button>
        <input ref={fileInput} type="file" hidden multiple accept="image/jpeg,image/png,image/webp,image/gif,video/mp4,video/webm,video/quicktime"
          onChange={(e) => { onFiles(e.target.files); e.target.value = ''; }} />
        <textarea ref={taRef} rows={1} value={text} maxLength={4000} placeholder="Type a message" aria-label="Message"
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) { e.preventDefault(); onSubmit(e); } }} />
        <button className="btn primary send" disabled={!text.trim()} aria-label="Send">Send</button>
      </form>
    </div>
  );
}

function GifPanel({ onPick }: { onPick: (g: Gif) => void }) {
  const [q, setQ] = useState(''); const [gifs, setGifs] = useState<Gif[]>([]); const [state, setState] = useState<'idle' | 'loading' | 'error'>('idle');
  useEffect(() => {
    if (!giphyConfigured) return;
    setState('loading');
    const t = setTimeout(() => searchGifs(q).then((g) => { setGifs(g); setState('idle'); }).catch(() => setState('error')), 350);
    return () => clearTimeout(t);
  }, [q]);
  if (!giphyConfigured) return <div className="panel"><p className="muted">GIF search needs a Giphy key. Add VITE_GIPHY_API_KEY to the site settings.</p></div>;
  return (
    <div className="panel gifs">
      <input type="search" placeholder="Search GIFs" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search GIFs" />
      {state === 'error' && <p className="error">Could not load GIFs.</p>}
      <div className="gif-grid">{gifs.map((g) => <button key={g.id} type="button" onClick={() => onPick(g)}><img src={g.preview} alt="" loading="lazy" /></button>)}</div>
    </div>
  );
}
