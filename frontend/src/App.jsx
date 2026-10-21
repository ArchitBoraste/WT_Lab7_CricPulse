import { useEffect, useState } from 'react';
import { NavLink, Route, Routes } from 'react-router-dom';
import Dashboard from './pages/Dashboard';
import MatchCentre from './pages/MatchCentre';
import Stats from './pages/Stats';
import PlayerProfile from './pages/PlayerProfile';
import ControlRoom from './pages/ControlRoom';
import { useConnectionStatus } from './lib/live';

function ConnectionPill() {
  const status = useConnectionStatus();
  const text = { live: 'Live', connecting: 'Connecting', reconnecting: 'Reconnecting' }[status];
  return (
    <span className={`conn conn-${status}`} title={status === 'live' ? 'Receiving live updates over WebSocket' : 'Falling back to polling'}>
      <i /> {text}
    </span>
  );
}

function ThemeToggle() {
  const [theme, setTheme] = useState(() => {
    try {
      return localStorage.getItem('theme') || 'dark';
    } catch {
      return 'dark';
    }
  });
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    try {
      localStorage.setItem('theme', theme);
    } catch {
      /* storage unavailable */
    }
  }, [theme]);
  return (
    <button className="icon-btn" onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')} aria-label="Toggle colour theme">
      {theme === 'dark' ? '☀' : '☾'}
    </button>
  );
}

export default function App() {
  return (
    <>
      <header className="topbar">
        <div className="topbar-inner">
          <NavLink to="/" className="brand">
            <span className="brand-ball" />
            <span>
              Cric<b>Pulse</b>
            </span>
          </NavLink>
          <nav>
            <NavLink to="/" end>
              Matches
            </NavLink>
            <NavLink to="/stats">Stats</NavLink>
            <NavLink to="/control">Control Room</NavLink>
          </nav>
          <div className="topbar-right">
            <ConnectionPill />
            <ThemeToggle />
          </div>
        </div>
      </header>
      <main>
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/match/:id" element={<MatchCentre />} />
          <Route path="/stats" element={<Stats />} />
          <Route path="/players/:id" element={<PlayerProfile />} />
          <Route path="/control" element={<ControlRoom />} />
          <Route path="*" element={<div className="page"><h1>Not found</h1></div>} />
        </Routes>
      </main>
      <footer className="footer muted small">CricPulse · Spring Boot + React · simulated live cricket data</footer>
    </>
  );
}
