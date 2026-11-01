package com.gridpulse.identity;

/** ADMIN: everything in the tenant. FLEET_MANAGER: fleets, vehicles, schedules. OPERATOR: sessions, outages. VIEWER: read-only. */
public enum Role { ADMIN, FLEET_MANAGER, OPERATOR, VIEWER }
