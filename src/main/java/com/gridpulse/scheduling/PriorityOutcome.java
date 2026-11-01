package com.gridpulse.scheduling;

/** Deadline outcome for one priority level. */
public record PriorityOutcome(int priority, int needingCharge, int met, double successRate) {
}
