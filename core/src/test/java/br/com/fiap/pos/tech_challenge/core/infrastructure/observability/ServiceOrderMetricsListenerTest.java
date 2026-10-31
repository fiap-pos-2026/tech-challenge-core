package br.com.fiap.pos.tech_challenge.core.infrastructure.observability;

import br.com.fiap.pos.tech_challenge.core.application.event.ServiceOrderStatusChangedEvent;
import br.com.fiap.pos.tech_challenge.core.domain.enums.ServiceOrderStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceOrderMetricsListenerTest {

    private SimpleMeterRegistry registry;
    private ServiceOrderMetricsListener listener;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        listener = new ServiceOrderMetricsListener(registry);
    }

    @Test
    void openIncrementsOpenedCounter() {
        UUID id = UUID.randomUUID();
        listener.onStatusChanged(new ServiceOrderStatusChangedEvent(
                id, null, ServiceOrderStatus.RECEIVED, "a@b.c", "A"));

        assertThat(registry.find("service_order_opened_total").counter()).isNotNull();
        assertThat(registry.find("service_order_opened_total").counter().count()).isEqualTo(1.0);
    }

    @Test
    void transitionRecordsDurationAndTransition() {
        UUID id = UUID.randomUUID();
        listener.onStatusChanged(new ServiceOrderStatusChangedEvent(
                id, null, ServiceOrderStatus.RECEIVED, "a@b.c", "A"));
        listener.onStatusChanged(new ServiceOrderStatusChangedEvent(
                id, ServiceOrderStatus.RECEIVED, ServiceOrderStatus.IN_DIAGNOSIS, "a@b.c", "A"));

        assertThat(registry.find("service_order_status_transitions_total")
                .tags("from", "RECEIVED", "to", "IN_DIAGNOSIS").counter()).isNotNull();
        assertThat(registry.find("service_order_status_duration_seconds")
                .tag("status", "RECEIVED").timer()).isNotNull();
    }

    @Test
    void integrationErrorIncrementsCounter() {
        listener.recordIntegrationError("mail");
        assertThat(registry.find("service_order_integration_errors_total")
                .tag("type", "mail").counter().count()).isEqualTo(1.0);
    }
}
