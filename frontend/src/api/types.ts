// Shapes returned by the GridPulse REST API (see API_DOCUMENTATION.md).

export type Role = 'ADMIN' | 'FLEET_MANAGER' | 'OPERATOR' | 'VIEWER';
export type ConnectorType = 'J1772' | 'CCS1' | 'CCS2' | 'NACS' | 'CHADEMO' | 'TYPE2';
export type Algorithm = 'BASELINE' | 'PRIORITY_SLACK' | 'PRIORITY_SLACK_COST';

export interface Fleet { id: string; name: string; createdAt: string }
export interface Vehicle {
  id: string; fleetId: string; vin: string; model: string; batteryCapacityKwh: number;
  maxChargeRateKw: number; connectorType: ConnectorType; stateOfChargePct: number;
}
export interface GridConnection { id: string; name: string; capacityLimitKw: number; timeZone: string }
export interface Station { id: string; gridConnectionId: string; name: string; status: 'ONLINE' | 'OFFLINE' | 'MAINTENANCE' }
export interface Charger {
  id: string; stationId: string; label: string; connectorType: ConnectorType; maxPowerKw: number;
  status: 'AVAILABLE' | 'OUT_OF_SERVICE';
}

export interface Window { start: string; end: string }
export interface Power { value: number }
export interface Energy { value: number }
export interface ScheduledSession { vehicleId: string; chargerId: string; window: Window; power: Power }
export interface UnmetRequest {
  vehicleId: string; reason: 'NO_COMPATIBLE_CHARGER' | 'NO_CAPACITY_BEFORE_DEPARTURE' | 'DEADLINE_MISSED';
  required: Energy; delivered: Energy;
}
export interface Schedule { algorithm: string; sessions: ScheduledSession[]; unmet: UnmetRequest[] }
export interface PriorityOutcome { priority: number; needingCharge: number; met: number; successRate: number }
export interface Metrics {
  algorithm: string; vehicles: number; vehiclesNeedingCharge: number; sessions: number; deadlinesMet: number;
  deadlineSuccessRate: number; violations: number; chargerUtilization: number; energyDelivered: Energy;
  energyRequired: Energy; byPriority: PriorityOutcome[];
  cost: { totalCost: number; energy: Energy; averagePricePerKwh: number };
}
export interface GridProfile {
  gridId: string; capacity: Power; loadBySlot: Power[]; peak: Power; peakUtilization: number; bottleneckSlots: number[];
}
export interface Violation { type: string; detail: string }
export interface Preview {
  schedule: Schedule; violations: Violation[]; metrics: Metrics; gridProfiles: GridProfile[];
  existingBookingsConsidered: number;
}
export interface Recalculation {
  before: Preview;
  result: {
    schedule: Schedule; replannedFrom: string; sessionsKept: number; sessionsInterrupted: number;
    sessionsReplanned: number; newlyUnmetVehicles: string[]; violations: Violation[];
  };
  metricsAfter: Metrics;
  gridProfilesAfter: GridProfile[];
}

export interface VehicleNeed { vehicleId: string; targetSocPct: number; arrival: string; departure: string; priority: number }
export interface PlanRequest {
  horizonStart: string; slotMinutes: number; slotCount: number; algorithm: Algorithm; vehicles: VehicleNeed[];
}

export interface Comparison { algorithm: Algorithm; deadlinesMet: number; deadlineSuccessRate: number; estimatedCost: number; hardConstraintViolations: number }
export interface Explanation { comparison: Comparison[]; narrative: string | null; aiAvailable: boolean; toolCalls: string[] }

export interface AnalyticsSummary {
  from: string; to: string; sessionsByStatus: Record<string, number>; scheduledEnergyKwh: number; vehiclesServed: number;
  chargers: { chargerId: string; label: string; station: string; sessions: number; occupiedHours: number; utilization: number }[];
}

export interface Source { ref: number; documentId: string; title: string; source: string | null; chunkIndex: number; score: number; text: string }
export interface Citation { ref: string; documentId: string; title: string; chunkIndex: number; score: number; excerpt: string }
export interface Answer {
  question: string; answer: string | null; aiAvailable: boolean; grounded: boolean; citations: Citation[];
  sources: Source[]; chatModel: string | null; embeddingModel: string;
}
export interface KnowledgeDocument { id: string; title: string; source: string | null; type: string; createdAt: string; chunks: number }

export interface ProblemDetail { title?: string; status?: number; detail?: string; errors?: Record<string, string> }
