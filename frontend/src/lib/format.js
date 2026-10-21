export function scoreText(inn) {
  if (!inn) return '';
  return `${inn.runs}/${inn.wickets}`;
}

export function inningsFor(summary, teamId) {
  return (summary.innings || []).filter((i) => i.teamId === teamId);
}

export function formatTime(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleString(undefined, {
    weekday: 'short',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function formatDate(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
}

export function countdown(iso, now) {
  const ms = new Date(iso) - now;
  if (ms <= 0) return 'Starting now';
  const s = Math.floor(ms / 1000);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  if (h > 0) return `in ${h}h ${m}m`;
  return `in ${m}:${String(sec).padStart(2, '0')}`;
}

/** CSS class for a ball outcome label such as "4", "6", "W", "Wd", "2Lb". */
export function ballClass(outcome) {
  if (outcome === 'W') return 'ball ball-w';
  if (outcome === '4') return 'ball ball-4';
  if (outcome === '6') return 'ball ball-6';
  if (outcome === '0') return 'ball ball-0';
  if (/Wd|Nb|B|Lb/.test(outcome)) return 'ball ball-x';
  return 'ball';
}

export function ballText(outcome) {
  return outcome === '0' ? '•' : outcome;
}

export const fmt = (n, digits = 2) => (n === null || n === undefined ? '–' : Number(n).toFixed(digits));
