package com.helpdesk.ticket;

/** Drives the SLA deadline (see helpdesk.sla.* in application.yml). */
public enum TicketPriority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT
}
