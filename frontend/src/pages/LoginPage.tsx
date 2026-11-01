import { useState, type FormEvent } from 'react';
import { Navigate } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { ErrorNote } from '../components/Ui';

export function LoginPage() {
  const { user, signIn } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  if (user) return <Navigate to="/" replace />;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await signIn(username, password);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="min-h-screen grid place-items-center px-5">
      <form onSubmit={submit} className="w-full max-w-sm grid gap-4" aria-labelledby="sign-in-title">
        <div>
          <div className="figure text-ink-soft">GridPulse depot console</div>
          <h1 id="sign-in-title">Sign in</h1>
        </div>
        <label className="lbl">Username
          <input className="field" autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} required />
        </label>
        <label className="lbl">Password
          <input className="field" type="password" autoComplete="current-password" value={password}
            onChange={(e) => setPassword(e.target.value)} required />
        </label>
        <ErrorNote error={error} />
        <button className="btn justify-center" type="submit" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
      </form>
    </main>
  );
}
