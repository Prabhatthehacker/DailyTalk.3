import { useCallback, useEffect, useMemo, useState } from 'react';
import { Navigate, Route, Routes, useNavigate, useParams } from 'react-router-dom';
import { useStore } from './store';
import * as api from './lib/api';
import { CallSession, watchIncomingCalls, type CallState } from './lib/calls';
import { conversationTitle, peerNumberOf } from './lib/labels';
import type { CallDoc, Conversation } from './lib/types';
import { AuthScreen, SetupNeeded } from './components/Auth';
import { Sidebar } from './components/ChatList';
import { ChatView } from './components/ChatView';
import { StatusView } from './components/StatusView';
import { CallOverlay, type ActiveCall } from './components/CallUI';
import { Confirm, ContactDialog, GroupInfoDialog, NewGroupDialog, SettingsDialog, StartChatDialog } from './components/Dialogs';
import type { ChatAction } from './components/ChatMenu';
import { Logo } from './components/Logo';
import { firebaseConfigured } from './lib/firebase';

type Dialog =
  | { k: 'start' } | { k: 'group' } | { k: 'settings' } | { k: 'logout' }
  | { k: 'contact' | 'info' | 'deleteContact' | 'deleteConv' | 'leave'; c: Conversation };

export default function App() {
  const { ready, user, profile, toastMsg } = useStore();
  if (!firebaseConfigured) return <SetupNeeded />;
  if (!ready) return <div className="splash"><Logo size={72} /><p>Loading…</p></div>;
  if (!user) return <AuthScreen />;
  if (!profile) return <div className="splash"><Logo size={72} /><p>Loading your account…</p></div>;
  return (
    <>
      <Shell />
      {toastMsg && <div className="toast" role="status">{toastMsg}</div>}
    </>
  );
}

function Shell() {
  return (
    <Routes>
      <Route path="/chats" element={<Layout tab="chats" />} />
      <Route path="/chats/:cid" element={<Layout tab="chats" />} />
      <Route path="/status" element={<Layout tab="status" />} />
      <Route path="*" element={<Navigate to="/chats" replace />} />
    </Routes>
  );
}

