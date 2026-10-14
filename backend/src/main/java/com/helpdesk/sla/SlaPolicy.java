package com.helpdesk.sla;

import com.helpdesk.config.HelpdeskProperties;
import com.helpdesk.ticket.TicketPriority;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/** How long a ticket of each priority has to be resolved, counted from when it was created. */
@Component
public class SlaPolicy {

    private final HelpdeskProperties.Sla sla;

    public SlaPolicy(HelpdeskProperties props) {
        this.sla = props.sla();
    }

    public Duration allowedTime(TicketPriority priority) {
        return switch (priority) {
            case URGENT -> sla.urgent();
            case HIGH -> sla.high();
            case MEDIUM -> sla.medium();
            case LOW -> sla.low();
        };
    }

    public Instant dueAt(Instant createdAt, TicketPriority priority) {
        return createdAt.plus(allowedTime(priority));
    }
}
