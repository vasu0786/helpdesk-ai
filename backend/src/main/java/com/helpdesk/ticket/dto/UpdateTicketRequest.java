package com.helpdesk.ticket.dto;

import com.helpdesk.ticket.TicketCategory;
import com.helpdesk.ticket.TicketPriority;
import com.helpdesk.ticket.TicketStatus;

/**
 * Agent/admin partial update: only non-null fields are applied.
 * Set unassign=true to remove the assignee.
 */
public record UpdateTicketRequest(
        TicketStatus status,
        TicketCategory category,
        TicketPriority priority,
        Long assigneeId,
        Boolean unassign
) {
}
