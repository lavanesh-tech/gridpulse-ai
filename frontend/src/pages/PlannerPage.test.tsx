import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PlannerPage } from './PlannerPage';
import { mockApi, renderWithApp } from '../test/render';
import { setToken } from '../api/client';
import type { Metrics, Preview } from '../api/types';

// Canvas charts do not render in jsdom; the strip's data logic is unit-tested in lib/schedule.test.ts.
vi.mock('../charts/EChart', () => ({ EChart: ({ label }: { label: string }) => <div role="img" aria-label={label} /> }));

afterEach(() => {
  setToken(null);
  vi.unstubAllGlobals();
});

const vehicles = [
  { id: 'v-1', fleetId: 'f-1', vin: '1HGCM82633A000001', model: 'eTransit', batteryCapacityKwh: 89, maxChargeRateKw: 115, connectorType: 'CCS1', stateOfChargePct: 20 },
  { id: 'v-2', fleetId: 'f-1', vin: '1HGCM82633A000002', model: 'eSprinter', batteryCapacityKwh: 113, maxChargeRateKw: 115, connectorType: 'NACS', stateOfChargePct: 35 },
];

const metrics = (met: number): Metrics => ({
  algorithm: 'priority-slack', vehicles: 2, vehiclesNeedingCharge: 2, sessions: 1, deadlinesMet: met, deadlineSuccessRate: met / 2,
  violations: 0, chargerUtilization: 0.25, energyDelivered: { value: 53.4 }, energyRequired: { value: 105 },
  byPriority: [], cost: { totalCost: 5.87, energy: { value: 53.4 }, averagePricePerKwh: 0.11 },
});

const preview: Preview = {
  schedule: {
    algorithm: 'priority-slack',
    sessions: [{ vehicleId: 'v-1', chargerId: 'c-1', window: { start: '2026-01-06T04:00:00Z', end: '2026-01-06T06:00:00Z' }, power: { value: 26.7 } }],
    unmet: [{ vehicleId: 'v-2', reason: 'NO_COMPATIBLE_CHARGER', required: { value: 50.85 }, delivered: { value: 0 } }],
  },
  violations: [], metrics: metrics(1), existingBookingsConsidered: 1,
  gridProfiles: [{ gridId: 'g-1', capacity: { value: 300 }, loadBySlot: [{ value: 0 }], peak: { value: 26.7 }, peakUtilization: 0.09, bottleneckSlots: [] }],
};

const routes = [
  { path: '/api/v1/fleets', body: [{ id: 'f-1', name: 'Night vans', createdAt: '2026-01-01T00:00:00Z' }] },
  { path: '/api/v1/fleets/f-1/vehicles', body: vehicles },
  { path: '/api/v1/stations', body: [{ id: 's-1', gridConnectionId: 'g-1', name: 'Depot A', status: 'ONLINE' }] },
  { path: '/api/v1/stations/s-1/chargers', body: [{ id: 'c-1', stationId: 's-1', label: 'A1', connectorType: 'CCS1', maxPowerKw: 150, status: 'AVAILABLE' }] },
  { method: 'POST', path: '/api/v1/schedules/preview', body: preview },
  { method: 'POST', path: '/api/v1/advisor/explain-schedule', body: {
    narrative: null, aiAvailable: false, toolCalls: [],
    comparison: [
      { algorithm: 'BASELINE', deadlinesMet: 1, deadlineSuccessRate: 0.5, estimatedCost: 15.2, hardConstraintViolations: 0 },
      { algorithm: 'PRIORITY_SLACK', deadlinesMet: 1, deadlineSuccessRate: 0.5, estimatedCost: 5.87, hardConstraintViolations: 0 },
      { algorithm: 'PRIORITY_SLACK_COST', deadlinesMet: 1, deadlineSuccessRate: 0.5, estimatedCost: 5.87, hardConstraintViolations: 0 },
    ] } },
  { method: 'POST', path: '/api/v1/schedules/recalculate', body: {
    before: preview, metricsAfter: metrics(0), gridProfilesAfter: preview.gridProfiles,
    result: { schedule: { ...preview.schedule, sessions: [] }, replannedFrom: '2026-01-06T05:00:00Z', sessionsKept: 1,
      sessionsInterrupted: 1, sessionsReplanned: 0, newlyUnmetVehicles: ['v-1'], violations: [] } } },
];

