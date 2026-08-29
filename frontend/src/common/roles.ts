// Role codes surfaced in the UI. The first 11 are the Underworld 8-man scheme from
// specs/backend/02-ingestion-upload-run.md; "Ranger" is the Fissure of Woe duo scheme
// (role = primary profession — RoleModel.PRIMARY_PROFESSION; "Derv" is shared with the UW set).
// Static, hardcoded per specs/frontend/00-overview.md's "Static reference data" note.
export const ROLES = [
  'T1', 'T2', 'T3', 'T4', 'LT', 'Spiker', 'Derv', 'SoS', 'Necro', 'RangerNecro', 'Emo', 'Ranger',
] as const;

export type Role = (typeof ROLES)[number];
