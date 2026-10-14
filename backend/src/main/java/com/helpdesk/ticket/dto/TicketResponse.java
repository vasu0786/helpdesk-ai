package com.helpdesk.ticket.dto;

import com.helpdesk.ticket.Ticket;
import com.helpdesk.ticket.TicketCategory;
import com.helpdesk.ticket.TicketPriority;
import com.helpdesk.ticket.TicketStatus;
import com.helpdesk.user.User;

import java.time.Instant;

public record TicketResponse(
        Long id,
        String subject,
        String description,
        String customerEmail,
        TicketStatus status,
        TicketCategory category,
        TicketPriority priority,
        Assignee assignee,
        Sla sla,
        Instant resolvedAt,
        Instant closedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public record Assignee(Long id, String fullName, String email) {
    }

    public record Sla(Instant dueAt, boolean breached, Instant breachedAt) {
    }

    public static TicketResponse from(Ticket t) {
        User a = t.getAssignee();
        Assignee assignee = a == null ? null : new Assignee(a.getId(), a.getFullName(), a.getEmail());
        return new TicketResponse(t.getId(), t.getSubject(), t.getDescription(), t.getCustomerEmail(),
                t.getStatus(), t.getCategory(), t.getPriority(), assignee,
                new Sla(t.getSlaDueAt(), t.isSlaBreached(), t.getSlaBreachedAt()),
                t.getResolvedAt(), t.getClosedAt(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
