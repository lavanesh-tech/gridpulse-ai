import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { api, getToken, onTokenChange, setToken, tokenClaims } from '../api/client';

interface AuthState {
  user: { username: string; role: string } | null;
  signIn: (username: string, password: string) => Promise<void>;
  signOut: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [jwt, setJwt] = useState<string | null>(getToken());
  useEffect(() => onTokenChange(() => setJwt(getToken())), []);

  const value: AuthState = {
    user: tokenClaims(jwt),
    signIn: async (username, password) => {
      const res = await api<{ accessToken: string }>('/api/v1/auth/token', { body: { username, password } });
      setToken(res.accessToken);
    },
    signOut: () => setToken(null),
  };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}

/** Hides controls the user's role cannot use. Purely cosmetic: the API rejects the call anyway. */
export function can(role: string | undefined, ...allowed: string[]) {
  return !!role && allowed.includes(role);
}
