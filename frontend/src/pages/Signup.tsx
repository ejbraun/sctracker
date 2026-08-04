import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '../api/client';
import type { Person } from '../api/types';
import { Panel } from '../components/Panel';
import { ErrorBanner } from '../components/ErrorBanner';
import styles from './AuthPage.module.css';

const MIN_PASSWORD_LENGTH = 8;

/** specs/frontend/01-auth.md. */
export function Signup() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [signupKey, setSignupKey] = useState('');
  const [clientError, setClientError] = useState<string | null>(null);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: () => api.post<Person>('/signup', { username, password, signup_key: signupKey }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['account', 'me'] });
      navigate(searchParams.get('redirect') ?? '/');
    },
  });

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setClientError(null);
    if (password.length < MIN_PASSWORD_LENGTH) {
      setClientError(`Password must be at least ${MIN_PASSWORD_LENGTH} characters`);
      return;
    }
    if (password !== confirmPassword) {
      setClientError('Passwords do not match');
      return;
    }
    mutation.mutate();
  }

  return (
    <div className={styles.page}>
      <Panel className={styles.form}>
        <h1>Sign up</h1>
        {clientError ? <ErrorBanner error={new ApiError(0, clientError)} /> : <ErrorBanner error={mutation.error} />}
        <form onSubmit={handleSubmit}>
          <label>
            Username
            <input value={username} onChange={(e) => setUsername(e.target.value)} required autoFocus />
          </label>
          <label>
            Password
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          </label>
          <label>
            Confirm password
            <input type="password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} required />
          </label>
          <label>
            Signup key
            <input value={signupKey} onChange={(e) => setSignupKey(e.target.value)} required />
          </label>
          <button type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? 'Signing up…' : 'Sign up'}
          </button>
        </form>
        <p>
          Already have an account? <Link to="/login">Log in</Link>
        </p>
      </Panel>
    </div>
  );
}
