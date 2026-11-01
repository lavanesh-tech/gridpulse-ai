import { lazy, Suspense } from 'react';
import { NavLink, Navigate, Outlet, Route, Routes } from 'react-router';
import { useAuth } from './auth/AuthContext';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { FleetsPage } from './pages/FleetsPage';
import { StationsPage } from './pages/StationsPage';
import { AdvisorPage } from './pages/AdvisorPage';

// The planner pulls in ECharts; load it only when someone opens it.
const PlannerPage = lazy(() => import('./pages/PlannerPage').then((m) => ({ default: m.PlannerPage })));

const NAV = [
  { to: '/', label: 'Tonight', end: true },
  { to: '/planner', label: 'Plan charging' },
  { to: '/fleets', label: 'Fleets' },
  { to: '/stations', label: 'Stations' },
  { to: '/advisor', label: 'Advisor' },
];

function Shell() {
  const { user, signOut } = useAuth();
  if (!user) return <Navigate to="/sign-in" replace />;
  return (
    <div className="min-h-screen md:grid md:grid-cols-[13rem_1fr]">
      <nav aria-label="Main" className="bg-ink text-paper md:min-h-screen px-4 py-4 flex md:flex-col gap-1 overflow-x-auto">
        <div className="figure text-xl mr-4 md:mr-0 md:mb-6 whitespace-nowrap">GridPulse</div>
        {NAV.map((n) => (
          <NavLink key={n.to} to={n.to} end={n.end}
            className={({ isActive }) => `px-2 py-1.5 rounded-sm whitespace-nowrap ${isActive ? 'bg-paper text-ink' : 'text-paper/80 hover:text-paper'}`}>
            {n.label}
          </NavLink>
        ))}
        <div className="md:mt-auto ml-auto md:ml-0 pt-4 text-sm text-paper/70 whitespace-nowrap">
          <div>{user.username}</div>
          <div className="mb-2">{user.role.replace('_', ' ').toLowerCase()}</div>
          <button type="button" className="underline text-paper" onClick={signOut}>Sign out</button>
        </div>
      </nav>
      <main className="px-5 md:px-10 py-8 max-w-[1280px]">
        <Suspense fallback={<p className="text-ink-soft">Loading…</p>}>
          <Outlet />
        </Suspense>
      </main>
    </div>
  );
}

export default function App() {
  return (
    <Routes>
      <Route path="/sign-in" element={<LoginPage />} />
      <Route element={<Shell />}>
        <Route index element={<DashboardPage />} />
        <Route path="planner" element={<PlannerPage />} />
        <Route path="fleets" element={<FleetsPage />} />
        <Route path="stations" element={<StationsPage />} />
        <Route path="advisor" element={<AdvisorPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
