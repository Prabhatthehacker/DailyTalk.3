import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { onAuthStateChanged, type User } from 'firebase/auth';
import { auth, firebaseConfigured } from './lib/firebase';
import * as api from './lib/api';
import type { Contact, Conversation, Profile, Status } from './lib/types';

interface Store {
  ready: boolean;
  user: User | null;
  profile: Profile | null;
  conversations: Conversation[];   // visible (not cleared) conversations
  allConversations: Conversation[];
  cleared: Map<string, number>;
  contacts: Map<string, Contact>;
  profiles: Map<string, Profile>;
  statuses: Status[];
  toast: (msg: string) => void;
  toastMsg: string | null;
}
const Ctx = createContext<Store | null>(null);
export const useStore = () => { const s = useContext(Ctx); if (!s) throw new Error('no store'); return s; };

export function StoreProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(!firebaseConfigured);
  const [user, setUser] = useState<User | null>(null);
  const [profile, setProfile] = useState<Profile | null>(null);
  const [all, setAll] = useState<Conversation[]>([]);
  const [cleared, setCleared] = useState(new Map<string, number>());
  const [contactList, setContactList] = useState<Contact[]>([]);
  const [profiles, setProfiles] = useState(new Map<string, Profile>());
  const [statuses, setStatuses] = useState<Status[]>([]);
  const [toastMsg, setToastMsg] = useState<string | null>(null);
  const assigning = useRef(false);

  const toast = useCallback((m: string) => { setToastMsg(m); setTimeout(() => setToastMsg(null), 4000); }, []);

  useEffect(() => {
    if (!firebaseConfigured) return;
    return onAuthStateChanged(auth, (u) => { setUser(u); if (!u) { setProfile(null); setAll([]); setStatuses([]); } setReady(true); });
  }, []);

  useEffect(() => {
    if (!user) return;
    const uid = user.uid;
    const subs = [
      api.watchProfile(uid, (p) => {
        if (p?.phone) { setProfile(p); return; }
        // Missing profile or number (e.g. sign-up was interrupted): create it now. The listener fires again when done.
        if (!assigning.current) {
          assigning.current = true;
          api.ensureAccount(uid, user.displayName || 'Me').catch(() => toast('Could not create your Daily Talk number. Check your connection and reload.'))
            .finally(() => { assigning.current = false; });
        }
        setProfile(p ?? null);
      }),
      api.watchConversations(uid, setAll),
      api.watchCleared(uid, setCleared),
      api.watchContacts(uid, setContactList),
      api.watchStatuses(uid, setStatuses),
    ];
    return () => subs.forEach((u) => u());
  }, [user, toast]);

  // Load profiles of everyone we share a conversation with.
  const memberKey = useMemo(() => [...new Set(all.flatMap((c) => c.members))].sort().join(','), [all]);
  useEffect(() => {
    const ids = memberKey ? memberKey.split(',') : [];
    if (!ids.length) return;
    let live = true;
    void api.fetchProfiles(ids).then((ps) => { if (live) setProfiles(new Map(ps.map((p) => [p.uid, p]))); }).catch(() => undefined);
    return () => { live = false; };
  }, [memberKey]);

  const conversations = useMemo(
    () => all.filter((c) => { const at = cleared.get(c.id); return !at || c.updatedAt > at; }),
    [all, cleared],
  );
  const contacts = useMemo(() => new Map(contactList.map((c) => [c.number, c])), [contactList]);

  const value: Store = { ready, user, profile, conversations, allConversations: all, cleared, contacts, profiles, statuses, toast, toastMsg };
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}
