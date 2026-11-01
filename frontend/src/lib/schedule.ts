import type { Charger, GridProfile, ScheduledSession, Vehicle, VehicleNeed } from '../api/types';

export interface Lane { chargerId: string; label: string }
export interface Bar { lane: number; start: number; end: number; vehicleId: string; powerKw: number; priority: number }

/** One timeline lane per charger, ordered by label, plus a bar per session placed in its charger's lane. */
export function toLanes(sessions: ScheduledSession[], chargers: Charger[], priorities: Map<string, number>) {
  const lanes: Lane[] = [...chargers]
    .sort((a, b) => a.label.localeCompare(b.label, undefined, { numeric: true }))
    .map((c) => ({ chargerId: c.id, label: c.label }));
  const index = new Map(lanes.map((l, i) => [l.chargerId, i]));
  const bars: Bar[] = [];
  for (const s of sessions) {
    let lane = index.get(s.chargerId);
    if (lane === undefined) {
      lane = lanes.length;
      lanes.push({ chargerId: s.chargerId, label: s.chargerId.slice(0, 6) });
      index.set(s.chargerId, lane);
    }
    bars.push({
      lane, start: Date.parse(s.window.start), end: Date.parse(s.window.end), vehicleId: s.vehicleId,
      powerKw: s.power.value, priority: priorities.get(s.vehicleId) ?? 0,
    });
  }
  return { lanes, bars };
}

/** Grid load as [timestampMs, kW] step points, one per slot start, plus the slot end so the last step is drawn. */
export function loadSeries(profile: GridProfile, horizonStart: string, slotMinutes: number): [number, number][] {
  const t0 = Date.parse(horizonStart);
  const step = slotMinutes * 60_000;
  const points: [number, number][] = profile.loadBySlot.map((p, i) => [t0 + i * step, p.value]);
  if (points.length) points.push([t0 + points.length * step, points[points.length - 1][1]]);
  return points;
}

export interface Band { start: number; end: number; kind: 'peak' | 'offpeak' | 'shoulder' }

/**
 * Tariff tint behind the timeline, using the bands of the synthetic sample tariff in the viewer's local time.
 * Display only; costs always come from the server.
 */
export function tariffBands(fromMs: number, toMs: number, bands = DEFAULT_BANDS): Band[] {
  const out: Band[] = [];
  const day = new Date(fromMs);
  day.setHours(0, 0, 0, 0);
  for (let d = day.getTime() - 86_400_000; d < toMs; d += 86_400_000) {
    for (const b of bands) {
      const s = new Date(d);
      s.setHours(b.from, 0, 0, 0);
      const e = new Date(d);
      e.setHours(b.to, 0, 0, 0);
      if (b.to <= b.from) e.setDate(e.getDate() + 1);
      const start = Math.max(s.getTime(), fromMs);
      const end = Math.min(e.getTime(), toMs);
      if (end > start) out.push({ start, end, kind: b.kind });
    }
  }
  return out.sort((a, b) => a.start - b.start);
}

export const DEFAULT_BANDS: { from: number; to: number; kind: Band['kind'] }[] = [
  { from: 16, to: 21, kind: 'peak' },
  { from: 21, to: 6, kind: 'offpeak' },
  { from: 6, to: 16, kind: 'shoulder' },
];

/** Smallest slot-aligned horizon covering every vehicle's window, capped at 288 slots (the API limit). */
export function horizonFor(needs: VehicleNeed[], slotMinutes = 15) {
  if (!needs.length) throw new Error('select at least one vehicle');
  const step = slotMinutes * 60_000;
  const start = Math.floor(Math.min(...needs.map((n) => Date.parse(n.arrival))) / step) * step;
  const end = Math.max(...needs.map((n) => Date.parse(n.departure)));
  const slotCount = Math.min(288, Math.max(1, Math.ceil((end - start) / step)));
  return { horizonStart: new Date(start).toISOString(), slotMinutes, slotCount };
}

/** Default overnight window in local time: tonight 18:00 to tomorrow 06:00. */
export function tonight(now = new Date()) {
  const arrival = new Date(now);
  arrival.setHours(18, 0, 0, 0);
  if (now.getHours() >= 18) arrival.setDate(arrival.getDate() + 1);
  const departure = new Date(arrival);
  departure.setDate(departure.getDate() + 1);
  departure.setHours(6, 0, 0, 0);
  return { arrival, departure };
}

/** Value for <input type="datetime-local"> in local time. */
export function toLocalInput(d: Date) {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export const vehicleLabel = (v: Pick<Vehicle, 'model' | 'vin'>) => `${v.model} …${v.vin.slice(-4)}`;
