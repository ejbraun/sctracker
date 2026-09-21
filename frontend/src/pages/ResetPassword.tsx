import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { api, ApiError } from '../api/client';
import { Panel } from '../components/Panel';
import { ErrorBanner } from '../components/ErrorBanner';
import styles from './AuthPage.module.css';

const MIN_PASSWORD_LENGTH = 8;

/**
 * Public page behind an admin-issued link (/reset-password?token=<token>). Sets the password, then
 * sends the user to /login — a reset doesn't start a session. specs/frontend/10-admin-password-reset.md.
 */
export function ResetPassword() {
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [clientError, setClientError] = useState<string | null>(null);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');

  const mutation = useMutation({
    mutationFn: () => api.post<void>('/password-reset', { token, password }),
    onSuccess: () => navigate('/login', { state: { passwordReset: true } }),
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

  if (!token) {
    return (
      <div className={styles.page}>
        <Panel className={styles.form}>
          <h1>Reset password</h1>
          <p>This reset link is missing its token. Ask an admin for a new one.</p>
          <p>
            <Link to="/login">Back to login</Link>
          </p>
        </Panel>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <Panel className={styles.form}>
        <h1>Reset password</h1>
        {clientError ? <ErrorBanner error={new ApiError(0, clientError)} /> : <ErrorBanner error={mutation.error} />}
        <form onSubmit={handleSubmit}>
          <label>
            New password
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required autoFocus />
          </label>
          <label>
            Confirm new password
            <input type="password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} required />
          </label>
          <button type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? 'Saving…' : 'Set password'}
          </button>
        </form>
      </Panel>
    </div>
  );
}
