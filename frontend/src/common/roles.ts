// The 8 role codes from specs/backend/02-ingestion-upload-run.md — static, hardcoded per
// specs/frontend/00-overview.md's "Static reference data" note, not fetched from an endpoint.
export const ROLES = ['T1', 'T2', 'T3', 'T4', 'LT', 'spiker', 'sos', 'emo'] as const;

export type Role = (typeof ROLES)[number];
