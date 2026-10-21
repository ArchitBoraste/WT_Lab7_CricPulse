import { useEffect, useState } from 'react';
import { ballClass, ballText } from '../lib/format';

export function Ball({ outcome, size }) {
  return <span className={`${ballClass(outcome)}${size === 'lg' ? ' ball-lg' : ''}`}>{ballText(outcome)}</span>;
}

export function BallStrip({ balls, size, empty = null }) {
  if (!balls || balls.length === 0) return empty;
  return (
    <div className="ball-strip">
      {balls.map((b, i) => (
        <Ball key={i} outcome={b} size={size} />
      ))}
    </div>
  );
}

export function TeamDot({ color }) {
  return <span className="team-dot" style={{ background: color }} />;
}

export function LiveBadge({ paused }) {
  return paused ? <span className="badge badge-paused">PAUSED</span> : <span className="badge badge-live">LIVE</span>;
}

export function StatusBadge({ summary }) {
  if (summary.status === 'LIVE') return <LiveBadge paused={summary.paused} />;
  if (summary.status === 'SCHEDULED') return <span className="badge badge-upcoming">UPCOMING</span>;
  return <span className="badge badge-done">RESULT</span>;
}

/** Horizontal win-probability bar in the two team colours. */
export function WinBar({ summary }) {
  const wp = summary.winProbability;
  if (!wp) return null;
  const { teamA, teamB } = summary;
  return (
    <div className="winbar" title="Win probability (heuristic estimate)">
      <div className="winbar-labels">
        <span>
          {teamA.shortName} <b>{wp.teamAPercent}%</b>
        </span>
        <span className="muted small">Win probability</span>
        <span>
          <b>{wp.teamBPercent}%</b> {teamB.shortName}
        </span>
      </div>
      <div className="winbar-track">
        <div style={{ width: `${wp.teamAPercent}%`, background: teamA.color }} />
        <div style={{ width: `${wp.teamBPercent}%`, background: teamB.color }} />
      </div>
    </div>
  );
}

export function useNow(ms = 1000) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), ms);
    return () => clearInterval(t);
  }, [ms]);
  return now;
}

export function Spinner({ label = 'Loading' }) {
  return (
    <div className="spinner-wrap">
      <div className="spinner" />
      <span className="muted">{label}…</span>
    </div>
  );
}

export function ErrorBox({ message }) {
  if (!message) return null;
  return <div className="error-box">{message}</div>;
}

export function Tabs({ tabs, value, onChange }) {
  return (
    <div className="tabs" role="tablist">
      {tabs.map((t) => (
        <button
          key={t.value}
          role="tab"
          aria-selected={value === t.value}
          className={`tab${value === t.value ? ' active' : ''}`}
          onClick={() => onChange(t.value)}
        >
          {t.label}
        </button>
      ))}
    </div>
  );
}
