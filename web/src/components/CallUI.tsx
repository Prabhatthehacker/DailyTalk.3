import { useEffect, useRef, useState } from 'react';
import { CallSession, type CallState } from '../lib/calls';
import { Avatar } from './Logo';

export interface ActiveCall { session?: CallSession; name: string; video: boolean; incoming?: boolean; state: CallState; error?: string }

const LABEL: Record<CallState, string> = {
  calling: 'Calling…', ringing: 'Incoming call…', connecting: 'Connecting…', connected: 'Connected', reconnecting: 'Reconnecting…',
  ended: 'Call ended', rejected: 'Call declined', missed: 'No answer', failed: 'Connection lost',
};

export function CallOverlay({ call, tick, onAccept, onReject, onHangup, onClose }: {
  call: ActiveCall; tick: number; onAccept: () => void; onReject: () => void; onHangup: () => void; onClose: () => void;
}) {
  const localRef = useRef<HTMLVideoElement>(null); const remoteRef = useRef<HTMLVideoElement>(null);
  const [muted, setMuted] = useState(false); const [camOff, setCamOff] = useState(false); const [secs, setSecs] = useState(0);
  const s = call.session;
  useEffect(() => { if (localRef.current && s?.local) localRef.current.srcObject = s.local; if (remoteRef.current && s) remoteRef.current.srcObject = s.remote; }, [s, tick]);
  useEffect(() => {
    if (call.state !== 'connected') return;
    const t0 = Date.now(); const t = setInterval(() => setSecs(Math.floor((Date.now() - t0) / 1000)), 1000);
    return () => clearInterval(t);
  }, [call.state]);
  const over = ['ended', 'rejected', 'missed', 'failed'].includes(call.state) || !!call.error;
  useEffect(() => { if (!over) return; const t = setTimeout(onClose, 2500); return () => clearTimeout(t); }, [over, onClose]);
  const ringingIn = call.incoming && call.state === 'ringing';

  return (
    <div className="call" role="dialog" aria-modal="true" aria-label="Call">
      {call.video && <video ref={remoteRef} className="remote" autoPlay playsInline />}
      {!call.video && <audio ref={remoteRef as never} autoPlay />}
      <div className="call-info">
        <Avatar name={call.name} size={96} />
        <h2>{call.name}</h2>
        <p role="status">{call.error ?? (call.state === 'connected' ? `${String(Math.floor(secs / 60)).padStart(2, '0')}:${String(secs % 60).padStart(2, '0')}` : LABEL[call.state])}</p>
      </div>
      {call.video && <video ref={localRef} className="local" autoPlay playsInline muted />}
      <div className="call-actions">
        {ringingIn ? (<>
          <button className="round bad" onClick={onReject} aria-label="Decline">✕</button>
          <button className="round good" onClick={onAccept} aria-label="Accept">📞</button>
        </>) : !over && (<>
          <button className={`round ${muted ? 'on' : ''}`} aria-pressed={muted} aria-label="Mute" onClick={() => { s?.setMuted(!muted); setMuted(!muted); }}>🎙</button>
          {call.video && <button className={`round ${camOff ? 'on' : ''}`} aria-pressed={camOff} aria-label="Camera" onClick={() => { s?.setCameraOff(!camOff); setCamOff(!camOff); }}>🎥</button>}
          <button className="round bad" onClick={onHangup} aria-label="End call">✕</button>
        </>)}
        {over && <button className="btn primary" onClick={onClose}>Close</button>}
      </div>
    </div>
  );
}
