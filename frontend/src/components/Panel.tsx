import type { ReactNode } from 'react';
import styles from './Panel.module.css';

export function Panel({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={className ? `${styles.panel} ${className}` : styles.panel}>{children}</div>;
}
