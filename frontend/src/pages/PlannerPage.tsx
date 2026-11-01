import { useEffect, useMemo, useState } from 'react';
import { useAllChargers, useCompare, useFleets, usePreview, useRecalculate, useVehicles } from '../api/hooks';
import type { Algorithm, Metrics, PlanRequest, Preview, Vehicle, VehicleNeed } from '../api/types';
import { can, useAuth } from '../auth/AuthContext';
import { NightStrip } from '../components/NightStrip';
import { Empty, ErrorNote, Figure, PageHeader } from '../components/Ui';
import { fmtDayTime, kwh, money, pct, reasonText } from '../lib/format';
import { horizonFor, toLocalInput, tonight, vehicleLabel } from '../lib/schedule';

const ALGORITHMS: { value: Algorithm; label: string; hint: string }[] = [
  { value: 'PRIORITY_SLACK', label: 'Deadlines first', hint: 'Urgent and tight vehicles first, charging as late as is safe.' },
  { value: 'PRIORITY_SLACK_COST', label: 'Cheapest energy', hint: 'Fills the cheapest price periods first. Can miss more departures.' },
  { value: 'BASELINE', label: 'First come, first served', hint: 'Full power on arrival. The comparison baseline.' },
];

interface Row { selected: boolean; targetSocPct: number; priority: number; arrival: string; departure: string }

export function PlannerPage() {
  const { user } = useAuth();
  const canPlan = can(user?.role, 'ADMIN', 'FLEET_MANAGER', 'OPERATOR');
  const fleets = useFleets();
  const [fleetId, setFleetId] = useState<string>();
  const activeFleet = fleetId ?? fleets.data?.[0]?.id;
  const vehicles = useVehicles(activeFleet);
  const { chargers } = useAllChargers();
  const [rows, setRows] = useState<Record<string, Row>>({});
  const [algorithm, setAlgorithm] = useState<Algorithm>('PRIORITY_SLACK');

  useEffect(() => {
    const { arrival, departure } = tonight();
    setRows((prev) => {
      const next = { ...prev };
      for (const v of vehicles.data ?? []) {
        next[v.id] ??= { selected: true, targetSocPct: 80, priority: 1, arrival: toLocalInput(arrival), departure: toLocalInput(departure) };
      }
      return next;
    });
  }, [vehicles.data]);

  const preview = usePreview();
  const compare = useCompare();
  const recalc = useRecalculate();
  const [plan, setPlan] = useState<PlanRequest>();
  const [planError, setPlanError] = useState<unknown>(null);

  const buildPlan = (): PlanRequest => {
    const needs: VehicleNeed[] = (vehicles.data ?? [])
      .filter((v) => rows[v.id]?.selected)
      .map((v) => ({
        vehicleId: v.id, targetSocPct: rows[v.id].targetSocPct, priority: rows[v.id].priority,
        arrival: new Date(rows[v.id].arrival).toISOString(), departure: new Date(rows[v.id].departure).toISOString(),
      }));
    return { ...horizonFor(needs), algorithm, vehicles: needs };
  };

  const run = () => {
    let p: PlanRequest;
    try {
      p = buildPlan();
    } catch (e) {
      preview.reset();
      setPlan(undefined);
      setPlanError(new Error('Include at least one vehicle to plan.'));
      return;
    }
    setPlanError(null);
    setPlan(p);
    recalc.reset();
    preview.mutate(p);
    compare.mutate(p);
  };

  const names = useMemo(() => new Map((vehicles.data ?? []).map((v) => [v.id, vehicleLabel(v)])), [vehicles.data]);
  const priorities = useMemo(() => new Map(plan?.vehicles.map((v) => [v.vehicleId, v.priority]) ?? []), [plan]);

  return (
    <>
      <PageHeader title="Plan charging" intro="Pick tonight's vehicles and targets. Nothing is booked: this shows what a plan would look like." />
      <ErrorNote error={fleets.error ?? vehicles.error} />
      {fleets.data?.length === 0 && <Empty>Create a fleet and add vehicles first.</Empty>}

      {!!vehicles.data?.length && (
        <section aria-labelledby="needs-title" className="mb-8">
          <div className="flex flex-wrap items-end gap-4 mb-3">
            <h2 id="needs-title" className="mr-auto">Vehicles tonight</h2>
            {(fleets.data?.length ?? 0) > 1 && (
              <label className="lbl">Fleet
                <select className="field" value={activeFleet} onChange={(e) => setFleetId(e.target.value)}>
                  {fleets.data!.map((f) => <option key={f.id} value={f.id}>{f.name}</option>)}
                </select>
              </label>
            )}
          </div>
          <NeedsTable vehicles={vehicles.data} rows={rows} setRows={setRows} />
          <fieldset className="mt-5 border-0 p-0">
            <legend className="font-medium mb-2">Optimise for</legend>
            <div className="flex flex-wrap gap-x-8 gap-y-2">
              {ALGORITHMS.map((a) => (
                <label key={a.value} className="flex gap-2 items-start max-w-xs">
                  <input type="radio" name="algorithm" className="mt-1.5" checked={algorithm === a.value} onChange={() => setAlgorithm(a.value)} />
                  <span><span className="font-medium">{a.label}</span><br /><span className="text-sm text-ink-soft">{a.hint}</span></span>
                </label>
              ))}
            </div>
          </fieldset>
          <button className="btn mt-5" type="button" onClick={run} disabled={!canPlan || preview.isPending}>
            {preview.isPending ? 'Planning…' : 'Show plan'}
          </button>
          {!canPlan && <p className="text-sm text-ink-soft">Viewers can read plans but not run them.</p>}
        </section>
      )}

      <ErrorNote error={planError ?? preview.error} />
      {plan && preview.data && (
        <PlanResult plan={plan} preview={preview.data} chargers={chargers} names={names} priorities={priorities}
          comparison={compare.data?.comparison} onWhatIf={(chargerId, at) => recalc.mutate({ plan, disruption: { at, chargersOutOfService: [chargerId] } })}
          whatIf={recalc} />
      )}
    </>
  );
}

