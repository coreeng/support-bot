package com.coreeng.supportbot.metrics;

import java.util.List;

public interface MetricsRepository {
    List<TicketMetric> getTicketMetrics();
    List<EscalationMetric> getEscalationMetrics();
}
