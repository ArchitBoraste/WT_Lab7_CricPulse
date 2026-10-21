import { Link } from 'react-router-dom';
import { fmt } from '../lib/format';
import { BallStrip } from './Common';

/** At-the-crease view: batters, current bowler, partnership and this over. */
export default function LivePanel({ live, summary }) {
  if (!live) return null;
  const batters = [live.striker, live.nonStriker].filter(Boolean);
  const b = live.bowler;

  return (
    <div className="card live-panel">
      <div className="live-grid">
        <div>
          <table className="table compact">
            <thead>
              <tr>
                <th className="left">Batting</th>
                <th>R</th>
                <th>B</th>
                <th>4s</th>
                <th>6s</th>
                <th>SR</th>
              </tr>
            </thead>
            <tbody>
              {batters.map((p) => (
                <tr key={p.playerId} className={p.onStrike ? 'on-strike' : ''}>
                  <td className="left">
                    <Link to={`/players/${p.playerId}`}>{p.name}</Link>
                    {p.onStrike && <span className="strike">*</span>}
                  </td>
                  <td>
                    <b>{p.runs}</b>
                  </td>
                  <td>{p.balls}</td>
                  <td>{p.fours}</td>
                  <td>{p.sixes}</td>
                  <td>{fmt(p.strikeRate, 1)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <table className="table compact">
            <thead>
              <tr>
                <th className="left">Bowling</th>
                <th>O</th>
                <th>M</th>
                <th>R</th>
                <th>W</th>
                <th>Econ</th>
              </tr>
            </thead>
            <tbody>
              {b ? (
                <tr>
                  <td className="left">
                    <Link to={`/players/${b.playerId}`}>{b.name}</Link>
                    {!b.bowling && <span className="muted small"> (last over)</span>}
                  </td>
                  <td>{b.overs}</td>
                  <td>{b.maidens}</td>
                  <td>{b.runs}</td>
                  <td>
                    <b>{b.wickets}</b>
                  </td>
                  <td>{fmt(b.economy)}</td>
                </tr>
              ) : (
                <tr>
                  <td className="left muted" colSpan={6}>
                    New bowler coming on
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <div className="live-side">
          <div className="kv">
            <span>Partnership</span>
            <b>
              {live.partnershipRuns} ({live.partnershipBalls})
            </b>
          </div>
          <div className="kv">
            <span>Current RR</span>
            <b>{fmt(summary.currentRunRate)}</b>
          </div>
          {live.runsNeeded != null ? (
            <>
              <div className="kv">
                <span>Required RR</span>
                <b>{fmt(summary.requiredRunRate)}</b>
              </div>
              <div className="kv">
                <span>Need</span>
                <b>
                  {live.runsNeeded} off {live.ballsRemaining}
                </b>
              </div>
            </>
          ) : (
            <div className="kv">
              <span>Projected</span>
              <b>{live.projectedScore}</b>
            </div>
          )}
          <div className="this-over">
            <span className="muted small">{live.needsBowler ? 'Last over' : 'This over'}</span>
            <BallStrip balls={live.thisOver} size="lg" empty={<span className="muted small">—</span>} />
          </div>
          {live.lastWicket && (
            <div className="small">
              <span className="muted">Last wicket: </span>
              {live.lastWicket}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
