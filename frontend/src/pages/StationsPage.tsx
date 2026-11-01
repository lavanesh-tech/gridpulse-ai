import { useState } from 'react';
import { useChargers, useGrids, useStations, useWrite } from '../api/hooks';
import type { Charger, ConnectorType, GridConnection, Station } from '../api/types';
import { can, useAuth } from '../auth/AuthContext';
import { Empty, ErrorNote, PageHeader } from '../components/Ui';

export function StationsPage() {
  const { user } = useAuth();
  const stations = useStations();
  const grids = useGrids();
  const isAdmin = can(user?.role, 'ADMIN');
  const canOperate = can(user?.role, 'ADMIN', 'OPERATOR');
  const gridName = new Map(grids.data?.map((g) => [g.id, `${g.name} (${Math.round(g.capacityLimitKw)} kW)`]));

  const outage = useWrite<{ id: string }, Station>((b) => `/api/v1/stations/${b.id}/outage`, [['stations']]);
  const restore = useWrite<{ id: string }, Station>((b) => `/api/v1/stations/${b.id}/restore`, [['stations']]);

  return (
    <>
      <PageHeader title="Stations" intro="Where vehicles charge, which grid connection feeds them, and what is out of service." />
      <ErrorNote error={stations.error ?? outage.error ?? restore.error} />
      {stations.data?.length === 0 && <Empty>No stations yet{isAdmin ? '. Add a grid connection, then a station below.' : '.'}</Empty>}
      <div className="grid gap-8">
        {stations.data?.map((s) => (
          <section key={s.id} aria-labelledby={`st-${s.id}`}>
            <div className="flex flex-wrap items-baseline gap-x-4 gap-y-1 mb-2">
              <h2 id={`st-${s.id}`}>{s.name}</h2>
              <span className="text-ink-soft text-sm">{gridName.get(s.gridConnectionId)}</span>
              <span className={`text-sm font-medium ${s.status === 'ONLINE' ? 'text-teal' : 'text-signal'}`}>
                {s.status === 'ONLINE' ? 'Online' : 'Offline: excluded from new plans'}
              </span>
              {canOperate && (s.status === 'ONLINE'
                ? <button type="button" className="btn btn-quiet text-sm ml-auto" onClick={() => outage.mutate({ id: s.id })}>Report outage</button>
                : <button type="button" className="btn text-sm ml-auto" onClick={() => restore.mutate({ id: s.id })}>Restore station</button>)}
            </div>
            <ChargerTable stationId={s.id} canAdd={isAdmin} />
          </section>
        ))}
      </div>
      {isAdmin && grids.data && <AddStation grids={grids.data} />}
    </>
  );
}

function ChargerTable({ stationId, canAdd }: { stationId: string; canAdd: boolean }) {
  const chargers = useChargers(stationId);
  const add = useWrite<Record<string, unknown>, Charger>(() => `/api/v1/stations/${stationId}/chargers`, [['chargers', stationId]]);
  const [label, setLabel] = useState('');
  const [connectorType, setConnector] = useState<ConnectorType>('CCS1');
  const [maxPowerKw, setPower] = useState(150);
  return (
    <>
      {chargers.data?.length === 0 && <Empty>No chargers at this station.</Empty>}
      {!!chargers.data?.length && (
        <table className="data mb-2">
          <thead><tr><th>Charger</th><th>Connector</th><th className="text-right">Max power</th><th>Status</th></tr></thead>
          <tbody>
            {chargers.data.map((c) => (
              <tr key={c.id}>
                <td className="figure">{c.label}</td><td>{c.connectorType}</td>
                <td className="text-right tabular">{c.maxPowerKw} kW</td>
                <td className={c.status === 'AVAILABLE' ? '' : 'text-signal'}>{c.status === 'AVAILABLE' ? 'Available' : 'Out of service'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {canAdd && (
        <form className="flex flex-wrap gap-2 items-end" onSubmit={(e) => {
          e.preventDefault();
          add.mutate({ label, connectorType, maxPowerKw }, { onSuccess: () => setLabel('') });
        }}>
          <label className="lbl">Label<input className="field w-24" value={label} onChange={(e) => setLabel(e.target.value)} required /></label>
          <label className="lbl">Connector
            <select className="field" value={connectorType} onChange={(e) => setConnector(e.target.value as ConnectorType)}>
              {['CCS1', 'NACS', 'J1772', 'CCS2', 'CHADEMO', 'TYPE2'].map((c) => <option key={c}>{c}</option>)}
            </select>
          </label>
          <label className="lbl">Max kW<input className="field w-24" type="number" min={1} value={maxPowerKw} onChange={(e) => setPower(Number(e.target.value))} /></label>
          <button className="btn btn-quiet" disabled={add.isPending}>Add charger</button>
          <ErrorNote error={add.error} />
        </form>
      )}
    </>
  );
}

function AddStation({ grids }: { grids: GridConnection[] }) {
  const addGrid = useWrite<Record<string, unknown>, GridConnection>(() => '/api/v1/grid-connections', [['grids']]);
  const addStation = useWrite<Record<string, unknown>, Station>(() => '/api/v1/stations', [['stations']]);
  const [grid, setGrid] = useState({ name: '', capacityLimitKw: 600, timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone });
  const [station, setStation] = useState({ name: '', gridConnectionId: '' });
  const gridId = station.gridConnectionId || grids[0]?.id || '';
  return (
    <section aria-labelledby="add-title" className="mt-12 grid md:grid-cols-2 gap-8">
      <h2 id="add-title" className="md:col-span-2">Add capacity</h2>
      <form className="grid gap-2" onSubmit={(e) => { e.preventDefault(); addGrid.mutate(grid); }}>
        <h3 className="m-0 font-medium">Grid connection</h3>
        <label className="lbl">Name<input className="field" value={grid.name} onChange={(e) => setGrid({ ...grid, name: e.target.value })} required /></label>
        <label className="lbl">Contracted limit (kW)<input className="field" type="number" min={1} value={grid.capacityLimitKw}
          onChange={(e) => setGrid({ ...grid, capacityLimitKw: Number(e.target.value) })} /></label>
        <label className="lbl">Time zone for tariffs<input className="field" value={grid.timeZone} onChange={(e) => setGrid({ ...grid, timeZone: e.target.value })} /></label>
        <ErrorNote error={addGrid.error} />
        <button className="btn btn-quiet justify-self-start" disabled={addGrid.isPending}>Add grid connection</button>
      </form>
      <form className="grid gap-2" onSubmit={(e) => { e.preventDefault(); addStation.mutate({ name: station.name, gridConnectionId: gridId }); }}>
        <h3 className="m-0 font-medium">Station</h3>
        <label className="lbl">Name<input className="field" value={station.name} onChange={(e) => setStation({ ...station, name: e.target.value })} required /></label>
        <label className="lbl">Fed by
          <select className="field" value={gridId} onChange={(e) => setStation({ ...station, gridConnectionId: e.target.value })} required>
            {grids.map((g) => <option key={g.id} value={g.id}>{g.name}</option>)}
          </select>
        </label>
        <ErrorNote error={addStation.error} />
        <button className="btn btn-quiet justify-self-start" disabled={addStation.isPending || !grids.length}>Add station</button>
      </form>
    </section>
  );
}
