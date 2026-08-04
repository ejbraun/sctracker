import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { Person } from '../api/types';
import { Panel } from '../components/Panel';
import { ErrorBanner } from '../components/ErrorBanner';
import styles from './AuthPage.module.css';

/** specs/frontend/01-auth.md. */
export function Login() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: () => api.post<Person>('/login', { username, password }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['account', 'me'] });
      navigate(searchParams.get('redirect') ?? '/');
    },
  });

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    mutation.mutate();
  }

  return (
    <div className={styles.page}>
      <Panel className={styles.form}>
        <h1>Login</h1>
        <ErrorBanner error={mutation.error} />
        <form onSubmit={handleSubmit}>
          <label>
            Username
            <input value={username} onChange={(e) => setUsername(e.target.value)} required autoFocus />
          </label>
          <label>
            Password
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          </label>
          <button type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? 'Logging in…' : 'Login'}
          </button>
        </form>
        <p>
          No account? <Link to="/signup">Sign up</Link>
        </p>
      </Panel>
    </div>
  );
}
