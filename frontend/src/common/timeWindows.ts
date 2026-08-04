// A well-defined set of time-window presets — shared by Run History and Leaderboards so both
// filter the same way, instead of exposing raw from/to date inputs.
export const TIME_WINDOWS = ['day', 'week', 'month', 'year', 'all'] as const;

export type TimeWindow = (typeof TIME_WINDOWS)[number];

export const TIME_WINDOW_LABELS: Record<TimeWindow, string> = {
  day: 'Past day',
  week: 'Past week',
  month: 'Past month',
  year: 'Past year',
  all: 'All time',
};

const WINDOW_MS: Record<Exclude<TimeWindow, 'all'>, number> = {
  day: 24 * 60 * 60 * 1000,
  week: 7 * 24 * 60 * 60 * 1000,
  month: 30 * 24 * 60 * 60 * 1000,
  year: 365 * 24 * 60 * 60 * 1000,
};

/** ISO `from` timestamp for the window, or null for 'all' (unbounded — no query param sent). */
export function timeWindowFrom(window: TimeWindow, now: Date = new Date()): string | null {
  if (window === 'all') {
    return null;
  }
  return new Date(now.getTime() - WINDOW_MS[window]).toISOString();
}
