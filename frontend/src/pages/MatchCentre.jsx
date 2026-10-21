import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import Commentary from '../components/Commentary';
import LivePanel from '../components/LivePanel';
import Scorecard from '../components/Scorecard';
import { Manhattan, Worm } from '../components/Charts';
import { ErrorBox, Spinner, StatusBadge, Tabs, TeamDot, WinBar, useNow } from '../components/Common';
import { countdown, formatTime, inningsFor } from '../lib/format';
import { useMatch } from '../lib/live';

/** Big animated banner for boundaries and wickets. */
function EventBanner({ latest }) {
  const [event, setEvent] = useState(null);
  const lastId = useRef(latest?.id);
  useEffect(() => {
    if (!latest || latest.id === lastId.current) return undefined;
    lastId.current = latest.id;
    const kind = latest.wicket ? 'WICKET!' : latest.runsOffBat === 6 ? 'SIX!' : latest.runsOffBat === 4 ? 'FOUR!' : null;
    if (!kind) return undefined;
    setEvent({ kind, text: latest.wicket ? latest.batter : `${latest.batter} · ${latest.bowler}`, key: latest.id });
    const t = setTimeout(() => setEvent(null), 2600);
    return () => clearTimeout(t);
  }, [latest]);
  if (!event) return null;
  const cls = event.kind === 'WICKET!' ? 'ev-w' : event.kind === 'SIX!' ? 'ev-6' : 'ev-4';
  return (
    <div key={event.key} className={`event-banner ${cls}`} role="status">
      <strong>{event.kind}</strong>
      <span>{event.text}</span>
    </div>
  );
}

function ScoreHeader({ summary }) {
  const now = useNow(1000);
  const side = (team) => {
    const inns = inningsFor(summary, team.id);
    const batting = summary.status === 'LIVE' && inns.some((i) => i.number === summary.currentInnings);
    return (
      <div className={`sh-team${batting ? ' batting' : ''}${summary.winnerId === team.id ? ' won' : ''}`}>
        <div className="sh-name">
          <TeamDot color={team.color} /> <span className="full">{team.name}</span>
          <span className="short">{team.shortName}</span>
        </div>
        <div className="sh-score">
          {inns.length === 0 ? (
            <span className="muted">{summary.status === 'SCHEDULED' ? '' : 'Yet to bat'}</span>
          ) : (
            inns.map((i) => (
              <span key={i.number}>
                {i.runs}/{i.wickets}
                <small> ({i.overs})</small>
              </span>
            ))
          )}
        </div>
      </div>
    );
  };

  return (
    <div className="card score-header" style={{ '--team-a': summary.teamA.color, '--team-b': summary.teamB.color }}>
      <div className="sh-meta">
        <StatusBadge summary={summary} />
        <span className="muted small">
          {summary.format} · {summary.venue} · {formatTime(summary.startTime)}
        </span>
      </div>
      <div className="sh-teams">
        {side(summary.teamA)}
        <span className="vs">vs</span>
        {side(summary.teamB)}
      </div>
      <div className={`sh-status${summary.status === 'COMPLETED' ? ' result' : ''}`}>
        {summary.status === 'SCHEDULED' ? `Match starts ${countdown(summary.startTime, now)}` : summary.statusText}
      </div>
      {summary.tossText && summary.status !== 'SCHEDULED' && <div className="muted small center">{summary.tossText}</div>}
      {summary.status === 'LIVE' && <WinBar summary={summary} />}
    </div>
  );
}

export default function MatchCentre() {
  const { id } = useParams();
  const { match, error } = useMatch(id);
  const [tab, setTab] = useState('live');
  const [inningsTab, setInningsTab] = useState(null);

  if (error && !match) return <div className="page"><ErrorBox message={error} /></div>;
  if (!match) return <div className="page"><Spinner label="Loading match" /></div>;

  const { summary, live, scorecards, recentBalls, playerOfMatch } = match;
  const selectedInnings = inningsTab ?? (scorecards.length ? scorecards[scorecards.length - 1].number : null);
  const card = scorecards.find((c) => c.number === selectedInnings);

  return (
    <div className="page match-centre">
      <Link to="/" className="back">← All matches</Link>
      <ScoreHeader summary={summary} />
      <EventBanner latest={recentBalls?.[0]} />

      {playerOfMatch && (
        <div className="card potm">
          <span className="muted small">Player of the match</span>
          <Link to={`/players/${playerOfMatch.id}`}>{playerOfMatch.name}</Link>
          <span className="muted small">{playerOfMatch.teamShort}</span>
        </div>
      )}

      <LivePanel live={live} summary={summary} />

      {summary.status !== 'SCHEDULED' && (
        <>
          <Tabs
            value={tab}
            onChange={setTab}
            tabs={[
              { value: 'live', label: 'Commentary' },
              { value: 'scorecard', label: 'Scorecard' },
              { value: 'analysis', label: 'Analysis' },
            ]}
          />
          <div className="card tab-body">
            {tab === 'live' && <Commentary matchId={summary.id} recentBalls={recentBalls} scorecards={scorecards} />}
            {tab === 'scorecard' && (
              <>
                <Tabs
                  value={selectedInnings}
                  onChange={setInningsTab}
                  tabs={scorecards.map((c) => ({ value: c.number, label: `${c.battingTeam.shortName} ${c.runs}/${c.wickets}` }))}
                />
                {card && <Scorecard card={card} />}
              </>
            )}
            {tab === 'analysis' && (
              <div className="analysis">
                <h3>Runs per over</h3>
                <Manhattan scorecards={scorecards} overs={summary.oversPerInnings} />
                <h3>Run progression</h3>
                <Worm scorecards={scorecards} overs={summary.oversPerInnings} />
              </div>
            )}
          </div>
        </>
      )}
      {summary.status === 'SCHEDULED' && (
        <p className="card pad muted center">
          Squads are ready. The toss happens at the scheduled start, or start it now from the{' '}
          <Link to={`/control?match=${summary.id}`}>Control Room</Link>.
        </p>
      )}
    </div>
  );
}
