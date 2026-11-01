import { describe, expect, it } from 'vitest';
import type { Charger, GridProfile, ScheduledSession } from '../api/types';
import { horizonFor, loadSeries, tariffBands, toLanes, tonight } from './schedule';

const charger = (id: string, label: string): Charger =>
  ({ id, label, stationId: 's', connectorType: 'CCS1', maxPowerKw: 150, status: 'AVAILABLE' });
const session = (chargerId: string, start: string, end: string, vehicleId = 'v1'): ScheduledSession =>
  ({ vehicleId, chargerId, window: { start, end }, power: { value: 100 } });

describe('toLanes', () => {
  it('orders lanes by label naturally and places bars in their charger lane', () => {
    const { lanes, bars } = toLanes(
      [session('b', '2026-01-06T00:00:00Z', '2026-01-06T01:00:00Z', 'v2'), session('a', '2026-01-06T02:00:00Z', '2026-01-06T03:00:00Z')],
      [charger('b', 'C10'), charger('a', 'C2')],
      new Map([['v2', 2]]),
    );
    expect(lanes.map((l) => l.label)).toEqual(['C2', 'C10']);
    expect(bars).toEqual([
      { lane: 1, start: Date.parse('2026-01-06T00:00:00Z'), end: Date.parse('2026-01-06T01:00:00Z'), vehicleId: 'v2', powerKw: 100, priority: 2 },
      { lane: 0, start: Date.parse('2026-01-06T02:00:00Z'), end: Date.parse('2026-01-06T03:00:00Z'), vehicleId: 'v1', powerKw: 100, priority: 0 },
    ]);
  });

  it('adds a lane for a session on a charger it was not told about', () => {
    const { lanes } = toLanes([session('zzz999', '2026-01-06T00:00:00Z', '2026-01-06T01:00:00Z')], [], new Map());
    expect(lanes).toHaveLength(1);
  });
});

describe('loadSeries', () => {
  it('turns per-slot load into step points with a closing point', () => {
    const profile: GridProfile = { gridId: 'g', capacity: { value: 300 }, loadBySlot: [{ value: 0 }, { value: 120 }], peak: { value: 120 }, peakUtilization: 0.4, bottleneckSlots: [] };
    const t0 = Date.parse('2026-01-06T00:00:00Z');
    expect(loadSeries(profile, '2026-01-06T00:00:00Z', 15)).toEqual([[t0, 0], [t0 + 900_000, 120], [t0 + 1_800_000, 120]]);
  });
});

describe('tariffBands', () => {
  it('splits an overnight window into peak, off-peak and shoulder in local time (UTC in tests)', () => {
    const from = Date.parse('2026-01-05T18:00:00Z');
    const to = Date.parse('2026-01-06T10:00:00Z');
    const bands = tariffBands(from, to);
    expect(bands.map((b) => [new Date(b.start).toISOString().slice(11, 16), new Date(b.end).toISOString().slice(11, 16), b.kind])).toEqual([
      ['18:00', '21:00', 'peak'],
      ['21:00', '06:00', 'offpeak'],
      ['06:00', '10:00', 'shoulder'],
    ]);
  });
});

describe('horizonFor', () => {
  it('covers every window, aligned to slots', () => {
    const h = horizonFor([
      { vehicleId: 'a', targetSocPct: 80, priority: 1, arrival: '2026-01-05T18:07:00Z', departure: '2026-01-06T06:00:00Z' },
      { vehicleId: 'b', targetSocPct: 80, priority: 1, arrival: '2026-01-05T19:00:00Z', departure: '2026-01-06T07:10:00Z' },
    ]);
    expect(h.horizonStart).toBe('2026-01-05T18:00:00.000Z');
    expect(h.slotMinutes).toBe(15);
    expect(h.slotCount).toBe(53); // 18:00 -> 07:15
  });

  it('caps at the API limit and refuses an empty plan', () => {
    expect(horizonFor([{ vehicleId: 'a', targetSocPct: 80, priority: 1, arrival: '2026-01-01T00:00:00Z', departure: '2026-01-10T00:00:00Z' }]).slotCount).toBe(288);
    expect(() => horizonFor([])).toThrow();
  });
});

describe('tonight', () => {
  it('is 18:00 today to 06:00 tomorrow before the evening, and tomorrow night after it', () => {
    const morning = tonight(new Date('2026-01-05T09:00:00Z'));
    expect(morning.arrival.toISOString()).toBe('2026-01-05T18:00:00.000Z');
    expect(morning.departure.toISOString()).toBe('2026-01-06T06:00:00.000Z');
    expect(tonight(new Date('2026-01-05T19:00:00Z')).arrival.toISOString()).toBe('2026-01-06T18:00:00.000Z');
  });
});
