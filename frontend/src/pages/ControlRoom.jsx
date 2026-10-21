import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Ball, BallStrip, ErrorBox, Spinner, StatusBadge } from '../components/Common';
import { api } from '../lib/api';
import { useDashboard, useMatch } from '../lib/live';

const EXTRAS = [
  ['NONE', 'Legal'],
  ['WIDE', 'Wide'],
  ['NO_BALL', 'No ball'],
  ['BYE', 'Bye'],
  ['LEG_BYE', 'Leg bye'],
];
const WICKETS_FOR = {
  NONE: ['BOWLED', 'CAUGHT', 'LBW', 'RUN_OUT', 'STUMPED', 'HIT_WICKET'],
  WIDE: ['RUN_OUT', 'STUMPED'],
  NO_BALL: ['RUN_OUT'],
  BYE: ['RUN_OUT'],
  LEG_BYE: ['RUN_OUT'],
};
const label = (s) => s.replace('_', ' ').toLowerCase();

function CreateMatchForm({ onCreated }) {
  const [teams, setTeams] = useState([]);
  const [form, setForm] = useState({ teamAId: '', teamBId: '', format: 'T20', venue: '', delay: 0, mode: 'auto' });
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.teams().then((t) => {
      setTeams(t);
      if (t.length >= 2) setForm((f) => ({ ...f, teamAId: t[0].id, teamBId: t[1].id }));
    });
  }, []);

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const delayMin = Number(form.delay) || 0;
      const res = await api.createMatch({
        teamAId: Number(form.teamAId),
        teamBId: Number(form.teamBId),
        format: form.format,
        venue: form.venue || null,
        startTime: new Date(Date.now() + delayMin * 60000).toISOString(),
        autoSimulate: form.mode === 'auto',
      });
      onCreated(res);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="card form" onSubmit={submit}>
      <h3>Schedule a match</h3>
      <label>
        Home team
        <select className="input" value={form.teamAId} onChange={set('teamAId')}>
          {teams.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </select>
      </label>
      <label>
        Away team
        <select className="input" value={form.teamBId} onChange={set('teamBId')}>
          {teams.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </select>
      </label>
      <div className="form-row">
        <label>
          Format
          <select className="input" value={form.format} onChange={set('format')}>
            <option value="T10">T10 (10 overs)</option>
            <option value="T20">T20 (20 overs)</option>
            <option value="ODI">ODI (50 overs)</option>
          </select>
        </label>
        <label>
          Starts in (min)
          <input className="input" type="number" min="0" max="1440" value={form.delay} onChange={set('delay')} />
        </label>
      </div>
      <label>
        Venue <span className="muted small">(defaults to the home ground)</span>
        <input className="input" value={form.venue} onChange={set('venue')} placeholder="e.g. Eden Gardens" maxLength={120} />
      </label>
      <fieldset className="radio-group">
        <legend>Scoring</legend>
        <label>
          <input type="radio" name="mode" value="auto" checked={form.mode === 'auto'} onChange={set('mode')} /> Live simulation
        </label>
        <label>
          <input type="radio" name="mode" value="manual" checked={form.mode === 'manual'} onChange={set('mode')} /> Manual scorer
        </label>
      </fieldset>
      <ErrorBox message={error} />
      <button className="btn btn-primary" disabled={busy}>
        {busy ? 'Creating…' : 'Create match'}
      </button>
    </form>
  );
}

function TossForm({ summary, onStarted }) {
  const [winner, setWinner] = useState('');
  const [decision, setDecision] = useState('');
  const [error, setError] = useState(null);
  const start = async () => {
    try {
      onStarted(await api.startMatch(summary.id, { tossWinnerId: winner ? Number(winner) : null, decision: decision || null }));
    } catch (e) {
      setError(e.message);
    }
  };
  return (
    <div className="stack">
      <p className="muted">
        {summary.autoSimulate
          ? 'This match starts automatically at its scheduled time, or you can start it now.'
          : 'Manual match: conduct the toss to begin scoring.'}
      </p>
      <div className="form-row">
        <label>
          Toss won by
          <select className="input" value={winner} onChange={(e) => setWinner(e.target.value)}>
            <option value="">Random</option>
            <option value={summary.teamA.id}>{summary.teamA.name}</option>
            <option value={summary.teamB.id}>{summary.teamB.name}</option>
          </select>
        </label>
        <label>
          Elected to
          <select className="input" value={decision} onChange={(e) => setDecision(e.target.value)}>
            <option value="">Random</option>
            <option value="BAT">Bat</option>
            <option value="BOWL">Bowl</option>
          </select>
        </label>
      </div>
      <ErrorBox message={error} />
      <button className="btn btn-primary" onClick={start}>
        Toss &amp; start match
      </button>
    </div>
  );
}

