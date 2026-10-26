import type { Contact, Conversation, Profile } from './types';
import { formatNumber } from './number';

/** The name shown for a conversation: saved name > number > profile name. */
export function conversationTitle(
  c: Conversation, me: string, contacts: Map<string, Contact>, profiles: Map<string, Profile>,
): string {
  if (c.type === 'group') return c.name || 'Group';
  const peerUid = c.members.find((m) => m !== me);
  const peer = peerUid ? profiles.get(peerUid) : undefined;
  const number = peer?.phone;
  if (number) return contacts.get(number)?.name || formatNumber(number);
  return peer?.displayName || 'Unknown';
}

export function peerNumberOf(c: Conversation, me: string, profiles: Map<string, Profile>): string | undefined {
  if (c.type !== 'direct') return undefined;
  const peerUid = c.members.find((m) => m !== me);
  return (peerUid && profiles.get(peerUid)?.phone) || undefined;
}

export function initials(name: string): string {
  const parts = name.replace(/[^\p{L}\p{N} ]/gu, '').trim().split(/\s+/).filter(Boolean);
  if (!parts.length) return '?';
  return (parts[0][0] + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
}

export function formatTime(ts: number): string {
  const d = new Date(ts);
  const now = new Date();
  if (d.toDateString() === now.toDateString()) return d.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
  const y = new Date(now); y.setDate(now.getDate() - 1);
  if (d.toDateString() === y.toDateString()) return 'Yesterday';
  return d.toLocaleDateString([], { month: 'short', day: 'numeric' });
}
