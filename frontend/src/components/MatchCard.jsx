import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { countdown, fmt, formatDate, formatTime, inningsFor, scoreText } from '../lib/format';
import { BallStrip, StatusBadge, TeamDot, WinBar, useNow } from './Common';

function TeamRow({ team, summary }) {
  const innings = inningsFor(summary, team.id);
  const batting =
    summary.status === 'LIVE' && innings.some((i) => i.number === summary.currentInnings && !i.completed);
  const won = summary.winnerId === team.id;
  return (
    <div className={`team-row${batting ? ' batting' : ''}${won ? ' won' : ''}`}>
      <TeamDot color={team.color} />
      <span className="team-name">{team.name}</span>
      <span className="team-score">
        {innings.map((i) => (
          <span key={i.number}>
            {scoreText(i)} <small>({i.overs})</small>
          </span>
        ))}
        {innings.length === 0 && summary.status !== 'SCHEDULED' && <small className="muted">Yet to bat</small>}
      </span>
    </div>
  );
}

/** Flashes the card when a boundary or wicket arrives. */
function useEventFlash(summary) {
  const [flash, setFlash] = useState('');
  const last = useRef(summary.lastUpdated);
  useEffect(() => {
    if (last.current === summary.lastUpdated) return undefined;
    last.current = summary.lastUpdated;
    const latest = summary.recentBalls?.[summary.recentBalls.length - 1];
    const kind = latest === 'W' ? 'flash-w' : latest === '6' ? 'flash-6' : latest === '4' ? 'flash-4' : 'flash-tick';
    setFlash(kind);
    const t = setTimeout(() => setFlash(''), 1200);
    return () => clearTimeout(t);
  }, [summary.lastUpdated, summary.recentBalls]);
  return flash;
}

export default function MatchCard({ summary }) {
  const now = useNow(1000);
  const flash = useEventFlash(summary);
  const live = summary.status === 'LIVE';

  return (
    <Link
      to={`/match/${summary.id}`}
      className={`card match-card ${flash}`}
      style={{ '--team-a': summary.teamA.color, '--team-b': summary.teamB.color }}
    >
      <div className="match-card-head">
        <StatusBadge summary={summary} />
        <span className="muted small">
          {summary.format} · {summary.venue}
        </span>
      </div>
      <TeamRow team={summary.teamA} summary={summary} />
      <TeamRow team={summary.teamB} summary={summary} />

      {summary.status === 'SCHEDULED' ? (
        <div className="status-line">
          <span>{formatTime(summary.startTime)}</span>
          <span className="countdown">{countdown(summary.startTime, now)}</span>
        </div>
      ) : (
        <div className={`status-line${summary.status === 'COMPLETED' ? ' result' : ''}`}>{summary.statusText}</div>
      )}

      {live && (
        <>
          <div className="rates">
            <span>
              CRR <b>{fmt(summary.currentRunRate)}</b>
            </span>
            {summary.requiredRunRate != null && (
              <span>
                RRR <b>{fmt(summary.requiredRunRate)}</b>
              </span>
            )}
            <BallStrip balls={summary.recentBalls} />
          </div>
          <WinBar summary={summary} />
        </>
      )}
      {summary.status === 'COMPLETED' && (
        <div className="muted small">{formatDate(summary.completedAt || summary.startTime)}</div>
      )}
    </Link>
  );
}
