// k6 load test for the GridPulse API against a throwaway stack (run via scripts/load-test.sh).
// Two open-model workloads run together for the same period:
//   reads - dashboard-style list calls at a fixed arrival rate
//   plans - schedule previews for a 100-vehicle, 20-charger depot (the expensive call)
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend, Rate } from 'k6/metrics';

const BASE = __ENV.BASE;
const PASSWORD = __ENV.ADMIN_PASSWORD;
const DURATION = __ENV.DURATION || '60s';
const READ_RPS = Number(__ENV.READ_RPS || 50);
const PLAN_RPS = Number(__ENV.PLAN_RPS || 5);
const VEHICLES = 100;
const CHARGERS = 20;

const readLatency = new Trend('read_latency', true);
const planLatency = new Trend('plan_latency', true);
const errors = new Rate('errors');
const readCount = new Counter('read_count');
const planCount = new Counter('plan_count');

export const options = {
  scenarios: {
    reads: { executor: 'constant-arrival-rate', rate: READ_RPS, timeUnit: '1s', duration: DURATION,
             preAllocatedVUs: 20, maxVUs: 100, exec: 'reads' },
    plans: { executor: 'constant-arrival-rate', rate: PLAN_RPS, timeUnit: '1s', duration: DURATION,
             preAllocatedVUs: 10, maxVUs: 50, exec: 'plans' },
  },
  // Pass/fail gates for the run; the measured values are what goes in BENCHMARKS.md.
  thresholds: {
    errors: ['rate<0.01'],
    read_latency: ['p(95)<250'],
    plan_latency: ['p(95)<1500'],
  },
};

function json(method, path, token, body) {
  const res = http.request(method, BASE + path, body === undefined ? null : JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
  });
  if (res.status >= 300) throw new Error(`${method} ${path} -> ${res.status} ${res.body}`);
  return res.body ? res.json() : null;
}

export function setup() {
  const token = json('POST', '/api/v1/auth/token', null, { username: 'admin', password: PASSWORD }).accessToken;
  const grid = json('POST', '/api/v1/grid-connections', token, { name: 'Load feed', capacityLimitKw: 1200 });
  json('PUT', `/api/v1/grid-connections/${grid.id}/tariff`, token, { bands: [
    { start: '00:00', end: '06:00', pricePerKwh: 0.1 }, { start: '06:00', end: '00:00', pricePerKwh: 0.3 }] });
  const station = json('POST', '/api/v1/stations', token, { gridConnectionId: grid.id, name: 'Load depot' });
  for (let i = 1; i <= CHARGERS; i++) {
    json('POST', `/api/v1/stations/${station.id}/chargers`, token, { label: `L${i}`, connectorType: 'CCS1', maxPowerKw: 150 });
  }
  const fleet = json('POST', '/api/v1/fleets', token, { name: 'Load fleet' });
  const vehicles = [];
  for (let i = 0; i < VEHICLES; i++) {
    const v = json('POST', `/api/v1/fleets/${fleet.id}/vehicles`, token, {
      vin: '1GPLDTST' + String(i).padStart(9, '0'), model: 'Synthetic Van', batteryCapacityKwh: 60 + (i % 5) * 10,
      maxChargeRateKw: 50 + (i % 3) * 50, connectorType: 'CCS1', stateOfChargePct: 10 + (i * 7) % 60 });
    vehicles.push({
      vehicleId: v.id, targetSocPct: 80, priority: i % 3,
      // staggered plug-in 18:00-21:00, departures 05:00-07:45 (UTC, synthetic)
      arrival: `2026-01-05T${String(18 + (i % 4)).padStart(2, '0')}:00:00Z`,
      departure: `2026-01-06T${String(5 + (i % 3)).padStart(2, '0')}:${String((i * 15) % 60).padStart(2, '0')}:00Z`,
    });
  }
  return { token, fleetId: fleet.id, plan: {
    horizonStart: '2026-01-05T18:00:00Z', slotMinutes: 15, slotCount: 56, algorithm: 'PRIORITY_SLACK', vehicles } };
}

const auth = (t) => ({ headers: { Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' } });

export function reads(data) {
  const path = Math.random() < 0.5 ? '/api/v1/fleets' : `/api/v1/fleets/${data.fleetId}/vehicles`;
  const res = http.get(BASE + path, auth(data.token));
  readLatency.add(res.timings.duration);
  readCount.add(1);
  errors.add(!check(res, { 'read 200': (r) => r.status === 200 }));
}

export function plans(data) {
  const res = http.post(BASE + '/api/v1/schedules/preview', JSON.stringify(data.plan), auth(data.token));
  planLatency.add(res.timings.duration);
  planCount.add(1);
  errors.add(!check(res, {
    'plan 200': (r) => r.status === 200,
    'no violations': (r) => r.status === 200 && r.json('violations').length === 0,
  }));
}

export function handleSummary(d) {
  const m = d.metrics;
  const t = (x) => `p50 ${x.values.med.toFixed(1)} ms, p95 ${x.values['p(95)'].toFixed(1)} ms, max ${x.values.max.toFixed(1)} ms`;
  const out = {
    duration: DURATION, readRps: READ_RPS, planRps: PLAN_RPS, vehicles: VEHICLES, chargers: CHARGERS,
    reads: { count: m.read_count.values.count, med: m.read_latency.values.med, p95: m.read_latency.values['p(95)'], max: m.read_latency.values.max },
    plans: { count: m.plan_count.values.count, med: m.plan_latency.values.med, p95: m.plan_latency.values['p(95)'], max: m.plan_latency.values.max },
    errorRate: m.errors.values.rate,
    thresholdsPassed: Object.values(m).every((x) => !x.thresholds || Object.values(x.thresholds).every((v) => v.ok)),
  };
  const text = [
    `reads: ${out.reads.count} requests, ${t(m.read_latency)}`,
    `plans (${VEHICLES} vehicles, ${CHARGERS} chargers): ${out.plans.count} requests, ${t(m.plan_latency)}`,
    `errors: ${(out.errorRate * 100).toFixed(2)}%   thresholds ${out.thresholdsPassed ? 'PASSED' : 'FAILED'}`,
  ].join('\n');
  return { stdout: text + '\n', '/out/load-test-latest.json': JSON.stringify(out, null, 2) };
}
