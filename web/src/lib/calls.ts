import { get, onChildAdded, onValue, push, remove, serverTimestamp, set, update, type Unsubscribe } from 'firebase/database';
import { R } from './api';
import type { CallDoc } from './types';

export type CallState = 'calling' | 'ringing' | 'connecting' | 'connected' | 'reconnecting' | 'ended' | 'rejected' | 'missed' | 'failed';

export function iceServers(): RTCIceServer[] {
  const servers: RTCIceServer[] = [{ urls: ['stun:stun.l.google.com:19302', 'stun:stun1.l.google.com:19302'] }];
  const url = import.meta.env.VITE_TURN_URL as string | undefined;
  if (url) servers.push({ urls: url, username: import.meta.env.VITE_TURN_USERNAME as string, credential: import.meta.env.VITE_TURN_CREDENTIAL as string });
  return servers;
}

export const RING_TIMEOUT_MS = 45_000;

function toCall(id: string, d: Record<string, unknown>): CallDoc {
  return {
    id, callerId: d.callerId as string, calleeId: d.calleeId as string, conversationId: d.conversationId as string,
    type: d.type as CallDoc['type'], state: d.state as CallDoc['state'], offer: d.offer as CallDoc['offer'],
    answer: d.answer as CallDoc['answer'], createdAt: (d.createdAt as number) ?? Date.now(),
  };
}

/** Incoming calls arrive in your private callInbox; each entry is read once and then removed. */
export function watchIncomingCalls(uid: string, cb: (c: CallDoc) => void): Unsubscribe {
  return onChildAdded(R(`callInbox/${uid}`), async (snap) => {
    const id = snap.key!;
    void remove(R(`callInbox/${uid}/${id}`)).catch(() => undefined);
    try {
      const call = await get(R(`calls/${id}`));
      if (!call.exists()) return;
      const c = toCall(id, call.val());
      if (c.callerId !== uid && c.state === 'ringing' && Date.now() - c.createdAt < RING_TIMEOUT_MS) cb(c);
    } catch { /* not allowed or gone: ignore */ }
  }, () => undefined);
}

/** One WebRTC call. Signaling goes through the Realtime Database (calls/{id} + calls/{id}/candidates). */
export class CallSession {
  pc: RTCPeerConnection;
  local: MediaStream | null = null;
  remote = new MediaStream();
  private unsubs: Unsubscribe[] = [];
  private queued: RTCIceCandidateInit[] = [];
  private timer?: ReturnType<typeof setTimeout>;
  private done = false;

  constructor(
    public callId: string, private me: string, private video: boolean,
    private onState: (s: CallState) => void, private onStreams: () => void,
  ) {
    this.pc = new RTCPeerConnection({ iceServers: iceServers() });
    this.pc.ontrack = (e) => { e.streams[0]?.getTracks().forEach((t) => this.remote.addTrack(t)); this.onStreams(); };
    this.pc.onconnectionstatechange = () => {
      const s = this.pc.connectionState;
      if (s === 'connected') this.onState('connected');
      else if (s === 'disconnected') this.onState('reconnecting');
      else if (s === 'failed') { this.onState('failed'); void this.end('ended'); }
    };
    this.pc.onicecandidate = (e) => {
      if (!e.candidate) return;
      const j = e.candidate.toJSON();
      void set(push(R(`calls/${this.callId}/candidates`)), {
        from: this.me, candidate: j.candidate, ...(j.sdpMid ? { sdpMid: j.sdpMid } : {}), sdpMLineIndex: j.sdpMLineIndex ?? 0,
      }).catch(() => undefined);
    };
  }

  private async media() {
    // Throws NotAllowedError / NotFoundError; the caller shows a helpful message.
    this.local = await navigator.mediaDevices.getUserMedia({ audio: true, video: this.video ? { facingMode: 'user' } : false });
    this.local.getTracks().forEach((t) => this.pc.addTrack(t, this.local!));
    this.onStreams();
  }

