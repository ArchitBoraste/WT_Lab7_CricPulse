// Real-time layer: one shared STOMP-over-WebSocket connection for the whole app.
// Components subscribe to topics; subscriptions are restored automatically after a reconnect,
// and hooks fall back to REST polling while the socket is down.
import { Client } from '@stomp/stompjs';
import { useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { api } from './api';

const wsUrl =
  import.meta.env.VITE_WS_URL ||
  `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}/ws`;

const listeners = new Map(); // topic -> Set<callback>
const subscriptions = new Map(); // topic -> StompSubscription
const statusListeners = new Set();
let status = 'connecting';

function setStatus(next) {
  if (status === next) return;
  status = next;
  statusListeners.forEach((l) => l());
}

const client = new Client({
  brokerURL: wsUrl,
  reconnectDelay: 3000,
  heartbeatIncoming: 10000,
  heartbeatOutgoing: 10000,
  onConnect: () => {
    subscriptions.clear();
    for (const topic of listeners.keys()) attach(topic);
    setStatus('live');
  },
  onWebSocketClose: () => setStatus('reconnecting'),
  onStompError: () => setStatus('reconnecting'),
});
client.activate();

function attach(topic) {
  const sub = client.subscribe(topic, (msg) => {
    const body = JSON.parse(msg.body);
    listeners.get(topic)?.forEach((cb) => cb(body));
  });
  subscriptions.set(topic, sub);
}

export function subscribe(topic, callback) {
  let set = listeners.get(topic);
  if (!set) {
    set = new Set();
    listeners.set(topic, set);
    if (client.connected) attach(topic);
  }
  set.add(callback);
  return () => {
    set.delete(callback);
    if (set.size === 0) {
      listeners.delete(topic);
      try {
        subscriptions.get(topic)?.unsubscribe();
      } catch {
        /* connection already gone */
      }
      subscriptions.delete(topic);
    }
  };
}

export function useConnectionStatus() {
  return useSyncExternalStore(
    (cb) => {
      statusListeners.add(cb);
      return () => statusListeners.delete(cb);
    },
    () => status,
  );
}

/** Calls fn every `ms` while the socket is not connected. */
function usePollingFallback(fn, ms) {
  const connection = useConnectionStatus();
  const fnRef = useRef(fn);
  fnRef.current = fn;
  useEffect(() => {
    if (connection === 'live') return undefined;
    const t = setInterval(() => fnRef.current(), ms);
    return () => clearInterval(t);
  }, [connection, ms]);
}

const byStart = (a, b) => new Date(a.startTime) - new Date(b.startTime);
const byCompleted = (a, b) => new Date(b.completedAt) - new Date(a.completedAt);

/** Live, upcoming and recent matches, kept current by /topic/matches. */
export function useDashboard() {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);

  const load = () =>
    api.dashboard().then(
      (d) => {
        setData(d);
        setError(null);
      },
      (e) => setError(e.message),
    );

  useEffect(() => {
    load();
    return subscribe('/topic/matches', (summary) => {
      setData((prev) => {
        if (!prev) return prev;
        const without = (list) => list.filter((m) => m.id !== summary.id);
        const next = { live: without(prev.live), upcoming: without(prev.upcoming), recent: without(prev.recent) };
        if (summary.status === 'LIVE') {
          // Keep cards in place when they update; append newly started matches.
          const idx = prev.live.findIndex((m) => m.id === summary.id);
          next.live = [...next.live];
          next.live.splice(idx === -1 ? next.live.length : idx, 0, summary);
        } else if (summary.status === 'SCHEDULED') {
          next.upcoming = [...next.upcoming, summary].sort(byStart).slice(0, 8);
        } else {
          next.recent = [summary, ...next.recent].sort(byCompleted).slice(0, 8);
        }
        return next;
      });
    });
  }, []);

  usePollingFallback(load, 5000);
  return { data, error, reload: load };
}

/** Full snapshot of one match, kept current by /topic/matches/{id}. */
export function useMatch(id) {
  const [match, setMatch] = useState(null);
  const [error, setError] = useState(null);

  const load = () =>
    api.match(id).then(
      (d) => {
        setMatch(d);
        setError(null);
      },
      (e) => setError(e.message),
    );

  useEffect(() => {
    setMatch(null);
    load();
    return subscribe(`/topic/matches/${id}`, setMatch);
  }, [id]);

  usePollingFallback(load, 4000);
  return { match, error, setMatch };
}

/** Re-fetches on an interval; for data that is not pushed (stats). */
export function usePolled(fetcher, ms, deps = []) {
  const [data, setData] = useState(null);
  useEffect(() => {
    let alive = true;
    const run = () => fetcher().then((d) => alive && setData(d)).catch(() => {});
    run();
    const t = setInterval(run, ms);
    return () => {
      alive = false;
      clearInterval(t);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  return data;
}
