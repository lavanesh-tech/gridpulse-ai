import { useState, type FormEvent } from 'react';
import { useFleets, useVehicles, useWrite } from '../api/hooks';
import type { ConnectorType, Fleet, Vehicle } from '../api/types';
import { useAuth, can } from '../auth/AuthContext';
import { Empty, ErrorNote, PageHeader } from '../components/Ui';

const CONNECTORS: ConnectorType[] = ['CCS1', 'NACS', 'J1772', 'CCS2', 'CHADEMO', 'TYPE2'];

export function FleetsPage() {
  const { user } = useAuth();
  const fleets = useFleets();
  const [selected, setSelected] = useState<string>();
  const fleetId = selected ?? fleets.data?.[0]?.id;
  const vehicles = useVehicles(fleetId);
  const canEdit = can(user?.role, 'ADMIN', 'FLEET_MANAGER');

  const createFleet = useWrite<{ name: string }, Fleet>(() => '/api/v1/fleets', [['fleets']]);
  const [fleetName, setFleetName] = useState('');

  return (
    <>
      <PageHeader title="Fleets" intro="Vehicles, their batteries and connectors. The planner uses these specs." />
      <ErrorNote error={fleets.error ?? createFleet.error} />
      <div className="grid md:grid-cols-[16rem_1fr] gap-8">
        <section aria-labelledby="fleet-list">
          <h2 id="fleet-list" className="mb-2">Your fleets</h2>
          {fleets.data?.length === 0 && <Empty>No fleets yet.</Empty>}
          <ul className="m-0 p-0 list-none grid gap-1">
            {fleets.data?.map((f) => (
              <li key={f.id}>
                <button type="button" onClick={() => setSelected(f.id)} aria-pressed={f.id === fleetId}
                  className={`w-full text-left px-2 py-1.5 rounded-sm ${f.id === fleetId ? 'bg-ink text-paper' : 'hover:bg-paper'}`}>
                  {f.name}
                </button>
              </li>
            ))}
          </ul>
          {canEdit && (
            <form className="grid gap-2 mt-4" onSubmit={(e: FormEvent) => {
              e.preventDefault();
              createFleet.mutate({ name: fleetName }, { onSuccess: () => setFleetName('') });
            }}>
              <label className="lbl">New fleet name<input className="field" value={fleetName} onChange={(e) => setFleetName(e.target.value)} required /></label>
              <button className="btn btn-quiet" disabled={createFleet.isPending}>Create fleet</button>
            </form>
          )}
        </section>

        <section aria-labelledby="vehicle-list">
          <h2 id="vehicle-list" className="mb-2">Vehicles</h2>
          <ErrorNote error={vehicles.error} />
          {fleetId && vehicles.data?.length === 0 && <Empty>This fleet has no vehicles yet.</Empty>}
          {!!vehicles.data?.length && (
            <table className="data">
              <thead><tr><th>Model</th><th>VIN</th><th>Connector</th><th className="text-right">Battery</th><th className="text-right">Max rate</th><th className="text-right">Charge now</th></tr></thead>
              <tbody>
                {vehicles.data.map((v) => (
                  <tr key={v.id}>
                    <td>{v.model}</td><td className="tabular text-sm">{v.vin}</td><td className="figure">{v.connectorType}</td>
                    <td className="text-right tabular">{v.batteryCapacityKwh} kWh</td>
                    <td className="text-right tabular">{v.maxChargeRateKw} kW</td>
                    <td className="text-right tabular">{v.stateOfChargePct}%</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          {canEdit && fleetId && <AddVehicle fleetId={fleetId} />}
        </section>
      </div>
    </>
  );
}

function AddVehicle({ fleetId }: { fleetId: string }) {
  const add = useWrite<Record<string, unknown>, Vehicle>(() => `/api/v1/fleets/${fleetId}/vehicles`, [['vehicles', fleetId]]);
  const [form, setForm] = useState({ vin: '', model: '', batteryCapacityKwh: 100, maxChargeRateKw: 150, connectorType: 'CCS1', stateOfChargePct: 30 });
  const set = (k: string, v: string | number) => setForm({ ...form, [k]: v });
  return (
    <form className="mt-6 grid gap-3 md:grid-cols-3 items-end" onSubmit={(e) => {
      e.preventDefault();
      add.mutate(form, { onSuccess: () => set('vin', '') });
    }}>
      <h3 className="md:col-span-3 m-0 font-medium">Add a vehicle</h3>
      <label className="lbl">VIN (17 characters)<input className="field uppercase" value={form.vin} maxLength={17} onChange={(e) => set('vin', e.target.value.toUpperCase())} required /></label>
      <label className="lbl">Model<input className="field" value={form.model} onChange={(e) => set('model', e.target.value)} required /></label>
      <label className="lbl">Connector
        <select className="field" value={form.connectorType} onChange={(e) => set('connectorType', e.target.value)}>
          {CONNECTORS.map((c) => <option key={c}>{c}</option>)}
        </select>
      </label>
      <label className="lbl">Battery (kWh)<input className="field" type="number" min={1} value={form.batteryCapacityKwh} onChange={(e) => set('batteryCapacityKwh', Number(e.target.value))} /></label>
      <label className="lbl">Max charge rate (kW)<input className="field" type="number" min={1} value={form.maxChargeRateKw} onChange={(e) => set('maxChargeRateKw', Number(e.target.value))} /></label>
      <label className="lbl">Charge now (%)<input className="field" type="number" min={0} max={100} value={form.stateOfChargePct} onChange={(e) => set('stateOfChargePct', Number(e.target.value))} /></label>
      <div className="md:col-span-3"><ErrorNote error={add.error} /><button className="btn" disabled={add.isPending}>Add vehicle</button></div>
    </form>
  );
}
