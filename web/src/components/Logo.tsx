import logoUrl from '../assets/dailytalk-logo.png';
import lockupUrl from '../assets/dailytalk-lockup.png';

/** The two smiling speech bubbles (square, transparent). */
export function Logo({ size = 40 }: { size?: number }) {
  return <img src={logoUrl} width={size} height={size} alt="Daily Talk" style={{ display: 'block' }} />;
}

/** Bubbles + "Daily Talk" + tagline, used on the sign-in screen. */
export function Lockup({ width = 260 }: { width?: number }) {
  return <img src={lockupUrl} width={width} alt="Daily Talk. Small messages. Big connections." style={{ display: 'block', maxWidth: '100%', height: 'auto' }} />;
}

export function Avatar({ name, size = 44, group }: { name: string; size?: number; group?: boolean }) {
  const parts = name.replace(/[^\p{L}\p{N} ]/gu, '').trim().split(/\s+/).filter(Boolean);
  const text = group ? '👥' : parts.length ? (parts[0][0] + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase() : '?';
  let h = 0; for (const ch of name) h = (h * 31 + ch.charCodeAt(0)) % 360;
  const hue = 20 + (h % 40); // stays inside the warm range
  return <span className="avatar" style={{ width: size, height: size, fontSize: size * 0.38, background: `hsl(${hue} 55% 82%)`, color: `hsl(${hue} 45% 28%)` }} aria-hidden>{text}</span>;
}
