import { useEffect, useRef } from 'react';
import * as echarts from 'echarts/core';
import { CustomChart, LineChart } from 'echarts/charts';
import { GridComponent, MarkAreaComponent, MarkLineComponent, TooltipComponent, DataZoomComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import type { EChartsCoreOption } from 'echarts/core';

// Tree-shaken registration: only the chart types this app draws are bundled.
echarts.use([CustomChart, LineChart, GridComponent, MarkAreaComponent, MarkLineComponent, TooltipComponent, DataZoomComponent, CanvasRenderer]);

export function EChart({ option, height, label }: { option: EChartsCoreOption; height: number; label: string }) {
  const ref = useRef<HTMLDivElement>(null);
  const chart = useRef<echarts.ECharts | null>(null);

  useEffect(() => {
    if (!ref.current) return;
    chart.current = echarts.init(ref.current, undefined, { renderer: 'canvas' });
    const observer = new ResizeObserver(() => chart.current?.resize());
    observer.observe(ref.current);
    return () => {
      observer.disconnect();
      chart.current?.dispose();
    };
  }, []);

  useEffect(() => {
    chart.current?.setOption(option, { notMerge: true });
  }, [option]);

  return <div ref={ref} role="img" aria-label={label} style={{ height, width: '100%' }} />;
}
