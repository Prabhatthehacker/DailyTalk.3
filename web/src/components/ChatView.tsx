import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useStore } from '../store';
import * as api from '../lib/api';
import type { Conversation, Message } from '../lib/types';
import { Avatar } from './Logo';
import { ChatMenu, type ChatAction } from './ChatMenu';

const EMOJI_SET = [
  '😀', '😁', '😂', '🤣', '😅', '😊', '😇', '🙂', '😍', '😘',
  '😎', '🤩', '🥳', '😜', '🤔', '😮', '🤯', '😴', '😷', '🤧',
  '👍', '👎', '👏', '🙌', '🫶', '💪', '🔥', '🎉', '✨', '💯',
  '❤️', '🧡', '💛', '💚', '💙', '💜', '🖤', '💖', '💘', '💝',
  '📌', '✅', '❌', '⚠️', '💡', '📱', '💻', '🎧', '🎵', '🎮',
  '🌍', '🌙', '☀️', '⭐', '🌈', '🌼', '🍀', '🍕', '☕', '🍔',
  '🚀', '✈️', '🚗', '🚲', '🏠', '💼', '🎁', '🎂', '🍰', '🧁',
];

const GIF_API_KEY = import.meta.env.VITE_GIPHY_API_KEY as string | undefined;

interface Outbox {
  msg: Message;
  out: api.OutgoingMessage;
  file?: File;
}

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
      id,
      senderId: me,
      senderName: profile!.displayName,
      kind: out.kind,
      text: out.text,
      url: out.url ?? (file ? URL.createObjectURL(file) : undefined),
      createdAt: Date.now(),
      readBy: [me],
      state: 'sending',
    };
    const entry = { msg, out: { ...out, id }, file } as Outbox;
    setOutbox((o) => [...o, entry]);
    void sendEntry(entry);
  };

  const sendEntry = async (entry: Outbox) => {
    try {
      if (entry.file && !entry.out.url) {
        // your existing upload logic
      }
      await api.sendMessage(conv, profile!, entry.out);
      setOutbox((o) => o.filter((x) => x.msg.id !== entry.msg.id));
    } catch (e) {
      toast(e instanceof Error ? e.message : 'Message failed.');
    }
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
        {server.map((m) => (
          <div key={m.id} className={`bubble ${m.senderId === me ? 'mine' : 'theirs'}`}>
            <p>{m.text}</p>
          </div>
        ))}
      </div>

      <div className="composer-wrap">
        {panel === 'emoji' && (
          <div className="panel emoji-panel">
            <div className="panel-header">
              <strong>Emoji</strong>
              <button type="button" className="icon-btn small" onClick={() => setPanel(null)}>✕</button>
            </div>

            <div className="emoji-grid">
              {EMOJI_SET.map((emoji) => (
                <button
                  key={emoji}
                  type="button"
                  className="emoji-btn"
                  onClick={() => {
                    setText((t) => t + emoji);
                    taRef.current?.focus();
                    setPanel(null);
                  }}
                >
                  {emoji}
                </button>
              ))}
            </div>
          </div>
        )}

        {panel === 'gif' && (
          <GifPanel onClose={() => setPanel(null)} onPick={(g) => { enqueue({ kind: 'gif', url: g.url }); setPanel(null); }} />
        )}

        <form className="composer" onSubmit={send}>
          <button type="button" className="icon-btn" aria-label="Emoji" onClick={() => setPanel(panel === 'emoji' ? null : 'emoji')}>😊</button>
          <button type="button" className="icon-btn" aria-label="GIF" onClick={() => setPanel(panel === 'gif' ? null : 'gif')}>GIF</button>
          <button type="button" className="icon-btn" aria-label="Attach file" onClick={() => fileInput.current?.click()}>📎</button>

          <textarea
            ref={taRef}
            rows={1}
            value={text}
            placeholder="Type a message"
            onChange={(e) => setText(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                send(e);
              }
            }}
          />

          <button type="submit" className="btn primary send" disabled={!text.trim()}>Send</button>
        </form>
      </div>
    </section>
  );
}

function GifPanel({ onClose, onPick }: { onClose: () => void; onPick: (g: { id: string; url: string; preview: string }) => void }) {
  const [q, setQ] = useState('hello');
  const [gifs, setGifs] = useState<{ id: string; url: string; preview: string }[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!GIF_API_KEY) return;
    setLoading(true);
    const url = `https://api.giphy.com/v1/gifs/search?api_key=${encodeURIComponent(GIF_API_KEY)}&q=${encodeURIComponent(q)}&limit=9&rating=g`;
    fetch(url)
      .then((res) => res.json())
      .then((data) => {
        const items = Array.isArray(data?.data) ? data.data : [];
        setGifs(items.map((g: any) => ({
          id: String(g.id ?? Math.random().toString(36)),
          preview: g.images?.fixed_height_small?.url ?? g.images?.downsized?.url ?? '',
          url: g.images?.original?.url ?? g.images?.downsized?.url ?? '',
        })));
      })
      .catch(() => setGifs([]))
      .finally(() => setLoading(false));
  }, [q]);

  return (
    <div className="panel gif-panel">
      <div className="panel-header">
        <strong>GIFs</strong>
        <button type="button" className="icon-btn small" onClick={onClose}>✕</button>
      </div>

      <div className="gif-search">
        <input type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search GIFs" />
      </div>

      {loading ? <p className="muted">Loading…</p> : null}
      {!GIF_API_KEY && <p className="panel-note">Add VITE_GIPHY_API_KEY in your site settings.</p>}

      <div className="gif-grid">
        {gifs.map((g) => (
          <button key={g.id} type="button" className="gif-thumb" onClick={() => onPick(g)}>
            <img src={g.preview} alt="GIF preview" />
          </button>
        ))}
      </div>
    </div>
  );
}
