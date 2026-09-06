/** `(vN)` only for a real, positive build number — a manifest with no `version` deserializes to 0. */
export function versionSuffix(version: number | null): string {
  return version != null && version > 0 ? ` (v${version})` : '';
}
