// Seeds a small synthetic depot through the public API, exactly as a user would set one up.
const api = process.env.E2E_API ?? 'http://localhost:18081';
const password = process.env.E2E_ADMIN_PASSWORD ?? '';

async function call(path: string, token: string | null, body: unknown, method = 'POST') {
  const res = await fetch(api + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`${method} ${path} -> ${res.status} ${await res.text()}`);
  return res.status === 204 ? null : res.json();
}

export default async function globalSetup() {
  if (!password) throw new Error('E2E_ADMIN_PASSWORD is not set (run the tests through scripts/e2e.sh)');
  const { accessToken: t } = await call('/api/v1/auth/token', null, { username: 'admin', password });
  const grid = await call('/api/v1/grid-connections', t, { name: 'Depot feed', capacityLimitKw: 300 });
  await call(`/api/v1/grid-connections/${grid.id}/tariff`, t, { bands: [
    { start: '00:00', end: '06:00', pricePerKwh: 0.1 },
    { start: '06:00', end: '00:00', pricePerKwh: 0.3 },
  ] }, 'PUT');
  const station = await call('/api/v1/stations', t, { gridConnectionId: grid.id, name: 'North depot' });
  for (const label of ['A1', 'A2']) {
    await call(`/api/v1/stations/${station.id}/chargers`, t, { label, connectorType: 'CCS1', maxPowerKw: 150 });
  }
  const fleet = await call('/api/v1/fleets', t, { name: 'Night vans' });
  const vans = [['eTransit', 'CCS1', 20], ['eSprinter', 'CCS1', 35], ['ID.Buzz', 'NACS', 50]] as const;
  for (const [i, [model, connectorType, soc]] of vans.entries()) {
    await call(`/api/v1/fleets/${fleet.id}/vehicles`, t, {
      vin: `1HGCM82633A00000${i + 1}`, model, batteryCapacityKwh: 90, maxChargeRateKw: 115,
      connectorType, stateOfChargePct: soc,
    });
  }
  await call('/api/v1/advisor/documents/samples', t, {});
}