function NeedsTable({ vehicles, rows, setRows }: { vehicles: Vehicle[]; rows: Record<string, Row>; setRows: (f: (r: Record<string, Row>) => Record<string, Row>) => void }) {
  const update = (id: string, patch: Partial<Row>) => setRows((r) => ({ ...r, [id]: { ...r[id], ...patch } }));
  return (
    <div className="overflow-x-auto">
      <table className="data">
        <thead><tr><th>Include</th><th>Vehicle</th><th className="text-right">Charge now</th><th>Target</th><th>Priority</th><th>Plugged in</th><th>Leaves</th></tr></thead>
        <tbody>
          {vehicles.map((v) => {
            const r = rows[v.id];
            if (!r) return null;
            return (
              <tr key={v.id}>
                <td><input type="checkbox" aria-label={`Include ${vehicleLabel(v)}`} checked={r.selected} onChange={(e) => update(v.id, { selected: e.target.checked })} /></td>
                <td>{vehicleLabel(v)} <span className="text-sm text-ink-soft">{v.connectorType}</span></td>
                <td className="text-right tabular">{v.stateOfChargePct}%</td>
                <td><input className="field w-20" type="number" min={0} max={100} aria-label={`Target for ${vehicleLabel(v)}`} value={r.targetSocPct} onChange={(e) => update(v.id, { targetSocPct: Number(e.target.value) })} />%</td>
                <td>
                  <select className="field" aria-label={`Priority for ${vehicleLabel(v)}`} value={r.priority} onChange={(e) => update(v.id, { priority: Number(e.target.value) })}>
                    <option value={2}>Urgent</option><option value={1}>Scheduled route</option><option value={0}>Spare</option>
                  </select>
                </td>
                <td><input className="field" type="datetime-local" aria-label={`Plug-in time for ${vehicleLabel(v)}`} value={r.arrival} onChange={(e) => update(v.id, { arrival: e.target.value })} /></td>
                <td><input className="field" type="datetime-local" aria-label={`Departure for ${vehicleLabel(v)}`} value={r.departure} onChange={(e) => update(v.id, { departure: e.target.value })} /></td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function Outcome({ m }: { m: Metrics }) {
  const missed = m.vehiclesNeedingCharge - m.deadlinesMet;
  return (
    <div className="grid grid-cols-2 md:grid-cols-4 gap-6">
      <Figure value={`${m.deadlinesMet} of ${m.vehiclesNeedingCharge}`} label="Leave on time with their target charge" tone={missed ? 'bad' : 'ok'} />
      <Figure value={money(m.cost.totalCost)} label={`Estimated energy cost, ${money(m.cost.averagePricePerKwh)} per kWh`} />
      <Figure value={kwh(m.energyDelivered.value)} label="Energy planned" />
      <Figure value={pct(m.chargerUtilization, 0)} label="Charger time in use" />
    </div>
  );
}

interface ResultProps {
  plan: PlanRequest; preview: Preview; chargers: ReturnType<typeof useAllChargers>['chargers'];
  names: Map<string, string>; priorities: Map<string, number>;
  comparison?: { algorithm: Algorithm; deadlinesMet: number; estimatedCost: number; hardConstraintViolations: number }[];
  onWhatIf: (chargerId: string, at: string) => void; whatIf: ReturnType<typeof useRecalculate>;
}

function PlanResult({ plan, preview, chargers, names, priorities, comparison, onWhatIf, whatIf }: ResultProps) {
  const [chargerId, setChargerId] = useState('');
  const [at, setAt] = useState(toLocalInput(new Date(Date.parse(plan.horizonStart) + 3 * 3600_000)));
  const after = whatIf.data;
  const label = (a: string) => ALGORITHMS.find((x) => x.value === a)?.label ?? a;

  return (
    <section aria-labelledby="result-title" className="grid gap-8">
      <div>
        <h2 id="result-title" className="mb-4">{label(plan.algorithm)}: the night ahead</h2>
        <Outcome m={preview.metrics} />
        {preview.violations.length > 0 && <ErrorNote error={new Error(`${preview.violations.length} hard-constraint violations. Do not book this plan.`)} />}
        {preview.existingBookingsConsidered > 0 && (
          <p className="text-sm text-ink-soft mt-3">Planned around {preview.existingBookingsConsidered} existing booking{preview.existingBookingsConsidered > 1 ? 's' : ''}.</p>
        )}
      </div>

      <NightStrip schedule={after?.result.schedule ?? preview.schedule} chargers={chargers}
        profiles={after?.gridProfilesAfter ?? preview.gridProfiles} horizonStart={plan.horizonStart} slotMinutes={plan.slotMinutes}
        slotCount={plan.slotCount} priorities={priorities} vehicleNames={names} replannedFrom={after?.result.replannedFrom} />

      {preview.schedule.unmet.length > 0 && (
        <div>
          <h2 className="mb-2">Vehicles that will not reach their target</h2>
          <table className="data">
            <thead><tr><th>Vehicle</th><th>Why</th><th className="text-right">Needed</th><th className="text-right">Planned</th></tr></thead>
            <tbody>{preview.schedule.unmet.map((u) => (
              <tr key={u.vehicleId}><td>{names.get(u.vehicleId) ?? u.vehicleId.slice(0, 8)}</td><td>{reasonText(u.reason)}</td>
                <td className="text-right tabular">{kwh(u.required.value)}</td><td className="text-right tabular">{kwh(u.delivered.value)}</td></tr>
            ))}</tbody>
          </table>
        </div>
      )}

      {comparison && (
        <div>
          <h2 className="mb-2">Same vehicles, other strategies</h2>
          <table className="data max-w-2xl">
            <thead><tr><th>Strategy</th><th className="text-right">On time</th><th className="text-right">Estimated cost</th><th className="text-right">Violations</th></tr></thead>
            <tbody>{comparison.map((c) => (
              <tr key={c.algorithm} className={c.algorithm === plan.algorithm ? 'font-medium' : ''}>
                <td>{label(c.algorithm)}</td><td className="text-right tabular">{c.deadlinesMet} of {preview.metrics.vehiclesNeedingCharge}</td>
                <td className="text-right tabular">{money(c.estimatedCost)}</td><td className="text-right tabular">{c.hardConstraintViolations}</td></tr>
            ))}</tbody>
          </table>
        </div>
      )}

      <form className="grid gap-3" onSubmit={(e) => { e.preventDefault(); if (chargerId) onWhatIf(chargerId, new Date(at).toISOString()); }}>
        <h2>What if a charger fails?</h2>
        <p className="text-ink-soft m-0 max-w-[68ch]">Pick a charger and a time. GridPulse keeps what already charged, moves the rest to other chargers, and shows who would now miss departure.</p>
        <div className="flex flex-wrap gap-3 items-end">
          <label className="lbl">Charger
            <select className="field" value={chargerId} onChange={(e) => setChargerId(e.target.value)} required>
              <option value="">Choose…</option>
              {chargers.map((c) => <option key={c.id} value={c.id}>{c.label}</option>)}
            </select>
          </label>
          <label className="lbl">Fails at<input className="field" type="datetime-local" value={at} onChange={(e) => setAt(e.target.value)} /></label>
          <button className="btn btn-quiet" disabled={!chargerId || whatIf.isPending}>{whatIf.isPending ? 'Re-planning…' : 'Simulate failure'}</button>
        </div>
        <ErrorNote error={whatIf.error} />
        {after && (
          <div className="border-l-4 border-indigo pl-4 py-1">
            <p className="m-0">
              Re-planned from {fmtDayTime(after.result.replannedFrom)}: {after.result.sessionsInterrupted} session interrupted,
              {' '}{after.result.sessionsReplanned} moved. {after.metricsAfter.deadlinesMet} of {after.metricsAfter.vehiclesNeedingCharge} still leave on time.
            </p>
            {after.result.newlyUnmetVehicles.length > 0 && (
              <p className="m-0 mt-1 text-signal">Now missing departure: {after.result.newlyUnmetVehicles.map((v) => names.get(v) ?? v.slice(0, 8)).join(', ')}</p>
            )}
            <button type="button" className="underline text-sm mt-2" onClick={() => whatIf.reset()}>Back to the original plan</button>
          </div>
        )}
      </form>
    </section>
  );
}
