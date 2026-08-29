import { MAPS, mapById, defaultPartySize, sizeLabel } from '../common/maps';
import styles from './MapSizePicker.module.css';

interface Props {
  mapId: string;
  /** Currently-selected party size. Defaults to the map's default when omitted. */
  partySize?: number;
  onMapChange: (mapId: string) => void;
  /**
   * Called when the user picks a different party size. Omit to make the size read-only — it then
   * renders as a static chip (used on Leaderboards / Loserboards in v1, where the map path segment
   * already implies the size; see specs/features/fow-and-party-size.md §4.4).
   */
  onSizeChange?: (partySize: number) => void;
}

/**
 * Shared map + party-size control for Dashboard, Leaderboards, Loserboards, and Run History. The
 * size control only becomes a real <select> when the chosen map supports more than one party size
 * AND {@link Props.onSizeChange} is supplied; otherwise it's a display-only chip.
 */
export function MapSizePicker({ mapId, partySize, onMapChange, onSizeChange }: Props) {
  const map = mapById(mapId);
  const sizes = map?.partySizes ?? [];
  const effectiveSize = partySize ?? defaultPartySize(mapId) ?? sizes[0];
  const sizeIsSelectable = onSizeChange != null && sizes.length > 1;

  function handleMapChange(nextMapId: string) {
    onMapChange(nextMapId);
    // Keep the selected size consistent with the new map — snap to its default.
    if (onSizeChange) {
      const nextDefault = defaultPartySize(nextMapId);
      if (nextDefault != null) {
        onSizeChange(nextDefault);
      }
    }
  }

  return (
    <div className={styles.picker}>
      <label className={styles.field}>
        Map
        <select value={mapId} onChange={(e) => handleMapChange(e.target.value)}>
          {MAPS.map((m) => (
            <option key={m.id} value={m.id}>
              {m.name}
            </option>
          ))}
        </select>
      </label>

      <label className={styles.field}>
        Party Size
        {sizeIsSelectable ? (
          <select value={effectiveSize} onChange={(e) => onSizeChange?.(Number(e.target.value))}>
            {sizes.map((n) => (
              <option key={n} value={n}>
                {sizeLabel(n)}
              </option>
            ))}
          </select>
        ) : (
          <span className={styles.sizeChip}>{effectiveSize != null ? sizeLabel(effectiveSize) : '—'}</span>
        )}
      </label>
    </div>
  );
}
