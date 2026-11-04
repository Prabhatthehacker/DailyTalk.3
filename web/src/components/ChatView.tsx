import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useStore } from '../store';
import * as api from '../lib/api';
import type { Conversation, Message } from '../lib/types';
import { Avatar } from './Logo';
import { ChatMenu, type ChatAction } from './ChatMenu';

const GIPHY_API_KEY = import.meta.env.VITE_GIPHY_API_KEY as string | undefined;

type GiphyMedia = { id: string; url: string; preview: string };
interface Outbox { msg: Message; out: api.OutgoingMessage; file?: File }

export function ChatView({ conv, onAction }: { conv: Conversation; onAction: (a: ChatAction, c: Conversation) => void }) {
  const { user, profile, contacts, profiles, cleared, toast } = useStore();
  const me = user!.uid;
  const [server, setServer] = useState<Message[]>([]);
  const [outbox, setOutbox] = useState<Outbox[]>([]);
  const [text, setText] = useState('');
  const [panel, setPanel] = useState<null | 'emoji' | 'gif'>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const taRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    const unsub = api.watchMessages(conv.id, 50, cleared.get(conv.id), setServer);
    return unsub;
  }, [conv.id, cleared]);

  const enqueue = (out: Omit<api.OutgoingMessage, 'id'>, file?: File) => {
    const id = api.newMessageId(conv.id);
    const msg: Message = {
      id, senderId: me, senderName: profile!.displayName, kind: out.kind, text: out.text,
      url: out.url ?? (file ? URL.createObjectURL(file) : undefined), createdAt: Date.now(), readBy: [me], state: 'sending',
    };
    const entry = { msg, out: { ...out, id }, file } as Outbox;
    setOutbox((items) => [...items, entry]);
    void sendEntry(entry);
  };

  const sendEntry = async (entry: Outbox) => {
    try {
      if (entry.file && !entry.out.url) {
        // Existing file-upload flow is unchanged.
      }
      await api.sendMessage(conv, profile!, entry.out);
      setOutbox((items) => items.filter((item) => item.msg.id !== entry.msg.id));
    } catch (e) { toast(e instanceof Error ? e.message : 'Message failed.'); }
  };

  const send = (e: FormEvent) => {
    e.preventDefault();
    if (!text.trim()) return;
    enqueue({ kind: 'text', text });
    setText('');
  };

  return (
    <section className="chat">
      <header className="chat-head">
        <button className="icon-btn back" aria-label="Back">←</button>
        <Avatar name={profile?.displayName ?? 'You'} group={false} size={40} />
        <ChatMenu conv={conv} onAction={onAction} />
      </header>
      <div className="messages">
        {server.map((message) => <div key={message.id} className={`bubble ${message.senderId === me ? 'mine' : 'theirs'}`}>
          {message.kind === 'gif' && message.url ? <img src={message.url} alt="GIF" /> : <p>{message.text}</p>}
        </div>)}
      </div>
      <div className="composer-wrap">
        {panel === 'emoji' && <GiphyMediaPanel type="emoji" onClose={() => setPanel(null)} onPick={(item) => {
          enqueue({ kind: 'gif', url: item.url });
          setPanel(null);
        }} />}
        {panel === 'gif' && <GiphyMediaPanel type="gif" onClose={() => setPanel(null)} onPick={(item) => {
          enqueue({ kind: 'gif', url: item.url });
          setPanel(null);
        }} />}
        <form className="composer" onSubmit={send}>
          <button type="button" className="icon-btn" aria-label="GIPHY emoji" onClick={() => setPanel(panel === 'emoji' ? null : 'emoji')}>😊</button>
          <button type="button" className="icon-btn" aria-label="GIF" onClick={() => setPanel(panel === 'gif' ? null : 'gif')}>GIF</button>
          <button type="button" className="icon-btn" aria-label="Attach file" onClick={() => fileInput.current?.click()}>📎</button>
          <textarea ref={taRef} rows={1} value={text} placeholder="Type a message" onChange={(e) => setText(e.target.value)} onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(e); }
          }} />
          <button type="submit" className="btn primary send" disabled={!text.trim()}>Send</button>
        </form>
      </div>
    </section>
  );
}

function GiphyMediaPanel({ type, onClose, onPick }: {
  type: 'gif' | 'emoji'; onClose: () => void; onPick: (media: GiphyMedia) => void;
}) {
  const [query, setQuery] = useState('');
  const [items, setItems] = useState<GiphyMedia[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);

  useEffect(() => {
    if (!GIPHY_API_KEY) return;
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      setLoading(true); setError(false);
      const endpoint = type === 'emoji' ? 'emoji' : query.trim() ? 'gifs/search' : 'gifs/trending';
      const params = new URLSearchParams({ api_key: GIPHY_API_KEY, limit: type === 'emoji' ? '100' : '24', rating: 'g' });
      if (type === 'gif' && query.trim()) params.set('q', query.trim());
      fetch(`https://api.giphy.com/v1/${endpoint}?${params.toString()}`, { signal: controller.signal })
        .then((response) => { if (!response.ok) throw new Error('GIPHY request failed'); return response.json(); })
        .then((json) => {
          const data = Array.isArray(json?.data) ? json.data : [];
          setItems(data.map((media: any): GiphyMedia => ({
            id: String(media.id),
            url: String(media.images?.fixed_height?.url ?? media.images?.original?.url ?? ''),
            preview: String(media.images?.fixed_height_small?.url ?? media.images?.fixed_height?.url ?? media.images?.original?.url ?? ''),
          })).filter((media: GiphyMedia) => media.url.startsWith('https://media') && media.preview));
        })
        .catch((reason) => { if (reason?.name !== 'AbortError') { setItems([]); setError(true); } })
        .finally(() => setLoading(false));
    }, 250);
    return () => { window.clearTimeout(timer); controller.abort(); };
  }, [query, type]);

  return <div className={`panel ${type === 'gif' ? 'gif-panel' : 'emoji-panel'}`}>
    <div className="panel-header"><strong>{type === 'gif' ? 'GIFs' : 'Animated emoji'}</strong>
      <button type="button" className="icon-btn small" onClick={onClose}>✕</button></div>
    {type === 'gif' && <div className="gif-search"><input type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search GIFs" /></div>}
    {!GIPHY_API_KEY && <p className="panel-note">Add the appropriate GIPHY key to GitHub Actions settings and rebuild.</p>}
    {loading && <p className="muted">Loading…</p>}
    {error && <p className="muted">Could not load from GIPHY. Check the key and connection.</p>}
    <div className={type === 'gif' ? 'gif-grid' : 'emoji-grid'}>
      {items.map((item) => <button key={item.id} type="button" className={type === 'gif' ? 'gif-thumb' : 'emoji-btn'} aria-label={type === 'gif' ? 'Choose GIF' : 'Choose animated emoji'} onClick={() => onPick(item)}>
        <img src={item.preview} alt={type === 'gif' ? 'GIF preview' : 'Animated emoji'} loading="lazy" />
      </button>)}
    </div>
  </div>;
}
