import { useState } from 'react';
import { useAdvisorStatus, useAsk, useDocuments, useWrite } from '../api/hooks';
import { can, useAuth } from '../auth/AuthContext';
import { Empty, ErrorNote, PageHeader } from '../components/Ui';

export function AdvisorPage() {
  const { user } = useAuth();
  const status = useAdvisorStatus();
  const docs = useDocuments();
  const ask = useAsk();
  const loadSamples = useWrite<Record<string, never>, unknown>(() => '/api/v1/advisor/documents/samples', [['documents']]);
  const [question, setQuestion] = useState('');
  const a = ask.data;

  return (
    <>
      <PageHeader title="Advisor" intro="Ask about your tariffs and charging policies. Answers quote your own documents and show where each fact came from." />
      {status.data && !status.data.chatModelConfigured && (
        <p className="text-sm text-ink-soft max-w-[68ch] -mt-3 mb-6">
          No AI model is connected, so you will see the most relevant passages instead of a written answer.
        </p>
      )}

      <form className="flex flex-wrap gap-2 items-end mb-6" onSubmit={(e) => { e.preventDefault(); if (question.trim()) ask.mutate(question); }}>
        <label className="lbl grow max-w-2xl">Question
          <input className="field" value={question} onChange={(e) => setQuestion(e.target.value)} placeholder="When is the off-peak period?" />
        </label>
        <button className="btn" disabled={ask.isPending}>{ask.isPending ? 'Looking…' : 'Ask'}</button>
      </form>
      <ErrorNote error={ask.error} />

      {a && (
        <article aria-live="polite" className="max-w-[72ch] mb-10">
          {a.answer && <p className="text-lg m-0">{a.answer}</p>}
          {a.answer && a.aiAvailable && !a.grounded && a.sources.length > 0 && (
            <p className="text-sm text-signal">This answer cites none of the passages below. Treat it with caution.</p>
          )}
          {a.sources.length > 0 && (
            <ol className="mt-4 pl-5 grid gap-3">
              {a.sources.map((s) => (
                <li key={s.ref} className={a.citations.some((c) => c.ref === `S${s.ref}`) ? '' : 'text-ink-soft'}>
                  <div className="font-medium">S{s.ref}: {s.title}</div>
                  <p className="m-0 text-sm">{s.text}</p>
                </li>
              ))}
            </ol>
          )}
        </article>
      )}

      <section aria-labelledby="docs-title">
        <div className="flex items-baseline gap-4 mb-2">
          <h2 id="docs-title">Documents</h2>
          {can(user?.role, 'ADMIN') && (
            <button type="button" className="btn btn-quiet text-sm" disabled={loadSamples.isPending} onClick={() => loadSamples.mutate({})}>
              Load sample documents
            </button>
          )}
        </div>
        <ErrorNote error={docs.error ?? loadSamples.error} />
        {docs.data?.length === 0 && <Empty>No documents yet. The advisor can only answer from documents you add.</Empty>}
        {!!docs.data?.length && (
          <table className="data max-w-3xl">
            <thead><tr><th>Title</th><th>Type</th><th className="text-right">Passages</th></tr></thead>
            <tbody>{docs.data.map((d) => (
              <tr key={d.id}><td>{d.title}</td><td>{d.type.toLowerCase()}</td><td className="text-right tabular">{d.chunks}</td></tr>
            ))}</tbody>
          </table>
        )}
      </section>
    </>
  );
}
