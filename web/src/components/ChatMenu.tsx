import { useEffect, useRef, useState } from 'react';
import { useStore } from '../store';
import { peerNumberOf } from '../lib/labels';
import type { Conversation } from '../lib/types';

export type ChatAction = 'voice' | 'video' | 'rename' | 'manage' | 'deleteContact' | 'deleteConversation' | 'groupInfo' | 'leave';

export function usePopover() {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const away = (e: MouseEvent) => { if (!ref.current?.contains(e.target as Node)) setOpen(false); };
    const esc = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false);
    document.addEventListener('mousedown', away); document.addEventListener('keydown', esc);
    return () => { document.removeEventListener('mousedown', away); document.removeEventListener('keydown', esc); };
  }, [open]);
  return { open, setOpen, ref };
}

export function ChatMenu({ conv, onAction }: { conv: Conversation; onAction: (a: ChatAction, c: Conversation) => void }) {
  const { user, profiles, contacts } = useStore();
  const { open, setOpen, ref } = usePopover();
  const number = peerNumberOf(conv, user!.uid, profiles);
  const saved = number ? contacts.has(number) : false;
  const canCall = conv.type === 'direct' && conv.members.length === 2;
  const item = (a: ChatAction, label: string, enabled = true, cls = '') => (
    <button role="menuitem" className={cls} disabled={!enabled} onClick={() => { setOpen(false); onAction(a, conv); }}>{label}</button>
  );
  return (
    <div className="menu-wrap" ref={ref}>
      <button className="icon-btn" aria-label="Chat menu" aria-haspopup="menu" aria-expanded={open} onClick={(e) => { e.stopPropagation(); setOpen(!open); }}>⋮</button>
      {open && (
        <div className="menu" role="menu">
          {item('voice', 'Voice call', canCall)}
          {item('video', 'Video call', canCall)}
          {conv.type === 'direct' ? <>
            {item('rename', 'Rename contact', saved)}
            {item('manage', saved ? 'Manage saved number' : 'Save number', !!number)}
            {item('deleteContact', 'Delete saved number', saved)}
          </> : <>
            {item('groupInfo', 'Group details')}
            {item('leave', 'Leave group', true, 'danger-text')}
          </>}
          {item('deleteConversation', 'Delete conversation', true, 'danger-text')}
        </div>
      )}
    </div>
  );
}
