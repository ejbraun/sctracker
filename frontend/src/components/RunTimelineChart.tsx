import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { RunSummary } from '../api/types';
import { formatDuration } from '../common/format';
import { runStatus } from '../common/runStatus';
import { StatusIcon } from './StatusIcon';
import styles from './RunTimelineChart.module.css';

/**
 * Scatter plot of runs over time (x = utc_start, y = duration), status-colored per run
 * (completed/wipe/resign/unknown) — see the dataviz skill's guidance this was built against.
 * This is a scatter, not a line: each run is an independent event, and a line implies a
 * continuity between consecutive runs that doesn't exist here.
 *
 * "Scrolled over" (the actual ask): rendered at a fixed density (pixels per day) inside a
 * horizontally scrolling container — but never narrower than the container itself (ResizeObserver-
 * measured, so it fills the available width for short date ranges) — so a long date range scrolls
 * left/right instead of being squeezed into one fixed width, while a short one still fills the
 * space rather than leaving it mostly blank. Never squeezed smaller than its natural pixel-per-day
 * size either way — only ever stretched wider.
 *
 * A table view already exists alongside this on the run-history page (specs/frontend/05), which
 * is what the dataviz skill's "a table view exists" check is looking for — not duplicated here.
 *
 * Each point navigates to that run's detail page on click (or Enter/Space when focused) — role="link"
 * plus a keyboard handler since an SVG <g> has no native click/activation semantics of its own.
 */

const PX_PER_DAY = 36;
const MIN_PLOT_WIDTH = 640;
const PLOT_HEIGHT = 320;
const MARGIN = { top: 16, right: 24, bottom: 40, left: 64 };
const POINT_SIZE = 11;
const HIT_RADIUS = 12;
const Y_TICK_COUNT = 5;
const X_TICK_COUNT = 6;
const DAY_MS = 86_400_000;

interface HoveredPoint {
  run: RunSummary;
  cx: number;
  cy: number;
}

function niceCeil(value: number): number {
  if (value <= 0) {
    return 1;
  }
  const exponent = Math.floor(Math.log10(value));
  const magnitude = 10 ** exponent;
  const residual = value / magnitude;
  const niceResidual = residual <= 1 ? 1 : residual <= 2 ? 2 : residual <= 5 ? 5 : 10;
  return niceResidual * magnitude;
}

