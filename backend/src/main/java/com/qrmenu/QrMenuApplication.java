package com.qrmenu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling powers the ordering module's DRAFT-cart TTL cleanup job
// (OrderCleanupScheduler, Milestone 4) and the outbox poller
// (com.qrmenu.shared.outbox.OutboxPollerScheduler, Milestone 5).
// @EnableAsync powers the mock payment provider's simulated webhook dispatch
// (MockPaymentSimulationDispatcher, Milestone 5) - it must run on a thread separate
// from the request that triggered it (Section 2: "ayrı, asenkron").
@EnableScheduling
@EnableAsync
@SpringBootApplication
public class QrMenuApplication {

    public static void main(String[] args) {
        SpringApplication.run(QrMenuApplication.class, args);
    }
}
