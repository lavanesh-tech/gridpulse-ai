import { useMemo } from 'react';
import type { CustomSeriesRenderItemAPI, CustomSeriesRenderItemParams } from 'echarts';
import { EChart } from '../charts/EChart';
import type { Charger, GridProfile, Schedule } from '../api/types';
import { fmtTime } from '../lib/format';
import { loadSeries, tariffBands, toLanes } from '../lib/schedule';

const BAND_FILL = { peak: 'rgba(180,35,44,0.08)', offpeak: 'rgba(59,74,140,0.11)', shoulder: 'rgba(199,122,18,0.05)' };
const BAR_FILL: Record<number, string> = { 2: '#8a4f05', 1: '#c77a12' };
const barFill = (p: number) => (p >= 2 ? BAR_FILL[2] : BAR_FILL[p] ?? '#e2b46a');
const GRID = { left: 72, right: 24 };

interface Props {
  schedule: Schedule;
  chargers: Charger[];
  profiles: GridProfile[];
  horizonStart: string;
  slotMinutes: number;
  slotCount: number;
  priorities: Map<string, number>;
  vehicleNames: Map<string, string>;
  replannedFrom?: string;
}

/**
 * The night at a glance: one lane per charger with each charging session as a bar (darker = higher priority),
 * tariff periods tinted behind, and below it each grid connection's planned load against its limit.
 */
export function NightStrip(p: Props) {
  const from = Date.parse(p.horizonStart);
  const to = from + p.slotCount * p.slotMinutes * 60_000;
  const { lanes, bars } = useMemo(() => toLanes(p.schedule.sessions, p.chargers, p.priorities), [p.schedule, p.chargers, p.priorities]);
  const bands = useMemo(() => tariffBands(from, to), [from, to]);

  const bandAreas = bands.map((b) => [
    { xAxis: b.start, itemStyle: { color: BAND_FILL[b.kind] } },
    { xAxis: b.end },
  ]);
  const replanLine = p.replannedFrom
    ? { symbol: 'none', silent: true, label: { formatter: 'Re-planned from here', color: '#b4232c' },
        lineStyle: { color: '#b4232c', type: 'dashed' as const }, data: [{ xAxis: Date.parse(p.replannedFrom) }] }
    : undefined;

  const timeAxis = {
    type: 'time' as const, min: from, max: to, splitNumber: 8,
    axisLabel: { formatter: (v: number) => fmtTime(v), color: '#4d5b68', fontFamily: 'Barlow Semi Condensed' },
    axisLine: { lineStyle: { color: '#d3dae0' } }, splitLine: { show: true, lineStyle: { color: '#e3e8ec' } },
  };

  const laneOption = {
    animation: false,
    grid: { ...GRID, top: 8, bottom: 28 },
    xAxis: timeAxis,
    yAxis: {
      type: 'category' as const, data: lanes.map((l) => l.label), inverse: true,
      axisLabel: { color: '#1b2733', fontFamily: 'Barlow Semi Condensed', fontWeight: 600 },
      axisTick: { show: false }, axisLine: { lineStyle: { color: '#d3dae0' } },
    },
    tooltip: {
      formatter: (x: { data: { value: [number, number, number, string, number] } }) => {
        const [lane, start, end, vehicle, power] = x.data.value;
        return `${p.vehicleNames.get(vehicle) ?? vehicle.slice(0, 8)} on ${lanes[lane]?.label}<br/>${fmtTime(start)}–${fmtTime(end)}, ${power} kW`;
      },
    },
    series: [{
      type: 'custom' as const,
      encode: { x: [1, 2], y: 0 },
      data: bars.map((b) => ({ value: [b.lane, b.start, b.end, b.vehicleId, b.powerKw], itemStyle: { color: barFill(b.priority) } })),
      renderItem: (_params: CustomSeriesRenderItemParams, api: CustomSeriesRenderItemAPI) => {
        const lane = api.value(0) as number;
        const start = api.coord([api.value(1), lane]);
        const end = api.coord([api.value(2), lane]);
        const height = (api.size?.([0, 1]) as number[])[1] * 0.56;
        return {
          type: 'rect',
          shape: { x: start[0], y: start[1] - height / 2, width: Math.max(2, end[0] - start[0] - 1), height },
          style: api.style(),
        };
      },
      markArea: { silent: true, data: bandAreas },
      markLine: replanLine,
    }],
  };

  const loadOption = {
    animation: false,
    grid: { ...GRID, top: 24, bottom: 28 },
    xAxis: timeAxis,
    yAxis: {
      type: 'value' as const, name: 'kW', nameTextStyle: { color: '#4d5b68' },
      axisLabel: { color: '#4d5b68', fontFamily: 'Barlow Semi Condensed' }, splitLine: { lineStyle: { color: '#e3e8ec' } },
    },
    tooltip: { trigger: 'axis' as const, valueFormatter: (v: number) => `${Math.round(v)} kW` },
    series: p.profiles.map((g, i) => ({
      type: 'line' as const, step: 'start' as const, symbol: 'none', name: `Grid ${i + 1}`,
      data: loadSeries(g, p.horizonStart, p.slotMinutes),
      lineStyle: { color: '#c77a12', width: 1.5 }, areaStyle: { color: 'rgba(199,122,18,0.18)' },
      markLine: {
        symbol: 'none', silent: true, label: { formatter: `Limit ${Math.round(g.capacity.value)} kW`, color: '#1f7a74' },
        lineStyle: { color: '#1f7a74', type: 'solid' as const, width: 1.5 }, data: [{ yAxis: g.capacity.value }],
      },
      markArea: i === 0 ? { silent: true, data: bandAreas } : undefined,
    })),
  };

  return (
    <figure className="m-0">
      <EChart option={laneOption} height={Math.max(140, lanes.length * 34 + 40)} label="Charging sessions per charger over the night" />
      <EChart option={loadOption} height={200} label="Planned grid load against the capacity limit" />
      <figcaption className="flex flex-wrap gap-x-6 gap-y-1 text-sm text-ink-soft mt-2" style={{ marginLeft: GRID.left }}>
        <Key color={BAND_FILL.peak} text="Peak price" />
        <Key color={BAND_FILL.offpeak} text="Off-peak price" />
        <Key color={BAND_FILL.shoulder} text="Shoulder price" />
        <Key color={BAR_FILL[2]} text="Urgent vehicle" solid />
        <Key color={BAR_FILL[1]} text="Scheduled route" solid />
        <Key color="#e2b46a" text="Spare vehicle" solid />
      </figcaption>
    </figure>
  );
}

function Key({ color, text, solid }: { color: string; text: string; solid?: boolean }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <span aria-hidden className="inline-block w-3.5 h-3.5" style={{ background: color, border: solid ? 'none' : '1px solid #d3dae0' }} />
      {text}
    </span>
  );
}
