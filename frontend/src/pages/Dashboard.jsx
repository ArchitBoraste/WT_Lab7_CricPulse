import { Link } from 'react-router-dom';
import MatchCard from '../components/MatchCard';
import { ErrorBox, Spinner, TeamDot } from '../components/Common';
import { api } from '../lib/api';
import { useDashboard, usePolled } from '../lib/live';

function Overview() {
  const o = usePolled(api.overview, 8000);
  if (!o) return null;
  const items = [
    ['Live matches', o.liveMatches],
    ['Balls bowled', o.deliveries.toLocaleString()],
    ['Runs scored', o.runs.toLocaleString()],
    ['Fours', o.fours],
    ['Sixes', o.sixes],
    ['Wickets', o.wickets],
  ];
  return (
    <div className="overview">
      {items.map(([label, value]) => (
        <div key={label} className="stat-tile">
          <div className="stat-value">{value}</div>
          <div className="stat-label">{label}</div>
        </div>
      ))}
    </div>
  );
}

function Leaders() {
  const lb = usePolled(() => api.leaderboard(5), 10000);
  if (!lb) return null;
  const list = (title, rows, value, unit) => (
    <div className="card leaders">
      <h3>{title}</h3>
      <ol>
        {rows.map((r) => (
          <li key={r.playerId}>
            <TeamDot color={r.teamColor} />
            <Link to={`/players/${r.playerId}`}>{r.name}</Link>
            <span className="muted small">{r.team}</span>
            <b>
              {value(r)}
              <small> {unit}</small>
            </b>
          </li>
        ))}
      </ol>
    </div>
  );
  return (
    <>
      {list('Most runs', lb.mostRuns, (r) => r.runs, 'runs')}
      {list('Most wickets', lb.mostWickets, (r) => r.wickets, 'wkts')}
      <Link to="/stats" className="btn btn-ghost block">
        All leaderboards →
      </Link>
    </>
  );
}

export default function Dashboard() {
  const { data, error } = useDashboard();

  return (
    <div className="page">
      <section className="hero">
        <div>
          <h1>Live cricket, ball by ball</h1>
          <p className="muted">Scores update the moment each delivery is bowled. Click a match for the full scorecard and commentary.</p>
        </div>
        <Overview />
      </section>

      <ErrorBox message={error && `Could not reach the server: ${error}`} />
      {!data && !error && <Spinner label="Fetching matches" />}

      {data && (
        <div className="dash-layout">
          <div className="dash-main">
            <section>
              <h2 className="section-title">
                <span className="pulse" /> Live now <span className="count">{data.live.length}</span>
              </h2>
              {data.live.length === 0 ? (
                <p className="muted card pad">No matches in progress. The next fixture starts soon.</p>
              ) : (
                <div className="grid cards">
                  {data.live.map((m) => (
                    <MatchCard key={m.id} summary={m} />
                  ))}
                </div>
              )}
            </section>

            <section>
              <h2 className="section-title">Upcoming</h2>
              <div className="grid cards">
                {data.upcoming.map((m) => (
                  <MatchCard key={m.id} summary={m} />
                ))}
              </div>
            </section>

            <section>
              <h2 className="section-title">Recent results</h2>
              <div className="grid cards">
                {data.recent.map((m) => (
                  <MatchCard key={m.id} summary={m} />
                ))}
              </div>
            </section>
          </div>
          <aside className="dash-side">
            <Leaders />
          </aside>
        </div>
      )}
    </div>
  );
}
