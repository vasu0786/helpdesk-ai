package com.helpdesk.ticket.dto;

/** Counts for the agent dashboard header cards. */
public record TicketStats(long open, long inProgress, long waitingOnCustomer, long resolved, long closed,
                          long slaBreached, long unassigned) {
}
