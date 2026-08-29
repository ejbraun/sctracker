import { MAPS, mapById, defaultPartySize, sizeLabel } from '../common/maps';
import styles from './MapSizePicker.module.css';

interface Props {
  mapId: string;
  /** Currently-selected party size. Defaults to the map's default when omitted. */
  partySize?: number;
  onMapChange: (mapId: string) => void;
  /**
   * Called when the user picks a different party size. When supplied, the size always renders as a
   * real <select> (with one option for a single-size map like the Underworld) so it looks and
   * behaves the same across maps. Omit to fall back to a static, read-only chip.
   */
  onSizeChange?: (partySize: number) => void;
}

/**
 * Shared map + party-size control for Dashboard, Leaderboards, and Loserboards. The size control is
 * a real <select> whenever {@link Props.onSizeChange} is supplied — even for a map with only one
 * supported size — so it's visually consistent everywhere.
 */
export function MapSizePicker({ mapId, partySize, onMapChange, onSizeChange }: Props) {
  const map = mapById(mapId);
  const sizes = map?.partySizes ?? [];
  const effectiveSize = partySize ?? defaultPartySize(mapId) ?? sizes[0];
  const sizeIsSelectable = onSizeChange != null;

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
