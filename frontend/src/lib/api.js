// Thin REST client for the Spring Boot API.
const BASE = import.meta.env.VITE_API_URL || '';

async function request(path, options = {}) {
  const res = await fetch(BASE + path, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
  });
  if (!res.ok) {
    let message = `${res.status} ${res.statusText}`;
    try {
      const err = await res.json();
      message = err.message || message;
      if (err.fields) {
        message += ': ' + Object.entries(err.fields).map(([k, v]) => `${k} ${v}`).join(', ');
      }
    } catch {
      /* non-JSON error body */
    }
    throw new Error(message);
  }
  return res.status === 204 ? null : res.json();
}

const post = (path, body) => request(path, { method: 'POST', body: body ? JSON.stringify(body) : undefined });
const patch = (path, body) => request(path, { method: 'PATCH', body: JSON.stringify(body) });

export const api = {
  dashboard: () => request('/api/matches/dashboard'),
  matches: (status, limit = 20) => request(`/api/matches?${status ? `status=${status}&` : ''}limit=${limit}`),
  match: (id) => request(`/api/matches/${id}`),
  commentary: (id, page = 0, size = 30) => request(`/api/matches/${id}/commentary?page=${page}&size=${size}`),
  createMatch: (body) => post('/api/matches', body),
  startMatch: (id, body) => post(`/api/matches/${id}/start`, body),
  deliver: (id, body) => post(`/api/matches/${id}/deliveries`, body),
  control: (id, body) => patch(`/api/matches/${id}/simulation`, body),

  teams: () => request('/api/teams'),
  squad: (teamId) => request(`/api/teams/${teamId}/players`),
  players: (q, teamId) => {
    const params = new URLSearchParams();
    if (q) params.set('q', q);
    if (teamId) params.set('teamId', teamId);
    return request(`/api/players?${params}`);
  },
  player: (id) => request(`/api/players/${id}`),

  leaderboard: (limit = 10) => request(`/api/stats/leaderboard?limit=${limit}`),
  overview: () => request('/api/stats/overview'),
};
