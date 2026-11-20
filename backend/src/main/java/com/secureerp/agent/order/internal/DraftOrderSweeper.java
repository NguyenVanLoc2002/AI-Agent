package com.secureerp.agent.order.internal;

import com.secureerp.agent.platform.TraceIds;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

/**
 * Background sweep of unfinished draft order requests (EP-09c, A-41(l), A-46 decision 2A). Before it, a request
 * past the submit timeout became an unknown outcome (and a handoff) only when a customer or SALES read it.
 *
 * <p>Every instance sweeps; no lock between instances is needed because each request is expired by a
 * conditional update in its own transaction ({@code DraftOrderStore.expireUnfinished}), so two sweeps cannot
 * both expire it. As a safety net a customer read expires the request it reads, and the SALES unknown list sweeps
 * one page. A failing sweep is logged with its exception class only and the next one runs as scheduled.
 *
 * <p>EP-09c reviews: the interval comes from the validated {@link OrderProperties} only (one source, backend M2);
 * scheduling itself is enabled by platform, so switching the sweeper off stops no other job (L-4/L5); each run
 * has a trace ID of its own, so its log lines and the handoffs it opens are not mixed into a request's trace
 * (security L-5).
 */
@Component
@ConditionalOnProperty(prefix = "apas.order.sweeper", name = "enabled", havingValue = "true", matchIfMissing = true)
class DraftOrderSweeper implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(DraftOrderSweeper.class);

    private final DefaultDraftOrders orders;
    private final Duration interval;

    DraftOrderSweeper(DefaultDraftOrders orders, OrderProperties properties) {
        this.orders = orders;
        this.interval = properties.sweeper().interval();
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(new FixedDelayTask(this::sweep, interval, interval));
    }

    void sweep() {
        String previous = MDC.get(TraceIds.MDC_KEY);
        MDC.put(TraceIds.MDC_KEY, UUID.randomUUID().toString());
        try {
            int expired = orders.sweep(DefaultDraftOrders.BACKGROUND_PAGES);
            if (expired > 0) {
                log.info("order.sweep expired={}", expired);
            }
        } catch (RuntimeException failure) {
            log.error("order.sweep failed exception={}", failure.getClass().getSimpleName());
        } finally {
            if (previous == null) {
                MDC.remove(TraceIds.MDC_KEY);
            } else {
                MDC.put(TraceIds.MDC_KEY, previous);
            }
        }
    }
}