function Scorer({ id }) {
  const { match, setMatch } = useMatch(id);
  const [squads, setSquads] = useState({});
  const [extra, setExtra] = useState('NONE');
  const [wicket, setWicket] = useState(false);
  const [wicketType, setWicketType] = useState('CAUGHT');
  const [dismissed, setDismissed] = useState('striker');
  const [fielderId, setFielderId] = useState('');
  const [bowlerId, setBowlerId] = useState('');
  const [commentary, setCommentary] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const teamIds = match ? [match.summary.teamA.id, match.summary.teamB.id] : [];
  useEffect(() => {
    teamIds.forEach((t) => {
      if (!squads[t]) api.squad(t).then((s) => setSquads((prev) => ({ ...prev, [t]: s })));
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [teamIds.join(',')]);

  // Keep the chosen wicket type valid for the selected extra.
  useEffect(() => {
    if (!WICKETS_FOR[extra].includes(wicketType)) setWicketType(WICKETS_FOR[extra][0]);
  }, [extra, wicketType]);

  if (!match) return <Spinner label="Loading match" />;
  const { summary, live, recentBalls } = match;

  const control = async (body) => {
    setError(null);
    try {
      setMatch(await api.control(id, body));
    } catch (e) {
      setError(e.message);
    }
  };

  const submit = async (r) => {
    setBusy(true);
    setError(null);
    const offBat = extra === 'NONE' || extra === 'NO_BALL';
    try {
      const res = await api.deliver(id, {
        runsOffBat: offBat ? r : 0,
        extraType: extra,
        extraRuns: offBat ? 0 : r,
        wicket,
        wicketType: wicket ? wicketType : null,
        dismissedPlayerId:
          wicket && wicketType === 'RUN_OUT' && dismissed === 'nonStriker' ? live.nonStriker?.playerId : null,
        fielderId: wicket && fielderId ? Number(fielderId) : null,
        bowlerId: live.needsBowler && bowlerId ? Number(bowlerId) : null,
        commentary: commentary || null,
      });
      setMatch(res);
      setExtra('NONE');
      setWicket(false);
      setWicketType('CAUGHT');
      setDismissed('striker');
      setFielderId('');
      setBowlerId('');
      setCommentary('');
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };

  const header = (
    <div className="scorer-head">
      <StatusBadge summary={summary} />
      <Link to={`/match/${summary.id}`}>
        {summary.teamA.shortName} vs {summary.teamB.shortName}
      </Link>
      <span className="muted small">{summary.format}</span>
    </div>
  );

  if (summary.status === 'SCHEDULED') {
    return (
      <div className="card">
        {header}
        <TossForm summary={summary} onStarted={setMatch} />
      </div>
    );
  }
  if (summary.status === 'COMPLETED' || !live) {
    return (
      <div className="card">
        {header}
        <p className="result">{summary.result}</p>
      </div>
    );
  }

  const simulating = summary.autoSimulate && !summary.paused;
  const bowlingSquad = squads[live.bowlingTeamId] || [];
  const current = summary.innings.find((i) => i.number === live.inningsNumber);
  const runOptions = extra === 'BYE' || extra === 'LEG_BYE' ? [1, 2, 3, 4, 5, 6] : [0, 1, 2, 3, 4, 5, 6];
  const runLabel = (r) => (extra === 'WIDE' ? (r === 0 ? 'Wd' : `Wd+${r}`) : extra === 'NO_BALL' ? (r === 0 ? 'Nb' : `Nb+${r}`) : r);

  return (
    <div className="card scorer">
      {header}
      <div className="scorer-score">
        <div>
          <b className="big">
            {current.runs}/{current.wickets}
          </b>{' '}
          <span className="muted">({current.overs} ov)</span>
        </div>
        <div className="small">{summary.statusText}</div>
      </div>
      <div className="scorer-crease small">
        <span>
          🏏 {live.striker?.name}* {live.striker?.runs}({live.striker?.balls})
        </span>
        <span>
          {live.nonStriker?.name} {live.nonStriker?.runs}({live.nonStriker?.balls})
        </span>
        <span>⚾ {live.needsBowler ? 'new over' : `${live.bowler?.name} ${live.bowler?.wickets}-${live.bowler?.runs} (${live.bowler?.overs})`}</span>
      </div>
      <BallStrip balls={live.thisOver} />

      <div className="sim-control">
        {summary.autoSimulate ? (
          <>
            <span>
              Simulation is <b>{summary.paused ? 'paused' : 'running'}</b>
            </span>
            <button className="btn btn-ghost" onClick={() => control({ paused: !summary.paused })}>
              {summary.paused ? 'Resume' : 'Pause'}
            </button>
            <button className="btn btn-ghost" onClick={() => control({ autoSimulate: false, paused: false })}>
              Take manual control
            </button>
          </>
        ) : (
          <>
            <span>
              <b>Manual scoring</b>
            </span>
            <button className="btn btn-ghost" onClick={() => control({ autoSimulate: true, paused: false })}>
              Hand back to simulator
            </button>
          </>
        )}
      </div>

      <fieldset className="pad-area" disabled={simulating || busy}>
        {simulating && <p className="muted small">Pause the simulation or take manual control to score by hand.</p>}

        {live.needsBowler && (
          <label>
            Bowler for the next over
            <select className="input" value={bowlerId} onChange={(e) => setBowlerId(e.target.value)}>
              <option value="">Auto-pick</option>
              {bowlingSquad
                .filter((p) => p.id !== live.lastBowlerId)
                .map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name} ({p.bowlingStyle || 'part-time'})
                  </option>
                ))}
            </select>
          </label>
        )}

        <div className="segmented" role="radiogroup" aria-label="Delivery type">
          {EXTRAS.map(([v, l]) => (
            <button type="button" key={v} className={extra === v ? 'active' : ''} onClick={() => setExtra(v)} aria-pressed={extra === v}>
              {l}
            </button>
          ))}
        </div>

        <div className="wicket-row">
          <label className="check">
            <input type="checkbox" checked={wicket} onChange={(e) => setWicket(e.target.checked)} /> Wicket
          </label>
          {wicket && (
            <>
              <select className="input" value={wicketType} onChange={(e) => setWicketType(e.target.value)}>
                {WICKETS_FOR[extra].map((w) => (
                  <option key={w} value={w}>
                    {label(w)}
                  </option>
                ))}
              </select>
              {wicketType === 'RUN_OUT' && (
                <select className="input" value={dismissed} onChange={(e) => setDismissed(e.target.value)}>
                  <option value="striker">{live.striker?.name} (striker)</option>
                  {live.nonStriker && <option value="nonStriker">{live.nonStriker.name} (non-striker)</option>}
                </select>
              )}
              {['CAUGHT', 'RUN_OUT', 'STUMPED'].includes(wicketType) && (
                <select className="input" value={fielderId} onChange={(e) => setFielderId(e.target.value)}>
                  <option value="">Fielder: auto</option>
                  {bowlingSquad.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name}
                    </option>
                  ))}
                </select>
              )}
            </>
          )}
        </div>

        <input className="input" placeholder="Commentary (optional, auto-generated if empty)" value={commentary} onChange={(e) => setCommentary(e.target.value)} maxLength={500} />

        <div className="run-pad">
          {(wicket && (extra === 'BYE' || extra === 'LEG_BYE') ? [0, ...runOptions] : runOptions).map((r) => (
            <button
              type="button"
              key={r}
              className={`run-btn${r === 4 ? ' r4' : ''}${r === 6 ? ' r6' : ''}${wicket ? ' rw' : ''}`}
              onClick={() => submit(r)}
            >
              {runLabel(r)}
              {wicket && <small>+W</small>}
            </button>
          ))}
        </div>
      </fieldset>
      <ErrorBox message={error} />

      <h4>Last balls</h4>
      <ul className="mini-feed">
        {recentBalls.slice(0, 6).map((b) => (
          <li key={b.id}>
            <span className="muted">{b.overLabel}</span> <Ball outcome={b.outcome} /> <span>{b.commentary}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

export default function ControlRoom() {
  const [params, setParams] = useSearchParams();
  const selected = params.get('match');
  const { data } = useDashboard();
  const options = data ? [...data.live, ...data.upcoming] : [];

  return (
    <div className="page">
      <h1>Control Room</h1>
      <p className="muted">Schedule fixtures and score matches by hand. Every ball you enter is pushed live to all viewers.</p>
      <div className="control-layout">
        <CreateMatchForm onCreated={(d) => setParams({ match: d.summary.id })} />
        <div className="stack">
          <div className="card">
            <label>
              Match to control
              <select className="input" value={selected || ''} onChange={(e) => setParams(e.target.value ? { match: e.target.value } : {})}>
                <option value="">Select a match…</option>
                {selected && !options.some((m) => String(m.id) === selected) && <option value={selected}>Match #{selected}</option>}
                {options.map((m) => (
                  <option key={m.id} value={m.id}>
                    #{m.id} {m.teamA.shortName} vs {m.teamB.shortName} · {m.status.toLowerCase()}
                    {m.status === 'LIVE' && !m.autoSimulate ? ' (manual)' : ''}
                  </option>
                ))}
              </select>
            </label>
          </div>
          {selected && <Scorer key={selected} id={selected} />}
        </div>
      </div>
    </div>
  );
}