/** The big number shown above a figure's label. */
async function figure(label: string) {
  return (await screen.findByText(label)).previousElementSibling as HTMLElement;
}

describe('PlannerPage', () => {
  it('plans the selected vehicles and explains who will miss departure and why', async () => {
    const calls = mockApi(routes);
    renderWithApp(<PlannerPage />, { role: 'FLEET_MANAGER' });

    expect(await screen.findByText('eTransit …0001')).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText('Priority for eTransit …0001'), 'Urgent');
    await userEvent.click(screen.getByRole('button', { name: 'Show plan' }));

    expect(await figure('Leave on time with their target charge')).toHaveTextContent('1 of 2');
    expect(await figure('Estimated energy cost, 0.11 per kWh')).toHaveTextContent('5.87');
    expect(screen.getByText('Planned around 1 existing booking.')).toBeInTheDocument();
    expect(screen.getByText('No charger with a matching connector')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Charging sessions per charger over the night' })).toBeInTheDocument();

    const sent = calls.find((c) => c.url === '/api/v1/schedules/preview')!.body as { algorithm: string; vehicles: { vehicleId: string; priority: number; targetSocPct: number }[] };
    expect(sent.algorithm).toBe('PRIORITY_SLACK');
    expect(sent.vehicles).toEqual([
      expect.objectContaining({ vehicleId: 'v-1', priority: 2, targetSocPct: 80 }),
      expect.objectContaining({ vehicleId: 'v-2', priority: 1, targetSocPct: 80 }),
    ]);

    const comparison = screen.getByRole('heading', { name: 'Same vehicles, other strategies' }).nextElementSibling as HTMLElement;
    expect(within(comparison).getByText('First come, first served')).toBeInTheDocument();
    expect(within(comparison).getByText('15.20')).toBeInTheDocument();
  });

  it('simulates a charger failure and names vehicles that would now miss departure', async () => {
    const calls = mockApi(routes);
    renderWithApp(<PlannerPage />, { role: 'OPERATOR' });
    await userEvent.click(await screen.findByRole('button', { name: 'Show plan' }));
    await figure('Leave on time with their target charge');

    await userEvent.selectOptions(screen.getByLabelText('Charger'), 'A1');
    await userEvent.click(screen.getByRole('button', { name: 'Simulate failure' }));

    expect(await screen.findByText(/Now missing departure: eTransit …0001/)).toBeInTheDocument();
    const req = calls.find((c) => c.url === '/api/v1/schedules/recalculate')!.body as { disruption: { chargersOutOfService: string[] } };
    expect(req.disruption.chargersOutOfService).toEqual(['c-1']);
  });

  it('only excluded vehicles: asks to include one instead of calling the server', async () => {
    const calls = mockApi(routes);
    renderWithApp(<PlannerPage />, { role: 'FLEET_MANAGER' });
    await userEvent.click(await screen.findByLabelText('Include eTransit …0001'));
    await userEvent.click(screen.getByLabelText('Include eSprinter …0002'));
    await userEvent.click(screen.getByRole('button', { name: 'Show plan' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Include at least one vehicle to plan.');
    expect(calls.some((c) => c.url === '/api/v1/schedules/preview')).toBe(false);
  });

  it('lets viewers read but not run plans', async () => {
    mockApi(routes);
    renderWithApp(<PlannerPage />, { role: 'VIEWER' });
    expect(await screen.findByRole('button', { name: 'Show plan' })).toBeDisabled();
  });
});
