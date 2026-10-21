import { useMemo, useState } from 'react';
import { api } from '../lib/api';
import { Ball } from './Common';

const PAGE = 30;

/**
 * Ball-by-ball feed. The newest balls arrive in every pushed snapshot; older ones are paged in
 * from the REST API on demand. Both are merged by delivery id.
 */
export default function Commentary({ matchId, recentBalls, scorecards }) {
  const [older, setOlder] = useState([]);
  const [loading, setLoading] = useState(false);
  const [exhausted, setExhausted] = useState(false);

  const balls = useMemo(() => {
    const byId = new Map();
    for (const b of older) byId.set(b.id, b);
    for (const b of recentBalls || []) byId.set(b.id, b);
    return [...byId.values()].sort((a, b) => b.id - a.id);
  }, [older, recentBalls]);

  const overInfo = (ball) => {
    const card = scorecards.find((c) => c.number === ball.inningsNumber);
    const overNo = Number(ball.overLabel.split('.')[0]) + 1;
    const summary = card?.overSummaries.find((o) => o.over === overNo);
    return { overNo, summary, team: card?.battingTeam.shortName };
  };

  const loadMore = async () => {
    setLoading(true);
    try {
      let page = Math.floor(balls.length / PAGE);
      // Live balls shift pages while we read; keep going until something new turns up.
      for (let attempt = 0; attempt < 3; attempt++) {
        const res = await api.commentary(matchId, page, PAGE);
        const known = new Set(balls.map((b) => b.id));
        const fresh = res.items.filter((b) => !known.has(b.id));
        if (fresh.length > 0 || res.items.length === 0) {
          setOlder((prev) => [...prev, ...fresh]);
          if (page + 1 >= res.totalPages) setExhausted(true);
          break;
        }
        page++;
      }
    } finally {
      setLoading(false);
    }
  };

  if (balls.length === 0) {
    return <p className="muted center pad">No balls bowled yet. Commentary will appear here live.</p>;
  }

  const rows = [];
  let lastInnings = null;
  for (const b of balls) {
    if (lastInnings !== null && b.inningsNumber !== lastInnings) {
      rows.push(
        <li key={`inn-${b.id}`} className="innings-break">
          End of innings {b.inningsNumber}
        </li>,
      );
    }
    lastInnings = b.inningsNumber;
    if (b.endOfOver) {
      const { overNo, summary, team } = overInfo(b);
      rows.push(
        <li key={`over-${b.id}`} className="over-break">
          <span>End of over {overNo}</span>
          {summary && (
            <span>
              {summary.runs} run{summary.runs === 1 ? '' : 's'}
              {summary.wickets > 0 && ` · ${summary.wickets} wkt`}
            </span>
          )}
          <span>
            {team} {b.score}
          </span>
        </li>,
      );
    }
    const highlight = b.wicket ? ' c-wicket' : b.runsOffBat === 6 ? ' c-six' : b.runsOffBat === 4 ? ' c-four' : '';
    rows.push(
      <li key={b.id} className={`comm${highlight}`}>
        <div className="comm-over">{b.overLabel}</div>
        <Ball outcome={b.outcome} />
        <div className="comm-text">{b.commentary}</div>
      </li>,
    );
  }

  return (
    <div>
      <ul className="commentary">{rows}</ul>
      {!exhausted && (
        <div className="center pad">
          <button className="btn btn-ghost" onClick={loadMore} disabled={loading}>
            {loading ? 'Loading…' : 'Load older balls'}
          </button>
        </div>
      )}
    </div>
  );
}
