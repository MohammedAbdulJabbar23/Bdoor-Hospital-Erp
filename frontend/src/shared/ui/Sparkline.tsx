import { cn } from './cn';

type Series = {
  values: (number | null)[];
  /** Tailwind colour class for the line stroke. */
  color?: string;
  /** Optional label above the chart. */
  label?: string;
};

type Props = {
  series: Series[];
  width?: number;
  height?: number;
  /** Range hint for normality bands; renders a faint horizontal band when both are set. */
  normalRange?: [number, number];
  className?: string;
};

/**
 * Tiny inline SVG line chart for vital trends. Auto-scales Y based on data; renders one
 * polyline per series with a circle on each datapoint. Designed for 4–8 datapoints,
 * 60–80px wide. Null values break the line cleanly.
 */
export function Sparkline({ series, width = 80, height = 28, normalRange, className }: Props) {
  const allValues = series.flatMap((s) => s.values).filter((v): v is number => v != null);
  if (allValues.length === 0) {
    return <div className={cn('text-[10px] text-ink-400', className)}>—</div>;
  }
  const padded = padRange(Math.min(...allValues), Math.max(...allValues));
  const [minY, maxY] = padded;
  const span = Math.max(0.0001, maxY - minY);
  const n = series[0]?.values.length ?? 0;
  const stepX = n > 1 ? (width - 4) / (n - 1) : 0;

  const yFor = (v: number) => height - 2 - ((v - minY) / span) * (height - 4);

  // Normal-range band, if given
  let bandY1: number | undefined;
  let bandY2: number | undefined;
  if (normalRange && normalRange[0] < normalRange[1]) {
    const lo = Math.max(minY, normalRange[0]);
    const hi = Math.min(maxY, normalRange[1]);
    if (hi > lo) {
      bandY1 = yFor(hi);
      bandY2 = yFor(lo);
    }
  }

  return (
    <svg width={width} height={height} className={cn('block', className)} viewBox={`0 0 ${width} ${height}`}>
      {bandY1 != null && bandY2 != null && (
        <rect x={0} y={bandY1} width={width} height={Math.max(1, bandY2 - bandY1)} fill="currentColor" className="text-emerald-100" />
      )}
      {series.map((s, idx) => {
        const segments = buildSegments(s.values, stepX, yFor);
        const color = s.color ?? 'text-ink-700';
        return (
          <g key={idx} className={color}>
            {segments.map((seg, i) => (
              <polyline
                key={i}
                points={seg.map((p) => `${2 + p.x},${p.y}`).join(' ')}
                fill="none"
                stroke="currentColor"
                strokeWidth={1.5}
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            ))}
            {s.values.map((v, i) =>
              v == null ? null : <circle key={i} cx={2 + i * stepX} cy={yFor(v)} r={1.6} fill="currentColor" />
            )}
          </g>
        );
      })}
    </svg>
  );
}

function padRange(min: number, max: number): [number, number] {
  if (min === max) {
    const pad = Math.max(1, Math.abs(min) * 0.1);
    return [min - pad, max + pad];
  }
  const pad = (max - min) * 0.12;
  return [min - pad, max + pad];
}

function buildSegments(values: (number | null)[], stepX: number, yFor: (v: number) => number) {
  const segs: { x: number; y: number }[][] = [];
  let current: { x: number; y: number }[] = [];
  values.forEach((v, i) => {
    if (v == null) {
      if (current.length > 0) { segs.push(current); current = []; }
    } else {
      current.push({ x: i * stepX, y: yFor(v) });
    }
  });
  if (current.length > 0) segs.push(current);
  return segs;
}
