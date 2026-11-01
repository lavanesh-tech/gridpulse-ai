package com.gridpulse.scheduling;

/** A deterministic scheduling strategy. Same problem in, same schedule out. */
public interface ChargingScheduler {
    String name();

    ChargingSchedule schedule(SchedulingProblem problem);
}
