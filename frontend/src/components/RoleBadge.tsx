import styles from './RoleBadge.module.css';

/** role = null (unresolved profession combo, spec 02) renders with an explicit label, not a blank cell — spec 06. */
export function RoleBadge({ role }: { role: string | null }) {
  if (!role) {
    return <span className={`${styles.badge} ${styles.unresolved}`}>unresolved</span>;
  }
  return <span className={styles.badge}>{role}</span>;
}
