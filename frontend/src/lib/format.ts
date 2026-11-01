const time = new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit' });
const dayTime = new Intl.DateTimeFormat(undefined, { weekday: 'short', hour: '2-digit', minute: '2-digit' });

export const fmtTime = (iso: string | number | Date) => time.format(new Date(iso));
export const fmtDayTime = (iso: string | number | Date) => dayTime.format(new Date(iso));
export const pct = (x: number, digits = 1) => `${(x * 100).toFixed(digits)}%`;
export const kw = (x: number) => `${Math.round(x).toLocaleString()} kW`;
export const kwh = (x: number) => `${Math.round(x).toLocaleString()} kWh`;
export const money = (x: number) => x.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
export const shortId = (id: string) => id.slice(0, 8);

const reasons: Record<string, string> = {
  NO_COMPATIBLE_CHARGER: 'No charger with a matching connector',
  NO_CAPACITY_BEFORE_DEPARTURE: 'No free capacity before departure',
  DEADLINE_MISSED: 'Partly charged by departure',
};
export const reasonText = (r: string) => reasons[r] ?? r;