export function RunTimelineChart({ runs }: { runs: RunSummary[] }) {
  const navigate = useNavigate();
  const [hovered, setHovered] = useState<HoveredPoint | null>(null);

  const scrollAreaRef = useRef<HTMLDivElement>(null);
  const [containerWidth, setContainerWidth] = useState(MIN_PLOT_WIDTH);
  useEffect(() => {
    const el = scrollAreaRef.current;
    if (!el) return;
    const observer = new ResizeObserver(([entry]) => {
      if (entry) setContainerWidth(entry.contentRect.width);
    });
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  const plottable = useMemo(() => runs.filter((r) => r.duration_ms != null), [runs]);
  const skippedCount = runs.length - plottable.length;

  const { minTime, maxTime, yMax } = useMemo(() => {
    if (plottable.length === 0) {
      return { minTime: 0, maxTime: 0, yMax: 1 };
    }
    const times = plottable.map((r) => new Date(r.utc_start).getTime());
    const durations = plottable.map((r) => r.duration_ms as number);
    return {
      minTime: Math.min(...times),
      maxTime: Math.max(...times),
      yMax: niceCeil(Math.max(...durations)),
    };
  }, [plottable]);

  if (plottable.length === 0) {
    return <p className={styles.caption}>No runs with recorded duration to chart yet.</p>;
  }

  const dayRange = Math.max(1, (maxTime - minTime) / DAY_MS);
  const contentWidth = Math.max(MIN_PLOT_WIDTH, Math.ceil(dayRange * PX_PER_DAY));
  // Never narrower than the actual container (fills short ranges instead of leaving it mostly
  // blank), never narrower than the content needs either (long ranges still scroll, never squeeze).
  const plotWidth = Math.max(contentWidth, Math.ceil(containerWidth) - MARGIN.left - MARGIN.right);
  const totalWidth = plotWidth + MARGIN.left + MARGIN.right;
  const totalHeight = PLOT_HEIGHT + MARGIN.top + MARGIN.bottom;
  const timeSpan = maxTime - minTime || 1;

  const xScale = (t: number) => MARGIN.left + ((t - minTime) / timeSpan) * plotWidth;
  const yScale = (d: number) => MARGIN.top + PLOT_HEIGHT - (d / yMax) * PLOT_HEIGHT;

  const yTicks = Array.from({ length: Y_TICK_COUNT + 1 }, (_, i) => (yMax / Y_TICK_COUNT) * i);
  const xTicks = Array.from({ length: X_TICK_COUNT + 1 }, (_, i) => minTime + (timeSpan / X_TICK_COUNT) * i);

  const legendEntries = [runStatus(true, ''), runStatus(false, 'wipe'), runStatus(false, 'resign'), runStatus(false, 'unknown')];

  return (
    <div className={styles.wrapper}>
      <div className={styles.legend}>
        {legendEntries.map(({ kind, label, color }) => (
          <span key={kind} className={styles.legendItem}>
            <StatusIcon kind={kind} color={color} size={12} />
            {label}
          </span>
        ))}
      </div>

      <div className={styles.scrollArea} ref={scrollAreaRef}>
        <svg
          width={totalWidth}
          height={totalHeight}
          role="img"
          aria-label={`Run duration over time, ${plottable.length} runs plotted`}
        >
          {/* y gridlines + tick labels */}
          {yTicks.map((tick) => {
            const y = yScale(tick);
            return (
              <g key={tick}>
                <line x1={MARGIN.left} y1={y} x2={totalWidth - MARGIN.right} y2={y} className={styles.gridLine} />
                <text x={MARGIN.left - 8} y={y + 4} textAnchor="end" className={styles.tickLabel}>
                  {formatDuration(tick)}
                </text>
              </g>
            );
          })}

          {/* x axis + tick labels */}
          <line
            x1={MARGIN.left}
            y1={MARGIN.top + PLOT_HEIGHT}
            x2={totalWidth - MARGIN.right}
            y2={MARGIN.top + PLOT_HEIGHT}
            className={styles.axisLine}
          />
          {xTicks.map((tick) => {
            const x = xScale(tick);
            return (
              <g key={tick}>
                <line
                  x1={x}
                  y1={MARGIN.top + PLOT_HEIGHT}
                  x2={x}
                  y2={MARGIN.top + PLOT_HEIGHT + 6}
                  className={styles.axisLine}
                />
                <text x={x} y={MARGIN.top + PLOT_HEIGHT + 20} textAnchor="middle" className={styles.tickLabel}>
                  {new Date(tick).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}
                </text>
              </g>
            );
          })}

          {/* points */}
          {plottable.map((run) => {
            const cx = xScale(new Date(run.utc_start).getTime());
            const cy = yScale(run.duration_ms as number);
            const status = runStatus(run.completed, run.end_reason);
            const half = POINT_SIZE / 2;
            return (
              <g
                key={run.run_id}
                className={styles.point}
                tabIndex={0}
                role="link"
                aria-label={`${status.label} run on ${new Date(run.utc_start).toLocaleString()}, duration ${formatDuration(run.duration_ms)}`}
                onMouseEnter={() => setHovered({ run, cx, cy })}
                onMouseLeave={() => setHovered((h) => (h?.run.run_id === run.run_id ? null : h))}
                onFocus={() => setHovered({ run, cx, cy })}
                onBlur={() => setHovered((h) => (h?.run.run_id === run.run_id ? null : h))}
                onClick={() => navigate(`/runs/${run.run_id}`)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    navigate(`/runs/${run.run_id}`);
                  }
                }}
              >
                {/* transparent hit target, bigger than the mark (interaction.md) */}
                <circle cx={cx} cy={cy} r={HIT_RADIUS} fill="transparent" />
                {/* surface ring so the mark stays legible on overlap (marks-and-anatomy.md) */}
                <circle cx={cx} cy={cy} r={half + 2} fill="var(--gw-parchment)" />
                <svg x={cx - half} y={cy - half} width={POINT_SIZE} height={POINT_SIZE}>
                  <StatusIcon kind={status.kind} color={status.color} size={POINT_SIZE} />
                </svg>
              </g>
            );
          })}

          {/* tooltip — rendered in the same SVG coordinate space as the points, so it scrolls
              with the content instead of needing scroll-offset bookkeeping */}
          {hovered &&
            (() => {
              const status = runStatus(hovered.run.completed, hovered.run.end_reason);
              const boxWidth = 190;
              const boxHeight = 60;
              const boxX = Math.min(hovered.cx + 12, totalWidth - MARGIN.right - boxWidth);
              const boxY = Math.max(hovered.cy - boxHeight - 12, MARGIN.top);
              return (
                <g>
                  <rect x={boxX} y={boxY} width={boxWidth} height={boxHeight} rx={2} className={styles.tooltipBg} />
                  <text x={boxX + 10} y={boxY + 20} className={styles.tooltipValue}>
                    {formatDuration(hovered.run.duration_ms)}
                  </text>
                  <text x={boxX + 10} y={boxY + 36} className={styles.tooltipSecondary}>
                    {new Date(hovered.run.utc_start).toLocaleString()}
                  </text>
                  <text x={boxX + 10} y={boxY + 51} className={styles.tooltipSecondary}>
                    {status.label}
                  </text>
                </g>
              );
            })()}
        </svg>
      </div>

      {skippedCount > 0 && (
        <p className={styles.caption}>
          {skippedCount} run{skippedCount === 1 ? '' : 's'} without recorded duration not shown.
        </p>
      )}
    </div>
  );
}
