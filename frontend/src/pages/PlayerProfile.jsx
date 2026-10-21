import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ErrorBox, Spinner, TeamDot } from '../components/Common';
import { api } from '../lib/api';
import { fmt, formatDate } from '../lib/format';

function Tiles({ items }) {
  return (
    <div className="tiles">
      {items.map(([label, value]) => (
        <div key={label} className="stat-tile">
          <div className="stat-value">{value}</div>
          <div className="stat-label">{label}</div>
        </div>
      ))}
    </div>
  );
}

export default function PlayerProfile() {
  const { id } = useParams();
  const [profile, setProfile] = useState(null);
  const [error, setError] = useState(null);

  // Stats change with every ball the player is involved in, so refresh periodically.
  useEffect(() => {
    let alive = true;
    const run = () =>
      api.player(id).then(
        (p) => alive && (setProfile(p), setError(null)),
        (e) => alive && setError(e.message),
      );
    setProfile(null);
    run();
    const t = setInterval(run, 10000);
    return () => {
      alive = false;
      clearInterval(t);
    };
  }, [id]);

  if (error && !profile) return <div className="page"><ErrorBox message={error} /></div>;
  if (!profile) return <div className="page"><Spinner label="Loading player" /></div>;

  const { player, batting, bowling, bestBowling, matches, recent } = profile;
  return (
    <div className="page">
      <Link to="/stats" className="back">← Statistics</Link>
      <div className="card player-head" style={{ borderColor: player.teamColor }}>
        <div className="avatar" style={{ background: player.teamColor }}>
          {player.name.split(' ').map((s) => s[0]).join('')}
        </div>
        <div>
          <h1>{player.name}</h1>
          <div className="muted">
            <TeamDot color={player.teamColor} /> {player.teamShort} · {player.role.replace('_', ' ').toLowerCase()} ·{' '}
            {player.battingStyle}
            {player.bowlingStyle && ` · ${player.bowlingStyle}`}
          </div>
        </div>
        <div className="player-matches">
          <b>{matches}</b>
          <span className="muted small">matches</span>
        </div>
      </div>

      <div className="profile-grid">
        <div className="card">
          <h3>Batting</h3>
          {batting ? (
            <Tiles
              items={[
                ['Innings', batting.innings],
                ['Runs', batting.runs],
                ['Highest', batting.highest],
                ['Average', fmt(batting.average)],
                ['Strike rate', fmt(batting.strikeRate)],
                ['4s / 6s', `${batting.fours} / ${batting.sixes}`],
                ['50s / 100s', `${batting.fifties} / ${batting.hundreds}`],
                ['Not outs', batting.notOuts],
              ]}
            />
          ) : (
            <p className="muted">Has not batted yet.</p>
          )}
        </div>
        <div className="card">
          <h3>Bowling</h3>
          {bowling ? (
            <Tiles
              items={[
                ['Innings', bowling.innings],
                ['Overs', bowling.overs],
                ['Wickets', bowling.wickets],
                ['Best', bestBowling],
                ['Economy', fmt(bowling.economy)],
                ['Average', fmt(bowling.average)],
                ['Dot balls', bowling.dots],
                ['3W+', bowling.threeWicketHauls],
              ]}
            />
          ) : (
            <p className="muted">Has not bowled yet.</p>
          )}
        </div>
      </div>

      <div className="card">
        <h3>Recent performances</h3>
        {recent.length === 0 ? (
          <p className="muted">No matches yet.</p>
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th className="left">Match</th>
                  <th className="left">Date</th>
                  <th>Batting</th>
                  <th>Bowling</th>
                </tr>
              </thead>
              <tbody>
                {recent.map((r) => (
                  <tr key={r.matchId}>
                    <td className="left">
                      <Link to={`/match/${r.matchId}`}>vs {r.opponent}</Link>
                    </td>
                    <td className="left muted">{formatDate(r.date)}</td>
                    <td>{r.batting ?? '–'}</td>
                    <td>{r.bowling ?? '–'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