  private listenCandidates() {
    this.unsubs.push(onChildAdded(R(`calls/${this.callId}/candidates`), (snap) => {
      const d = snap.val() as { from: string; candidate: string; sdpMid?: string; sdpMLineIndex?: number };
      if (!d || d.from === this.me) return;
      const init: RTCIceCandidateInit = { candidate: d.candidate, sdpMid: d.sdpMid ?? null, sdpMLineIndex: d.sdpMLineIndex ?? 0 };
      if (this.pc.remoteDescription) void this.pc.addIceCandidate(init).catch(() => undefined); else this.queued.push(init);
    }));
  }
  private async flush() { for (const c of this.queued.splice(0)) await this.pc.addIceCandidate(c).catch(() => undefined); }

  static async start(me: string, peer: string, conversationId: string, video: boolean, onState: (s: CallState) => void, onStreams: () => void) {
    const callId = push(R('calls')).key!;
    const s = new CallSession(callId, me, video, onState, onStreams);
    onState('calling');
    try {
      await s.media();
      const offer = await s.pc.createOffer();
      await s.pc.setLocalDescription(offer);
      await set(R(`calls/${callId}`), {
        callerId: me, calleeId: peer, conversationId, type: video ? 'video' : 'voice', state: 'ringing',
        offer: { type: offer.type, sdp: offer.sdp }, createdAt: serverTimestamp(),
      });
      await set(R(`callInbox/${peer}/${callId}`), { callerId: me, conversationId, type: video ? 'video' : 'voice', createdAt: serverTimestamp() });
    } catch (e) { s.cleanup(); throw e; }
    s.listenCandidates();
    s.unsubs.push(onValue(R(`calls/${callId}`), async (snap) => {
      const d = snap.val(); if (!d) return;
      if (d.answer && !s.pc.currentRemoteDescription) {
        await s.pc.setRemoteDescription(d.answer); await s.flush(); onState('connecting');
      }
      if (['rejected', 'ended', 'missed'].includes(d.state)) { onState(d.state); s.cleanup(); }
    }));
    s.timer = setTimeout(() => { if (!s.pc.currentRemoteDescription) { onState('missed'); void s.end('missed'); } }, RING_TIMEOUT_MS);
    return s;
  }

  static async answer(me: string, call: CallDoc, onState: (s: CallState) => void, onStreams: () => void) {
    const s = new CallSession(call.id, me, call.type === 'video', onState, onStreams);
    onState('connecting');
    try {
      await s.media();
      await s.pc.setRemoteDescription(call.offer as RTCSessionDescriptionInit);
      const answer = await s.pc.createAnswer();
      await s.pc.setLocalDescription(answer);
      await update(R(`calls/${call.id}`), { answer: { type: answer.type, sdp: answer.sdp }, state: 'accepted' });
    } catch (e) { s.cleanup(); await set(R(`calls/${call.id}/state`), 'ended').catch(() => undefined); throw e; }
    s.listenCandidates();
    await s.flush();
    s.unsubs.push(onValue(R(`calls/${call.id}/state`), (snap) => {
      const st = snap.val(); if (['ended', 'missed'].includes(st)) { onState('ended'); s.cleanup(); }
    }));
    return s;
  }

  static reject(callId: string) { return set(R(`calls/${callId}/state`), 'rejected'); }

  setMuted(m: boolean) { this.local?.getAudioTracks().forEach((t) => (t.enabled = !m)); }
  setCameraOff(off: boolean) { this.local?.getVideoTracks().forEach((t) => (t.enabled = !off)); }

  async end(state: 'ended' | 'missed' = 'ended') {
    await set(R(`calls/${this.callId}/state`), state).catch(() => undefined);
    this.cleanup();
  }
  cleanup() {
    if (this.done) return; this.done = true;
    clearTimeout(this.timer);
    this.unsubs.forEach((u) => u());
    this.local?.getTracks().forEach((t) => t.stop());
    this.pc.close();
  }
}
