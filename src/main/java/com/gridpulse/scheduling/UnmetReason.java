package com.gridpulse.scheduling;

public enum UnmetReason {
    /** No available charger has the vehicle's connector type. */
    NO_COMPATIBLE_CHARGER,
    /** No charger/grid capacity could be found at all inside the vehicle's window. */
    NO_CAPACITY_BEFORE_DEPARTURE,
    /** Some energy was scheduled but not enough to reach the target before departure. */
    DEADLINE_MISSED
}
