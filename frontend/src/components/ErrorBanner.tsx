import { ApiError } from '../api/client';
import styles from './ErrorBanner.module.css';

export function ErrorBanner({ error }: { error: unknown }) {
  if (!error) {
    return null;
  }
  const message = error instanceof ApiError || error instanceof Error ? error.message : String(error);
  return <div className={styles.banner}>{message}</div>;
}
