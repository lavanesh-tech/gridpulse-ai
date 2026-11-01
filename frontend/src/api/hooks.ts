import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import type {
  AnalyticsSummary, Answer, Charger, Explanation, Fleet, GridConnection, KnowledgeDocument, PlanRequest, Preview,
  Recalculation, Station, Vehicle,
} from './types';

export const useFleets = () => useQuery({ queryKey: ['fleets'], queryFn: () => api<Fleet[]>('/api/v1/fleets') });

export const useVehicles = (fleetId: string | undefined) =>
  useQuery({
    queryKey: ['vehicles', fleetId],
    queryFn: () => api<Vehicle[]>(`/api/v1/fleets/${fleetId}/vehicles`),
    enabled: !!fleetId,
  });

export const useGrids = () => useQuery({ queryKey: ['grids'], queryFn: () => api<GridConnection[]>('/api/v1/grid-connections') });
export const useStations = () => useQuery({ queryKey: ['stations'], queryFn: () => api<Station[]>('/api/v1/stations') });

export const useChargers = (stationId: string | undefined) =>
  useQuery({
    queryKey: ['chargers', stationId],
    queryFn: () => api<Charger[]>(`/api/v1/stations/${stationId}/chargers`),
    enabled: !!stationId,
  });

export const useAnalytics = (from: string, to: string) =>
  useQuery({
    queryKey: ['analytics', from, to],
    queryFn: () => api<AnalyticsSummary>('/api/v1/analytics/summary', { query: { from, to } }),
  });

export const useDocuments = () =>
  useQuery({ queryKey: ['documents'], queryFn: () => api<KnowledgeDocument[]>('/api/v1/advisor/documents') });

export const useAdvisorStatus = () =>
  useQuery({
    queryKey: ['advisor-status'],
    queryFn: () => api<{ chatModelConfigured: boolean; embeddingModel: string }>('/api/v1/advisor/status'),
  });

/** A write that refreshes the listed queries when it succeeds. */
export function useWrite<TBody, TResult>(path: (body: TBody) => string, invalidate: string[][], method = 'POST') {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: TBody) => api<TResult>(path(body), { method, body }),
    onSuccess: () => invalidate.forEach((key) => qc.invalidateQueries({ queryKey: key })),
  });
}

export const usePreview = () => useMutation({ mutationFn: (plan: PlanRequest) => api<Preview>('/api/v1/schedules/preview', { body: plan }) });
export const useCompare = () =>
  useMutation({ mutationFn: (plan: PlanRequest) => api<Explanation>('/api/v1/advisor/explain-schedule', { body: plan }) });
export const useRecalculate = () =>
  useMutation({
    mutationFn: (req: { plan: PlanRequest; disruption: { at: string; chargersOutOfService: string[] } }) =>
      api<Recalculation>('/api/v1/schedules/recalculate', { body: req }),
  });
export const useAsk = () =>
  useMutation({ mutationFn: (question: string) => api<Answer>('/api/v1/advisor/ask', { body: { question } }) });

/** Every charger at every station, for timeline lanes. */
export function useAllChargers() {
  const stations = useStations();
  const results = useQueries({
    queries: (stations.data ?? []).map((s) => ({
      queryKey: ['chargers', s.id],
      queryFn: () => api<Charger[]>(`/api/v1/stations/${s.id}/chargers`),
    })),
  });
  return { chargers: results.flatMap((r) => r.data ?? []), isLoading: stations.isLoading || results.some((r) => r.isLoading) };
}
