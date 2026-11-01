package com.gridpulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GridPulseApplication {
    public static void main(String[] args) {
        SpringApplication.run(GridPulseApplication.class, args);
    }
}
