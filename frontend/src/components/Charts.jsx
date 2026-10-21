// Lightweight SVG charts (no charting library): Manhattan (runs per over) and Worm (cumulative runs).

const W = 640;
const H = 240;
const PAD = { l: 34, r: 12, t: 14, b: 28 };

function niceMax(v) {
  if (v <= 10) return 10;
  const step = v <= 30 ? 5 : v <= 100 ? 20 : v <= 250 ? 50 : 100;
  return Math.ceil(v / step) * step;
}

function Gridlines({ max, ticks = 4 }) {
  const lines = [];
  for (let i = 0; i <= ticks; i++) {
    const v = (max / ticks) * i;
    const y = H - PAD.b - ((H - PAD.t - PAD.b) * i) / ticks;
    lines.push(
      <g key={i}>
        <line x1={PAD.l} x2={W - PAD.r} y1={y} y2={y} className="gridline" />
        <text x={PAD.l - 6} y={y + 4} textAnchor="end" className="axis">
          {Math.round(v)}
        </text>
      </g>,
    );
  }
  return lines;
}

export function Manhattan({ scorecards, overs }) {
  const series = scorecards.map((c) => ({ color: c.battingTeam.color, name: c.battingTeam.shortName, data: c.overSummaries }));
  const maxRuns = niceMax(Math.max(6, ...series.flatMap((s) => s.data.map((o) => o.runs))));
  const plotW = W - PAD.l - PAD.r;
  const plotH = H - PAD.t - PAD.b;
  const slot = plotW / overs;
  const barW = Math.max(2, (slot * 0.8) / Math.max(1, series.length));
  const labelEvery = overs > 20 ? 5 : overs > 10 ? 2 : 1;

  return (
    <figure className="chart">
      <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Runs per over">
        <Gridlines max={maxRuns} />
        {series.map((s, si) =>
          s.data.map((o) => {
            const h = (o.runs / maxRuns) * plotH;
            const x = PAD.l + (o.over - 1) * slot + slot * 0.1 + si * barW;
            const y = H - PAD.b - h;
            return (
              <g key={`${si}-${o.over}`}>
                <rect x={x} y={y} width={barW - 1} height={Math.max(0.5, h)} fill={s.color} rx="1.5">
                  <title>{`${s.name} over ${o.over}: ${o.runs} runs${o.wickets ? `, ${o.wickets} wkt` : ''} (${o.bowlerName})`}</title>
                </rect>
                {Array.from({ length: o.wickets }).map((_, wi) => (
                  <circle key={wi} cx={x + (barW - 1) / 2} cy={y - 6 - wi * 9} r="3.6" className="wkt-dot" />
                ))}
              </g>
            );
          }),
        )}
        {Array.from({ length: overs }).map((_, i) =>
          (i + 1) % labelEvery === 0 || i === 0 ? (
            <text key={i} x={PAD.l + i * slot + slot / 2} y={H - 8} textAnchor="middle" className="axis">
              {i + 1}
            </text>
          ) : null,
        )}
      </svg>
      <Legend series={series} extra="● wicket" />
    </figure>
  );
}

export function Worm({ scorecards, overs }) {
  const series = scorecards.map((c) => ({
    color: c.battingTeam.color,
    name: c.battingTeam.shortName,
    data: c.overSummaries,
    target: c.target,
  }));
  const maxRuns = niceMax(Math.max(20, ...series.flatMap((s) => s.data.map((o) => o.cumulativeRuns)), ...series.map((s) => s.target || 0)));
  const plotW = W - PAD.l - PAD.r;
  const plotH = H - PAD.t - PAD.b;
  const x = (over) => PAD.l + (over / overs) * plotW;
  const y = (runs) => H - PAD.b - (runs / maxRuns) * plotH;
  const labelEvery = overs > 20 ? 10 : 5;

  return (
    <figure className="chart">
      <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Cumulative runs">
        <Gridlines max={maxRuns} />
        {series.map((s, si) => {
          if (s.data.length === 0) return null;
          const pts = [[x(0), y(0)], ...s.data.map((o) => [x(o.over), y(o.cumulativeRuns)])];
          return (
            <g key={si}>
              {s.target && (
                <line x1={PAD.l} x2={W - PAD.r} y1={y(s.target)} y2={y(s.target)} className="target-line">
                  <title>{`Target ${s.target}`}</title>
                </line>
              )}
              <polyline points={pts.map((p) => p.join(',')).join(' ')} fill="none" stroke={s.color} strokeWidth="2.5" strokeLinejoin="round" />
              {s.data
                .filter((o) => o.wickets > 0)
                .map((o) => (
                  <circle key={o.over} cx={x(o.over)} cy={y(o.cumulativeRuns)} r="4" fill={s.color} className="wkt-ring">
                    <title>{`${s.name} ${o.cumulativeRuns} after ${o.over} overs, ${o.wickets} wkt`}</title>
                  </circle>
                ))}
            </g>
          );
        })}
        {Array.from({ length: Math.floor(overs / labelEvery) + 1 }).map((_, i) => (
          <text key={i} x={x(i * labelEvery)} y={H - 8} textAnchor="middle" className="axis">
            {i * labelEvery}
          </text>
        ))}
      </svg>
      <Legend series={series} extra={series.some((s) => s.target) ? '- - target' : null} />
    </figure>
  );
}

function Legend({ series, extra }) {
  return (
    <figcaption className="legend">
      {series.map((s) => (
        <span key={s.name}>
          <i style={{ background: s.color }} /> {s.name}
        </span>
      ))}
      {extra && <span className="muted">{extra}</span>}
    </figcaption>
  );
}
