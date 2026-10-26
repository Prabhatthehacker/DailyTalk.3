import { useState, type FormEvent, type ReactNode } from 'react';
import { authMessage, login, register, resetPassword, signInWithGoogle } from '../lib/api';
import { Lockup, Logo } from './Logo';

const Icon = ({ children }: { children: ReactNode }) => (
  <svg className="field-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden>{children}</svg>
);
const MailIcon = () => <Icon><rect x="3" y="5" width="18" height="14" rx="3" /><path d="M3.5 7.5 12 13l8.5-5.5" /></Icon>;
const LockIcon = () => <Icon><rect x="5" y="10.5" width="14" height="9.5" rx="2.5" /><path d="M8 10.5V8a4 4 0 0 1 8 0v2.5" /></Icon>;
const UserIcon = () => <Icon><circle cx="12" cy="8.5" r="3.7" /><path d="M4.8 20c.9-3.6 3.6-5.3 7.2-5.3s6.3 1.7 7.2 5.3" /></Icon>;
const EyeIcon = ({ off }: { off: boolean }) => (
  <Icon><path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z" /><circle cx="12" cy="12" r="2.8" />{off && <path d="M4 4l16 16" />}</Icon>
);
const GoogleG = () => (
  <svg width="20" height="20" viewBox="0 0 48 48" aria-hidden>
    <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z" />
    <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z" />
    <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z" />
    <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z" />
  </svg>
);

export function AuthScreen() {
  const [mode, setMode] = useState<'in' | 'up' | 'reset'>('in');
  const [name, setName] = useState(''); const [email, setEmail] = useState(''); const [password, setPassword] = useState('');
  const [show, setShow] = useState(false);
  const [error, setError] = useState(''); const [info, setInfo] = useState(''); const [busy, setBusy] = useState(false);

  const fail = (err: unknown) => setError(err instanceof Error && !(err as { code?: string }).code ? err.message : authMessage(err));
  const go = (m: 'in' | 'up' | 'reset') => { setMode(m); setError(''); setInfo(''); };

  async function submit(e: FormEvent) {
    e.preventDefault(); setError(''); setInfo(''); setBusy(true);
    try {
      if (mode === 'up') { if (!name.trim()) throw new Error('Enter your name.'); await register(name, email, password); }
      else if (mode === 'in') await login(email, password);
      else { await resetPassword(email); setInfo('If an account exists for this email, a reset link is on its way.'); }
    } catch (err) { fail(err); } finally { setBusy(false); }
  }
  async function google() {
    setError(''); setInfo(''); setBusy(true);
    try { await signInWithGoogle(); } catch (err) { fail(err); } finally { setBusy(false); }
  }

  const title = mode === 'in' ? 'Welcome Back!' : mode === 'up' ? 'Join Daily Talk' : 'Reset password';
  const subtitle = mode === 'in' ? 'Sign in to continue to Daily Talk' : mode === 'up' ? 'Create your account. You get your own Daily Talk number.' : 'Enter your email and we will send a reset link.';

  return (
    <main className="auth">
      <form className="auth-card" onSubmit={submit} noValidate>
        <Lockup width={250} />
        <div className="auth-head"><h1>{title}</h1><p className="muted">{subtitle}</p></div>

        {mode === 'up' && (
          <label className="field"><span className="sr">Your name</span><UserIcon />
            <input value={name} onChange={(e) => setName(e.target.value)} autoComplete="name" maxLength={60} placeholder="Your name" required />
          </label>
        )}
        <label className="field"><span className="sr">Email address</span><MailIcon />
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" placeholder="Email address" required />
        </label>
        {mode !== 'reset' && (
          <label className="field"><span className="sr">Password</span><LockIcon />
            <input type={show ? 'text' : 'password'} value={password} onChange={(e) => setPassword(e.target.value)} autoComplete={mode === 'up' ? 'new-password' : 'current-password'} minLength={6} placeholder="Password" required />
            <button type="button" className="eye" onClick={() => setShow(!show)} aria-label={show ? 'Hide password' : 'Show password'} aria-pressed={show}><EyeIcon off={show} /></button>
          </label>
        )}

        {mode === 'in' && <button type="button" className="link forgot" onClick={() => go('reset')}>Forgot password?</button>}
        {error && <p className="error" role="alert">{error}</p>}
        {info && <p className="ok" role="status">{info}</p>}

        <button className="btn signin" disabled={busy}>
          {busy ? 'Please wait…' : mode === 'in' ? 'Sign In' : mode === 'up' ? 'Create account' : 'Send reset link'}
          {!busy && <span aria-hidden> →</span>}
        </button>

        {mode !== 'reset' && (
          <>
            <div className="or"><span>or</span></div>
            <button type="button" className="btn google" onClick={() => void google()} disabled={busy}><GoogleG /> Continue with Google</button>
          </>
        )}

        <p className="switch">
          {mode === 'in' ? <>Don’t have an account? <button type="button" className="link" onClick={() => go('up')}>Sign Up</button></>
            : <>{mode === 'up' ? 'Already have an account?' : 'Remembered it?'} <button type="button" className="link" onClick={() => go('in')}>Sign In</button></>}
        </p>
        <svg className="wave" viewBox="0 0 400 60" preserveAspectRatio="none" aria-hidden><path d="M0 28C60 4 120 6 200 26s150 18 200-10v44H0Z" /></svg>
      </form>
    </main>
  );
}

export function SetupNeeded() {
  return (
    <main className="auth"><div className="auth-card">
      <Logo size={96} /><h1>Almost there</h1>
      <p className="muted">This build has no Firebase settings. Copy <code>.env.example</code> to <code>web/.env.local</code>, fill in your project values, and restart. See the README for the Firebase Console steps.</p>
    </div></main>
  );
}
