// Role codes surfaced in the UI, per specs/backend/02-ingestion-upload-run.md and
// specs/features/fow-and-party-size.md. Static reference data (specs/frontend/00-overview.md).

/** The Underworld 8-man trapper-team scheme. */
export const TRAPPER_ROLES = [
  'T1', 'T2', 'T3', 'T4', 'LT', 'Spiker', 'Derv', 'SoS', 'Necro', 'RangerNecro', 'Emo',
] as const;

/** The Fissure of Woe duo scheme — role = primary profession (RoleModel.PRIMARY_PROFESSION). */
export const PRIMARY_PROFESSION_ROLES = ['Ranger', 'Derv'] as const;

/** Every role code, for map-agnostic UI like the Run History role filter. */
export const ROLES = [...TRAPPER_ROLES, 'Ranger'] as const;

export type Role = (typeof ROLES)[number];
