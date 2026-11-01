import { useMemo } from 'react';
import { Link } from 'react-router';
import { useAnalytics } from '../api/hooks';
import { Empty, ErrorNote, Figure, PageHeader } from '../components/Ui';
import { kwh, pct } from '../lib/format';

/** Rolling window: from 18:00 yesterday to 18:00 tomorrow, local time. */
function window() {
  const from = new Date();
  from.setDate(from.getDate() - 1);
  from.setHours(18, 0, 0, 0);
  const to = new Date(from);
  to.setDate(to.getDate() + 2);
  return { from: from.toISOString(), to: to.toISOString() };
}

export function DashboardPage() {
  const { from, to } = useMemo(window, []);
  const q = useAnalytics(from, to);
  const s = q.data;
  const reserved = (s?.sessionsByStatus.RESERVED ?? 0) + (s?.sessionsByStatus.ACTIVE ?? 0);
  const interrupted = s?.sessionsByStatus.INTERRUPTED ?? 0;

  return (
    <>
      <PageHeader title="Tonight at the depot" intro="Booked charging from yesterday evening to tomorrow evening.">
        <Link className="btn" to="/planner">Plan charging</Link>
      </PageHeader>
      <ErrorNote error={q.error} />
      {s && (
        <>
          <section aria-label="Totals" className="grid grid-cols-2 md:grid-cols-4 gap-6 mb-10">
            <Figure value={reserved} label="Sessions booked or running" />
            <Figure value={kwh(s.scheduledEnergyKwh)} label="Energy scheduled" />
            <Figure value={s.vehiclesServed} label="Vehicles served" />
            <Figure value={interrupted} label="Sessions interrupted" tone={interrupted ? 'bad' : undefined} />
          </section>
          <section aria-labelledby="usage-title">
            <h2 id="usage-title" className="mb-2">Charger usage</h2>
            {s.chargers.length === 0 ? (
              <Empty>No chargers yet. <Link className="underline" to="/stations">Add a station and its chargers</Link> to start booking.</Empty>
            ) : (
              <table className="data">
                <thead><tr><th>Charger</th><th>Station</th><th className="text-right">Sessions</th><th className="text-right">Hours in use</th><th>Utilisation</th></tr></thead>
                <tbody>
                  {s.chargers.map((c) => (
                    <tr key={c.chargerId}>
                      <td className="figure">{c.label}</td>
                      <td>{c.station}</td>
                      <td className="text-right tabular">{c.sessions}</td>
                      <td className="text-right tabular">{c.occupiedHours.toFixed(1)}</td>
                      <td>
                        <div className="flex items-center gap-2">
                          <div className="h-2 bg-rule w-32" aria-hidden>
                            <div className="h-2 bg-amber" style={{ width: pct(Math.min(1, c.utilization), 0) }} />
                          </div>
                          <span className="tabular text-sm">{pct(c.utilization, 0)}</span>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>
        </>
      )}
    </>
  );
}