function Layout({ tab }: { tab: 'chats' | 'status' }) {
  const { user, conversations, allConversations, contacts, profiles, toast } = useStore();
  const { cid } = useParams();
  const nav = useNavigate();
  const me = user!.uid;
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const [call, setCall] = useState<ActiveCall | null>(null);
  const [tick, setTick] = useState(0);
  const [incoming, setIncoming] = useState<CallDoc | null>(null);

  const conv = useMemo(() => (cid ? allConversations.find((c) => c.id === cid) : undefined), [cid, allConversations]);
  const title = (c: Conversation) => conversationTitle(c, me, contacts, profiles);

  // Incoming calls (while this page is open).
  useEffect(() => watchIncomingCalls(me, (c) => setIncoming((cur) => cur ?? c)), [me]);
  useEffect(() => {
    if (!incoming) return;
    const t = setTimeout(() => setIncoming(null), 45_000);
    return () => clearTimeout(t);
  }, [incoming]);

  const onState = useCallback((state: CallState) => setCall((c) => (c ? { ...c, state } : c)), []);
  const onStreams = useCallback(() => setTick((n) => n + 1), []);

  async function startCall(c: Conversation, video: boolean) {
    const peer = c.members.find((m) => m !== me);
    if (!peer || call) return;
    setCall({ name: title(c), video, state: 'calling' });
    try {
      const session = await CallSession.start(me, peer, c.id, video, onState, onStreams);
      setCall((cur) => (cur ? { ...cur, session } : cur));
    } catch (e) { setCall((cur) => (cur ? { ...cur, error: mediaError(e) } : cur)); }
  }
  async function acceptCall() {
    const c = incoming; if (!c) return;
    setIncoming(null);
    const other = allConversations.find((x) => x.id === c.conversationId);
    setCall({ name: other ? title(other) : 'Incoming call', video: c.type === 'video', incoming: true, state: 'connecting' });
    try {
      const session = await CallSession.answer(me, c, onState, onStreams);
      setCall((cur) => (cur ? { ...cur, session } : cur));
    } catch (e) { setCall((cur) => (cur ? { ...cur, error: mediaError(e) } : cur)); }
  }

  function onAction(a: ChatAction, c: Conversation) {
    if (a === 'voice' || a === 'video') void startCall(c, a === 'video');
    else if (a === 'rename' || a === 'manage') setDialog({ k: 'contact', c });
    else if (a === 'groupInfo') setDialog({ k: 'info', c });
    else if (a === 'deleteContact') setDialog({ k: 'deleteContact', c });
    else if (a === 'deleteConversation') setDialog({ k: 'deleteConv', c });
    else if (a === 'leave') setDialog({ k: 'leave', c });
  }

  const incomingConv = incoming ? allConversations.find((x) => x.id === incoming.conversationId) : undefined;
  const hasChat = tab === 'chats' && !!cid;

  return (
    <div className={`shell ${hasChat ? 'show-main' : ''} ${tab === 'status' ? 'status-mode' : ''}`}>
      <Sidebar tab={tab} onAction={onAction} onAddChat={() => setDialog({ k: 'start' })} onAddGroup={() => setDialog({ k: 'group' })} onSettings={() => setDialog({ k: 'settings' })} onLogout={() => setDialog({ k: 'logout' })} />
      <main className="main">
        {tab === 'status' ? <StatusView onBack={() => nav('/chats')} />
          : conv ? <ChatView conv={conv} onAction={onAction} />
          : cid ? <div className="welcome"><p className="muted">This conversation is not available.</p></div>
          : <Welcome onNumber={() => setDialog({ k: 'start' })} onChat={() => setDialog({ k: 'start' })} hasChats={conversations.length > 0} />}
      </main>

      {dialog?.k === 'start' && <StartChatDialog onClose={() => setDialog(null)} onOpen={(id) => nav(`/chats/${id}`)} />}
      {dialog?.k === 'group' && <NewGroupDialog onClose={() => setDialog(null)} onOpen={(id) => nav(`/chats/${id}`)} />}
      {dialog?.k === 'settings' && <SettingsDialog onClose={() => setDialog(null)} />}
      {dialog?.k === 'logout' && (
        <Confirm title="Log out of Daily Talk?" body="You will need to sign in again to read your chats on this device. Your chats and your number stay safe in your account." action="Log out" danger={false}
          onConfirm={async () => { await api.logout(); nav('/chats'); }} onClose={() => setDialog(null)} />
      )}
      {dialog?.k === 'contact' && <ContactDialog conv={dialog.c} onClose={() => setDialog(null)} />}
      {dialog?.k === 'info' && <GroupInfoDialog conv={dialog.c} onClose={() => setDialog(null)} />}
      {dialog?.k === 'deleteContact' && (
        <Confirm title="Delete saved number?" body={`"${title(dialog.c)}" will go back to showing just the number. The conversation and its messages are kept.`} action="Delete saved number" danger
          onConfirm={async () => { const n = peerNumberOf(dialog.c, me, profiles); if (n) await api.deleteContact(me, n); }} onClose={() => setDialog(null)} />
      )}
      {dialog?.k === 'deleteConv' && (
        <Confirm title="Delete conversation?" body="This clears the chat from your devices only. The saved contact stays, and other people keep their copy. New messages will bring it back." action="Delete conversation" danger
          onConfirm={async () => { await api.clearConversation(me, dialog.c.id); if (cid === dialog.c.id) nav('/chats'); }} onClose={() => setDialog(null)} />
      )}
      {dialog?.k === 'leave' && (
        <Confirm title="Leave this group?" body="You will stop receiving messages from it." action="Leave group" danger
          onConfirm={async () => { await api.leaveGroup(dialog.c, me); if (cid === dialog.c.id) nav('/chats'); }} onClose={() => setDialog(null)} />
      )}

      {incoming && !call && (
        <CallOverlay call={{ name: incomingConv ? title(incomingConv) : 'Incoming call', video: incoming.type === 'video', incoming: true, state: 'ringing' }}
          tick={tick} onAccept={() => void acceptCall()} onReject={() => { void CallSession.reject(incoming.id).catch(() => toast('Could not decline the call.')); setIncoming(null); }}
          onHangup={() => undefined} onClose={() => setIncoming(null)} />
      )}
      {call && <CallOverlay call={call} tick={tick} onAccept={() => undefined} onReject={() => undefined}
        onHangup={() => { void call.session?.end(); setCall((c) => (c ? { ...c, state: 'ended' } : c)); }} onClose={() => { call.session?.cleanup(); setCall(null); }} />}
    </div>
  );
}

function mediaError(e: unknown): string {
  const name = (e as { name?: string })?.name;
  if (name === 'NotAllowedError') return 'Allow microphone (and camera) access in your browser to make calls.';
  if (name === 'NotFoundError') return 'No microphone or camera was found.';
  if (name === 'PermissionDeniedError') return 'Permission to use the microphone was denied.';
  return 'The call could not be started. Check your connection and try again.';
}

function Welcome({ onNumber, onChat, hasChats }: { onNumber: () => void; onChat: () => void; hasChats: boolean }) {
  return (
    <div className="welcome">
      <Logo size={120} />
      <h2>Welcome to Daily Talk</h2>
      <p className="muted">{hasChats ? 'Pick a conversation, or start a new one.' : 'Connect, share and stay in touch with your friends, family and colleagues.'}</p>
      <div className="row center">
        <button className="btn outline" onClick={onNumber}>Add a number</button>
        <button className="btn outline" onClick={onChat}>Start a chat</button>
      </div>
    </div>
  );
}
