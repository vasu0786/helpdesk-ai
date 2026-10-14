package com.helpdesk.ticket;

/** Query parameters for GET /api/tickets. All optional. */
public record TicketFilter(
        TicketStatus status,
        TicketCategory category,
        TicketPriority priority,
        Long assigneeId,
        Boolean unassigned,
        Boolean breached,
        String q
) {
}
