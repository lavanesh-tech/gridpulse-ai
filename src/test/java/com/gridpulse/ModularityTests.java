package com.gridpulse;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Fails the build if one module reaches into another module's internals or a cycle appears. */
class ModularityTests {
    @Test
    void modulesRespectBoundaries() {
        ApplicationModules.of(GridPulseApplication.class).verify();
    }
}
