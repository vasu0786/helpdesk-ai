package com.helpdesk.ticket;

import java.util.EnumSet;
import java.util.Set;

/**
 * Ticket lifecycle. Allowed moves:
 * <pre>
 * OPEN                -> IN_PROGRESS, CLOSED
 * IN_PROGRESS         -> WAITING_ON_CUSTOMER, RESOLVED
 * WAITING_ON_CUSTOMER -> IN_PROGRESS, RESOLVED
 * RESOLVED            -> IN_PROGRESS (reopen), CLOSED
 * CLOSED              -> (final)
 * </pre>
 */
public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    WAITING_ON_CUSTOMER,
    RESOLVED,
    CLOSED;

    public Set<TicketStatus> allowedNext() {
        return switch (this) {
            case OPEN -> EnumSet.of(IN_PROGRESS, CLOSED);
            case IN_PROGRESS -> EnumSet.of(WAITING_ON_CUSTOMER, RESOLVED);
            case WAITING_ON_CUSTOMER -> EnumSet.of(IN_PROGRESS, RESOLVED);
            case RESOLVED -> EnumSet.of(IN_PROGRESS, CLOSED);
            case CLOSED -> EnumSet.noneOf(TicketStatus.class);
        };
    }

    public boolean canMoveTo(TicketStatus next) {
        return allowedNext().contains(next);
    }

    /** Statuses where the SLA clock is running and a breach can happen. */
    public static final Set<TicketStatus> SLA_ACTIVE = EnumSet.of(OPEN, IN_PROGRESS);
}
