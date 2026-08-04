import { runStatus } from '../common/runStatus';
import { StatusIcon } from './StatusIcon';
import styles from './StatusBadge.module.css';

export function StatusBadge({ completed, endReason }: { completed: boolean; endReason: string }) {
  const status = runStatus(completed, endReason);
  return (
    <span className={styles.badge}>
      <StatusIcon kind={status.kind} color={status.color} size={12} />
      {status.label}
    </span>
  );
}
