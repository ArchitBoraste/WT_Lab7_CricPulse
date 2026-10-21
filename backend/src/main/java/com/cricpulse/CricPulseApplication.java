package com.cricpulse;

import com.cricpulse.config.SimulationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(SimulationProperties.class)
public class CricPulseApplication {

    public static void main(String[] args) {
        SpringApplication.run(CricPulseApplication.class, args);
    }
}
