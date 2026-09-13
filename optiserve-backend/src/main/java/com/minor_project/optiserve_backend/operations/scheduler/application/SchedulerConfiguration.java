package com.minor_project.optiserve_backend.operations.scheduler.application;

import com.minor_project.optiserve_backend.operations.scheduler.domain.HybridSchedulerStrategy;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulerStrategy;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SchedulerConfiguration {

    @Bean
    Clock schedulerClock() {
        return Clock.systemUTC();
    }

    @Bean
    SchedulerStrategy schedulerStrategy(Clock schedulerClock) {
        return new HybridSchedulerStrategy(schedulerClock);
    }
}
