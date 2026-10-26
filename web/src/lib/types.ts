export interface Profile { uid: string; displayName: string; phone?: string | null }

export interface Conversation {
  id: string;
  type: 'direct' | 'group';
  members: string[];
  admins?: string[];
  name?: string;
  lastMessage?: { text: string; senderId: string };
  updatedAt: number;
  unread: Record<string, number>;
}

export type MessageKind = 'text' | 'gif' | 'image' | 'video';

export interface Message {
  id: string;
  senderId: string;
  senderName: string;
  kind: MessageKind;
  text?: string;
  url?: string;
  createdAt: number;
  readBy: string[];
  state: 'sent' | 'sending' | 'failed';
  progress?: number; // 0..1 while a photo/video is uploading
}

export interface Contact { number: string; name: string }

export interface Status {
  id: string;
  authorId: string;
  authorName: string;
  kind: 'text';
  text: string;
  createdAt: number;
  expiresAt: number;
  viewers: string[];
}

export interface CallDoc {
  id: string;
  callerId: string;
  calleeId: string;
  conversationId: string;
  type: 'voice' | 'video';
  state: 'ringing' | 'accepted' | 'rejected' | 'ended' | 'missed';
  offer?: { type: string; sdp: string };
  answer?: { type: string; sdp: string };
  createdAt: number;
}

