import { createContext, useContext, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { Person } from '../api/types';

interface AuthContextValue {
  person: Person | null;
  isLoading: boolean;
}

const AuthContext = createContext<AuthContextValue>({ person: null, isLoading: true });

/** specs/frontend/00-overview.md — calls GET /api/account/me once on mount via TanStack Query. */
export function AuthProvider({ children }: { children: ReactNode }) {
  const { data, isLoading } = useQuery({
    queryKey: ['account', 'me'],
    // A 401 here just means "not logged in" — not a real error to surface, so it resolves to null
    // rather than leaving the query in an error state.
    queryFn: () => api.get<Person>('/account/me').catch(() => null),
    retry: false,
    staleTime: Infinity,
  });

  return <AuthContext.Provider value={{ person: data ?? null, isLoading }}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  return useContext(AuthContext);
}
