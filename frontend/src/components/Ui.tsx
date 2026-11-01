import type { ReactNode } from 'react';
import { ApiError } from '../api/client';

export function PageHeader({ title, intro, children }: { title: string; intro?: string; children?: ReactNode }) {
  return (
    <header className="flex flex-wrap items-end justify-between gap-4 mb-6">
      <div className="max-w-[68ch]">
        <h1>{title}</h1>
        {intro && <p className="text-ink-soft mt-1">{intro}</p>}
      </div>
      {children}
    </header>
  );
}

/** Shows the server's own problem detail and any per-field messages. */
export function ErrorNote({ error }: { error: unknown }) {
  if (!error) return null;
  const message = error instanceof Error ? error.message : 'Something went wrong';
  const fields = error instanceof ApiError ? error.problem.errors : undefined;
  return (
    <div role="alert" className="border-l-4 border-signal bg-paper px-3 py-2 text-sm my-3">
      <p className="m-0">{message}</p>
      {fields && (
        <ul className="m-0 mt-1 pl-4">
          {Object.entries(fields).map(([f, m]) => <li key={f}>{f}: {m}</li>)}
        </ul>
      )}
    </div>
  );
}

export function Figure({ value, label, tone }: { value: ReactNode; label: string; tone?: 'ok' | 'bad' }) {
  const color = tone === 'bad' ? 'text-signal' : tone === 'ok' ? 'text-teal' : 'text-ink';
  return (
    <div>
      <div className={`figure text-3xl leading-none ${color}`}>{value}</div>
      <div className="text-sm text-ink-soft mt-1">{label}</div>
    </div>
  );
}

export function Empty({ children }: { children: ReactNode }) {
  return <p className="text-ink-soft border border-dashed border-rule px-4 py-6 my-2">{children}</p>;
}
