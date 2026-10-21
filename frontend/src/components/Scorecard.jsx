import { Link } from 'react-router-dom';
import { fmt } from '../lib/format';

export default function Scorecard({ card }) {
  return (
    <div className="scorecard">
      <div className="scorecard-head" style={{ borderColor: card.battingTeam.color }}>
        <h3>{card.battingTeam.name}</h3>
        <div className="scorecard-total">
          {card.runs}/{card.wickets} <small>({card.overs} ov, RR {fmt(card.runRate)})</small>
        </div>
      </div>

      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th className="left">Batter</th>
              <th className="left hide-sm" />
              <th>R</th>
              <th>B</th>
              <th>4s</th>
              <th>6s</th>
              <th>SR</th>
            </tr>
          </thead>
          <tbody>
            {card.batting.map((b) => (
              <tr key={b.playerId} className={b.atCrease ? 'at-crease' : ''}>
                <td className="left">
                  <Link to={`/players/${b.playerId}`}>{b.name}</Link>
                  {b.onStrike && <span className="strike">*</span>}
                  <div className="dismissal show-sm">{b.dismissal}</div>
                </td>
                <td className="left dismissal hide-sm">{b.dismissal}</td>
                <td>
                  <b>{b.runs}</b>
                </td>
                <td>{b.balls}</td>
                <td>{b.fours}</td>
                <td>{b.sixes}</td>
                <td>{fmt(b.strikeRate, 1)}</td>
              </tr>
            ))}
            <tr className="sub">
              <td className="left">Extras</td>
              <td className="left hide-sm muted small">
                (wd {card.extras.wides}, nb {card.extras.noBalls}, b {card.extras.byes}, lb {card.extras.legByes})
              </td>
              <td>
                <b>{card.extras.total}</b>
              </td>
              <td colSpan={4} />
            </tr>
            <tr className="sub total">
              <td className="left">Total</td>
              <td className="left hide-sm muted small">
                {card.wickets} wkts, {card.overs} overs
              </td>
              <td>
                <b>{card.runs}</b>
              </td>
              <td colSpan={4} />
            </tr>
          </tbody>
        </table>
      </div>

      {card.didNotBat?.length > 0 && (
        <p className="small">
          <span className="muted">{card.completed ? 'Did not bat: ' : 'Yet to bat: '}</span>
          {card.didNotBat.map((p, i) => (
            <span key={p.id}>
              {i > 0 && ', '}
              <Link to={`/players/${p.id}`}>{p.name}</Link>
            </span>
          ))}
        </p>
      )}

      {card.fallOfWickets?.length > 0 && (
        <p className="small fow">
          <span className="muted">Fall of wickets: </span>
          {card.fallOfWickets.map((f, i) => (
            <span key={f.wicket}>
              {i > 0 && ' · '}
              {f.score}-{f.wicket} ({f.playerName}, {f.over})
            </span>
          ))}
        </p>
      )}

      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th className="left">Bowler</th>
              <th>O</th>
              <th>M</th>
              <th>R</th>
              <th>W</th>
              <th>Econ</th>
              <th className="hide-sm">0s</th>
              <th className="hide-sm">WD</th>
              <th className="hide-sm">NB</th>
            </tr>
          </thead>
          <tbody>
            {card.bowling.map((b) => (
              <tr key={b.playerId} className={b.bowling ? 'at-crease' : ''}>
                <td className="left">
                  <Link to={`/players/${b.playerId}`}>{b.name}</Link>
                  {b.bowling && <span className="strike">*</span>}
                </td>
                <td>{b.overs}</td>
                <td>{b.maidens}</td>
                <td>{b.runs}</td>
                <td>
                  <b>{b.wickets}</b>
                </td>
                <td>{fmt(b.economy)}</td>
                <td className="hide-sm">{b.dots}</td>
                <td className="hide-sm">{b.wides}</td>
                <td className="hide-sm">{b.noBalls}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
