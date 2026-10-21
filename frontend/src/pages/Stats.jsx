import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Spinner, Tabs, TeamDot } from '../components/Common';
import { api } from '../lib/api';
import { fmt } from '../lib/format';
import { usePolled } from '../lib/live';

const BOARDS = {
  mostRuns: {
    label: 'Most runs',
    cols: [
      ['Inn', (r) => r.innings],
      ['Runs', (r) => <b>{r.runs}</b>],
      ['HS', (r) => r.highest],
      ['Avg', (r) => fmt(r.average)],
      ['SR', (r) => fmt(r.strikeRate)],
      ['50/100', (r) => `${r.fifties}/${r.hundreds}`],
    ],
  },
  mostWickets: {
    label: 'Most wickets',
    cols: [
      ['Inn', (r) => r.innings],
      ['Overs', (r) => r.overs],
      ['Wkts', (r) => <b>{r.wickets}</b>],
      ['Econ', (r) => fmt(r.economy)],
      ['Avg', (r) => fmt(r.average)],
      ['3W+', (r) => r.threeWicketHauls],
    ],
  },
  bestStrikeRate: {
    label: 'Strike rate',
    note: 'Minimum 30 balls faced',
    cols: [
      ['Runs', (r) => r.runs],
      ['Balls', (r) => r.balls],
      ['SR', (r) => <b>{fmt(r.strikeRate)}</b>],
      ['4s', (r) => r.fours],
      ['6s', (r) => r.sixes],
    ],
  },
  mostSixes: {
    label: 'Most sixes',
    cols: [
      ['Inn', (r) => r.innings],
      ['6s', (r) => <b>{r.sixes}</b>],
      ['4s', (r) => r.fours],
      ['Runs', (r) => r.runs],
      ['SR', (r) => fmt(r.strikeRate)],
    ],
  },
  bestEconomy: {
    label: 'Economy',
    note: 'Minimum 6 overs bowled',
    cols: [
      ['Overs', (r) => r.overs],
      ['Runs', (r) => r.runs],
      ['Wkts', (r) => r.wickets],
      ['Econ', (r) => <b>{fmt(r.economy)}</b>],
      ['Dots', (r) => r.dots],
    ],
  },
};

function PlayerSearch() {
  const [q, setQ] = useState('');
  const [teamId, setTeamId] = useState('');
  const [teams, setTeams] = useState([]);
  const [players, setPlayers] = useState(null);

  useEffect(() => {
    api.teams().then(setTeams);
  }, []);
  useEffect(() => {
    const t = setTimeout(() => api.players(q, teamId).then(setPlayers), 200);
    return () => clearTimeout(t);
  }, [q, teamId]);

  return (
    <div className="card">
      <h3>Find a player</h3>
      <div className="form-row">
        <input className="input" placeholder="Search by name…" value={q} onChange={(e) => setQ(e.target.value)} />
        <select className="input" value={teamId} onChange={(e) => setTeamId(e.target.value)}>
          <option value="">All teams</option>
          {teams.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </select>
      </div>
      {!players ? (
        <Spinner />
      ) : (
        <ul className="player-list">
          {players.slice(0, 60).map((p) => (
            <li key={p.id}>
              <TeamDot color={p.teamColor} />
              <Link to={`/players/${p.id}`}>{p.name}</Link>
              <span className="muted small">
                {p.teamShort} · {p.role.replace('_', ' ').toLowerCase()}
              </span>
            </li>
          ))}
          {players.length === 0 && <li className="muted">No players found.</li>}
        </ul>
      )}
    </div>
  );
}

export default function Stats() {
  const [board, setBoard] = useState('mostRuns');
  const lb = usePolled(() => api.leaderboard(15), 8000);
  const def = BOARDS[board];

  return (
    <div className="page">
      <h1>Statistics</h1>
      <p className="muted">Career numbers across every match on the platform, including balls bowled moments ago.</p>
      <div className="stats-layout">
        <div className="card">
          <Tabs value={board} onChange={setBoard} tabs={Object.entries(BOARDS).map(([value, b]) => ({ value, label: b.label }))} />
          {def.note && <p className="muted small">{def.note}</p>}
          {!lb ? (
            <Spinner />
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>#</th>
                    <th className="left">Player</th>
                    {def.cols.map(([h]) => (
                      <th key={h}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {lb[board].map((r, i) => (
                    <tr key={r.playerId}>
                      <td className="muted">{i + 1}</td>
                      <td className="left">
                        <TeamDot color={r.teamColor} />
                        <Link to={`/players/${r.playerId}`}>{r.name}</Link> <span className="muted small">{r.team}</span>
                      </td>
                      {def.cols.map(([h, f]) => (
                        <td key={h}>{f(r)}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
        <PlayerSearch />
      </div>
    </div>
  );
}
