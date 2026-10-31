package br.com.fiap.pos.tech_challenge.core.infrastructure.observability;

import br.com.fiap.pos.tech_challenge.core.application.event.ServiceOrderStatusChangedEvent;
import br.com.fiap.pos.tech_challenge.core.domain.enums.ServiceOrderStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Métricas de OS para dashboards New Relic / Actuator (volume, tempo por status, transições).
 */
@Component
@Log4j2
public class ServiceOrderMetricsListener {

    private final MeterRegistry registry;
    private final Counter opened;
    private final Map<UUID, Instant> enteredAt = new ConcurrentHashMap<>();
    private final Map<UUID, ServiceOrderStatus> currentStatus = new ConcurrentHashMap<>();

    public ServiceOrderMetricsListener(MeterRegistry registry) {
        this.registry = registry;
        this.opened = Counter.builder("service_order_opened_total")
                .description("Ordens de serviço abertas")
                .register(registry);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(ServiceOrderStatusChangedEvent event) {
        Instant now = Instant.now();
        UUID id = event.serviceOrderUuid();

        if (event.previousStatus() == null && event.newStatus() == ServiceOrderStatus.RECEIVED) {
            opened.increment();
            log.info("service_order.opened uuid={} status={}", id, event.newStatus());
        }

        ServiceOrderStatus previous = event.previousStatus() != null
                ? event.previousStatus()
                : currentStatus.get(id);
        if (previous != null) {
            Instant start = enteredAt.getOrDefault(id, now);
            long millis = Math.max(0, Duration.between(start, now).toMillis());
            Timer.builder("service_order_status_duration_seconds")
                    .description("Tempo decorrido em cada status da OS")
                    .tag("status", previous.name())
                    .register(registry)
                    .record(millis, TimeUnit.MILLISECONDS);
            Counter.builder("service_order_status_transitions_total")
                    .tag("from", previous.name())
                    .tag("to", event.newStatus().name())
                    .register(registry)
                    .increment();
            log.info("service_order.status_transition uuid={} from={} to={} durationMs={}",
                    id, previous, event.newStatus(), millis);
        }

        enteredAt.put(id, now);
        currentStatus.put(id, event.newStatus());
    }

    public void recordIntegrationError(String type) {
        Counter.builder("service_order_integration_errors_total")
                .tag("type", type)
                .register(registry)
                .increment();
        log.warn("service_order.integration_error type={}", type);
    }
}
