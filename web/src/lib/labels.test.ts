import { describe, expect, it } from 'vitest';
import { conversationTitle, initials } from './labels';
import type { Contact, Conversation, Profile } from './types';

const conv = (o: Partial<Conversation>): Conversation => ({ id: 'c', type: 'direct', members: ['me'], updatedAt: 0, unread: {}, ...o });

describe('conversation titles', () => {
  const contacts = new Map<string, Contact>([['1234567890', { number: '1234567890', name: 'Sarah' }]]);
  const profiles = new Map<string, Profile>([['p', { uid: 'p', displayName: 'Peer', phone: '1234567890' }]]);
  it('shows the number first, then the saved name', () => {
    expect(conversationTitle(conv({ members: ['me', 'p'] }), 'me', new Map(), profiles)).toBe('12345 67890');
    expect(conversationTitle(conv({ members: ['me', 'p'] }), 'me', contacts, profiles)).toBe('Sarah');
  });
  it('uses the group name', () => {
    expect(conversationTitle(conv({ type: 'group', name: 'Team' }), 'me', contacts, profiles)).toBe('Team');
  });
  it('builds initials', () => { expect(initials('Sarah Johnson')).toBe('SJ'); expect(initials('')).toBe('?'); });
});
