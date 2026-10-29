import { useMemo, useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useStore } from '../store';
import { conversationTitle, formatTime } from '../lib/labels';
import type { Conversation } from '../lib/types';
import { Avatar, Logo } from './Logo';
import { ChatMenu, usePopover, type ChatAction } from './ChatMenu';

export function Sidebar({ tab, onAction, onAddChat, onAddGroup, onSettings, onLogout }: {
  tab: 'chats' | 'status'; onAction: (a: ChatAction, c: Conversation) => void;
  onAddChat: () => void; onAddGroup: () => void; onSettings: () => void; onLogout: () => void;
}) {
  const { user, profile, conversations, contacts, profiles } = useStore();
  const [q, setQ] = useState(''); const nav = useNavigate(); const add = usePopover();
  const me = user!.uid;
  const avatarUrl = profile?.photoURL ?? user?.photoURL ?? undefined;
  const rows = useMemo(() => conversations
    .map((c) => ({ c, title: conversationTitle(c, me, contacts, profiles) }))
    .filter((r) => r.title.toLowerCase().includes(q.trim().toLowerCase())), [conversations, contacts, profiles, q, me]);

  return (
    <aside className="sidebar" style={{ display: 'flex', flexDirection: 'column', minHeight: 0 }}>
      <header className="side-head" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10, flexWrap: 'wrap' }}>
        <div className="brand" style={{ display: 'flex', alignItems: 'center', gap: 8 }}><Logo size={32} /><strong>Daily Talk</strong></div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginLeft: 'auto', flexWrap: 'wrap' }}>
          <button
            className="me-chip"
            onClick={onSettings}
            aria-label="Account settings"
            style={{
              display: 'flex', alignItems: 'center', gap: 8, background: 'var(--sand)', border: '1px solid var(--line)', borderRadius: 999,
              padding: '5px 10px 5px 6px', maxWidth: 180, minHeight: 42, width: 'fit-content', flex: '1 1 180px', justifyContent: 'flex-start'
            }}
          >
            <Avatar name={profile?.displayName ?? '?'} size={30} photoUrl={avatarUrl} />
            <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontSize: '0.9rem' }}>{profile?.displayName}</span>
          </button>
          <button className="btn outline small logout-btn" onClick={onLogout} style={{ whiteSpace: 'nowrap' }}>Log out</button>
        </div>
      </header>
      <div className="tabs-row" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8, padding: '6px 16px 10px' }}>
        <nav className="tabs" aria-label="Sections" style={{ display: 'flex', gap: 4, background: 'var(--sand)', padding: 4, borderRadius: 999, flex: 1 }}>
          <NavLink to="/chats" className={tab === 'chats' ? 'tab on' : 'tab'}>Chats</NavLink>
          <NavLink to="/status" className={tab === 'status' ? 'tab on' : 'tab'}>Status</NavLink>
        </nav>
        <div className="menu-wrap" ref={add.ref}>
          <button className="btn primary small" aria-haspopup="menu" aria-expanded={add.open} onClick={() => add.setOpen(!add.open)}>+ Add</button>
          {add.open && (
            <div className="menu" role="menu">
              <button role="menuitem" onClick={() => { add.setOpen(false); onAddChat(); }}>Add a chat</button>
              <button role="menuitem" onClick={() => { add.setOpen(false); onAddGroup(); }}>Add a group</button>
            </div>
          )}
        </div>
      </div>
      {tab === 'chats' && (
        <>
          <input className="search" type="search" placeholder="Search chats" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search chats" style={{ margin: '0 16px 8px', width: 'auto', borderRadius: 999, background: 'var(--cream)' }} />
          <ul className="chat-list" style={{ listStyle: 'none', margin: 0, padding: '4px 8px 12px', overflowY: 'auto', flex: 1 }}>
            {rows.map(({ c, title }) => {
              const unread = c.unread[me] ?? 0;
              return (
                <li key={c.id} style={{ display: 'flex', alignItems: 'center', borderRadius: 14, position: 'relative' }}>
                  <button className="chat-row" onClick={() => nav(`/chats/${c.id}`)} style={{ flex: 1, minWidth: 0, display: 'flex', gap: 12, alignItems: 'center', background: 'none', border: 0, padding: '10px 6px 10px 8px', textAlign: 'left' }}>
                    <Avatar name={title} group={c.type === 'group'} size={42} photoUrl={undefined} />
                    <span className="chat-main" style={{ flex: 1, minWidth: 0, display: 'grid', gap: 2 }}>
                      <span className="chat-top" style={{ display: 'flex', justifyContent: 'space-between', gap: 8, alignItems: 'center' }}><strong>{title}</strong><time>{formatTime(c.updatedAt)}</time></span>
                      <span className="chat-bottom" style={{ display: 'flex', justifyContent: 'space-between', gap: 8, alignItems: 'center' }}>
                        <span className="preview" style={{ color: 'var(--muted)', fontSize: '0.88rem', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{c.lastMessage ? `${c.lastMessage.senderId === me ? 'You: ' : ''}${c.lastMessage.text}` : 'No messages yet'}</span>
                        {unread > 0 && <span className="badge" aria-label={`${unread} unread`} style={{ background: 'var(--brown)', color: '#fff', borderRadius: 999, minWidth: 22, height: 22, padding: '0 6px', fontSize: '0.75rem', fontWeight: 700, display: 'grid', placeItems: 'center' }}>{unread > 99 ? '99+' : unread}</span>}
                      </span>
                    </span>
                  </button>
                  <ChatMenu conv={c} onAction={onAction} />
                </li>
              );
            })}
            {!rows.length && (
              <li className="empty-list" style={{ padding: '24px 16px', color: 'var(--muted)', textAlign: 'center' }}>{q ? 'No chats match your search.' : 'No chats yet. Use Add to start one with a phone number.'}</li>
            )}
          </ul>
        </>
      )}
    </aside>
  );
}
